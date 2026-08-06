package com.hfxconnect.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Full HTTP-layer tests for the resource-submission moderation routes
 * (Milestone 9A) — the role matrix (401/403/200) plus one full
 * create-review HTTP round trip, following {@code
 * ResourceSubmissionApiIntegrationTest}'s established pattern.
 */
@AutoConfigureTestRestTemplate
class ModerationResourceSubmissionApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	@Test
	void queueWithNoTokenIsRejected() {
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions", HttpMethod.GET, new HttpEntity<>(null, new HttpHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void queueAsUserIsForbidden() {
		assertQueueStatus(Role.USER, HttpStatus.FORBIDDEN);
	}

	@Test
	void queueAsOrganizationIsForbidden() {
		assertQueueStatus(Role.ORGANIZATION, HttpStatus.FORBIDDEN);
	}

	@Test
	void queueAsModeratorSucceeds() {
		assertQueueStatus(Role.MODERATOR, HttpStatus.OK);
	}

	@Test
	void queueAsAdminSucceeds() {
		assertQueueStatus(Role.ADMIN, HttpStatus.OK);
	}

	private void assertQueueStatus(Role role, HttpStatus expected) {
		User user = persistUser(role);
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions", HttpMethod.GET, new HttpEntity<>(null, authHeaders(user)), String.class);
		assertThat(response.getStatusCode()).isEqualTo(expected);
	}

	@Test
	void fullApprovalFlowPublishesAResourceAndOwnerSeesTheResult() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = category("Full Flow Check");
		String name = "Full Flow Item " + UUID.randomUUID();

		ResponseEntity<String> createResponse = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), name), authedJsonHeaders(contributor)), String.class);
		String submissionId = extractId(createResponse.getBody());

		ResponseEntity<String> approveResponse = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Verified against the organization's website.\"}", authedJsonHeaders(moderator)),
				String.class);

		assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(approveResponse.getBody()).contains("\"status\":\"APPROVED\"").contains("\"resultingResource\"");

		ResponseEntity<String> ownerView = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions/" + submissionId, HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(contributor)), String.class);
		assertThat(ownerView.getBody()).contains("\"status\":\"APPROVED\"").contains("\"resultingResource\"")
				.doesNotContain(moderator.getId().toString());

		ResponseEntity<String> publicSearch = restTemplate.getForEntity("/api/v1/resources?q=" + name.replace(" ", "+"), String.class);
		assertThat(publicSearch.getBody()).contains(name);
	}

	/**
	 * Regression test: the public resource detail endpoint must actually
	 * expose {@code lastVerifiedAt} — {@link com.hfxconnect.resource.ResourceDetails}
	 * and {@link com.hfxconnect.resource.ResourceResponse} both needed this
	 * field threaded through explicitly (a manual end-to-end pass caught this
	 * before it shipped; the entity/service layer alone getting it right was
	 * not sufficient).
	 */
	@Test
	void publishedResourceExposesVerificationStatusAndLastVerifiedAtPublicly() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = category("Verification Exposure Check");
		String name = "Verification Exposure Item " + UUID.randomUUID();

		ResponseEntity<String> createResponse = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), name), authedJsonHeaders(contributor)), String.class);
		String submissionId = extractId(createResponse.getBody());

		ResponseEntity<String> approveResponse = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Verified against the organization's website.\"}", authedJsonHeaders(moderator)),
				String.class);
		// Split on "resultingResource" first — the response body's own
		// "category" object also has a "slug" field, earlier in the JSON, so
		// a plain split("\"slug\":\"") would grab the wrong one.
		String resultingResourceJson = approveResponse.getBody().split("\"resultingResource\"")[1];
		String slug = resultingResourceJson.split("\"slug\":\"")[1].split("\"")[0];

		ResponseEntity<String> publicDetail = restTemplate.getForEntity("/api/v1/resources/slug/" + slug, String.class);

		assertThat(publicDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(publicDetail.getBody()).contains("\"verificationStatus\":\"VERIFIED\"").contains("\"lastVerifiedAt\":");
		assertThat(publicDetail.getBody()).doesNotContain("\"lastVerifiedAt\":null");
	}

	@Test
	void selfReviewReturnsForbidden() {
		User contributorModerator = persistUser(Role.MODERATOR);
		Category category = category("Self Review API Check");
		ResponseEntity<String> createResponse = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Self Review API Item"), authedJsonHeaders(contributorModerator)), String.class);
		String submissionId = extractId(createResponse.getBody());

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Approving my own submission.\"}", authedJsonHeaders(contributorModerator)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"SELF_REVIEW_NOT_ALLOWED\"");
	}

	@Test
	void rejectReturnsRejectedStatusAndReason() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = category("Reject API Check");
		ResponseEntity<String> createResponse = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Reject API Item"), authedJsonHeaders(contributor)), String.class);
		String submissionId = extractId(createResponse.getBody());

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/reject", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Insufficient detail to verify.\"}", authedJsonHeaders(moderator)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"REJECTED\"").contains("Insufficient detail to verify.");
	}

	@Test
	void blankReasonReturnsValidationError() {
		User contributor = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = category("Blank Reason API Check");
		ResponseEntity<String> createResponse = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Blank Reason API Item"), authedJsonHeaders(contributor)), String.class);
		String submissionId = extractId(createResponse.getBody());

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"\"}", authedJsonHeaders(moderator)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void auditEventsRouteRequiresModeratorRole() {
		User user = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		Category category = category("Audit Route API Check");
		ResponseEntity<String> createResponse = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Audit Route API Item"), authedJsonHeaders(user)), String.class);
		String submissionId = extractId(createResponse.getBody());
		restTemplate.exchange("/api/v1/moderation/resource-submissions/" + submissionId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Approved for audit trail check.\"}", authedJsonHeaders(moderator)), String.class);

		ResponseEntity<String> forbidden = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/audit-events", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(user)), String.class);
		ResponseEntity<String> allowed = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions/" + submissionId + "/audit-events", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(moderator)), String.class);

		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(allowed.getBody()).contains("\"action\":\"RESOURCE_CREATED\"").contains("\"actorEmail\"");
	}

	@Test
	void disabledModeratorLosesAccessImmediately() {
		User moderator = persistUser(Role.MODERATOR);
		String token = accessTokenService.issue(moderator.getId(), moderator.getRole()).token();
		userRepository.saveAndFlush(TestUserFactory.withChangedRoleAndStatus(
				moderator, Role.MODERATOR, com.hfxconnect.user.AccountStatus.SUSPENDED));

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/resource-submissions", HttpMethod.GET, new HttpEntity<>(null, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	private String extractId(String body) {
		return body.split("\"id\":\"")[1].split("\"")[0];
	}

	private String createBody(Long categoryId, String name) {
		return String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"%s\",\"shortDescription\":\"A short description.\","
						+ "\"addressLine1\":\"123 Main St\",\"city\":\"Halifax\",\"province\":\"NS\","
						+ "\"postalCode\":\"B3H 4R2\",\"costType\":\"UNKNOWN\"}",
				categoryId, name);
	}

	private HttpHeaders authHeaders(User user) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessTokenService.issue(user.getId(), user.getRole()).token());
		return headers;
	}

	private HttpHeaders authedJsonHeaders(User user) {
		HttpHeaders headers = authHeaders(user);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private User persistUser(Role role) {
		return userRepository.saveAndFlush(TestUserFactory.withRole(role));
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "mod-rs-api-cat-" + marker, null));
	}

}
