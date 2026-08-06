package com.hfxconnect.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.text.SlugGenerator;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
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

/** Full HTTP-layer tests for the correction-report moderation routes (Milestone 9A). */
@AutoConfigureTestRestTemplate
class ModerationCorrectionReportApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	@Test
	void queueAsUserIsForbidden() {
		User user = persistUser(Role.USER);
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/correction-reports", HttpMethod.GET, new HttpEntity<>(null, authHeaders(user)), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void queueAsModeratorSucceeds() {
		User moderator = persistUser(Role.MODERATOR);
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/correction-reports", HttpMethod.GET, new HttpEntity<>(null, authHeaders(moderator)), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void approveWithApplyProposedChangesUpdatesThePublicResource() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("API Apply Changes Target");
		String reportId = createReport(reporter, resource.getId(),
				"{\"issueType\":\"ADDRESS\",\"explanation\":\"The address is stale.\",\"proposedAddressLine1\":\"1 API Ave\"}");

		ResponseEntity<String> approveResponse = restTemplate.exchange(
				"/api/v1/moderation/correction-reports/" + reportId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed.\",\"applyProposedChanges\":true,\"deactivateResource\":false}",
						authedJsonHeaders(moderator)), String.class);

		assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(approveResponse.getBody()).contains("\"status\":\"APPROVED\"");

		ResponseEntity<String> resourceDetail = restTemplate.getForEntity(
				"/api/v1/resources/slug/" + resourceRepository.findById(resource.getId()).orElseThrow().getSlug(), String.class);
		assertThat(resourceDetail.getBody()).contains("1 API Ave");
	}

	@Test
	void resourceClosedApprovalWithDeactivateTrueRemovesResourceFromPublicListing() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("API Deactivate Target");
		String reportId = createReport(reporter, resource.getId(),
				"{\"issueType\":\"RESOURCE_CLOSED\",\"explanation\":\"Permanently closed.\"}");

		ResponseEntity<String> approveResponse = restTemplate.exchange(
				"/api/v1/moderation/correction-reports/" + reportId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Confirmed closed.\",\"applyProposedChanges\":false,\"deactivateResource\":true}",
						authedJsonHeaders(moderator)), String.class);

		assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> resourceDetail = restTemplate.getForEntity(
				"/api/v1/resources/slug/" + resource.getSlug(), String.class);
		assertThat(resourceDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void invalidDeactivationRequestReturnsBadRequest() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("API Invalid Deactivation Target");
		String reportId = createReport(reporter, resource.getId(),
				"{\"issueType\":\"ADDRESS\",\"explanation\":\"Not a closure issue.\"}");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/correction-reports/" + reportId + "/approve", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Trying to deactivate anyway.\",\"applyProposedChanges\":false,\"deactivateResource\":true}",
						authedJsonHeaders(moderator)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_DEACTIVATION_REQUEST\"");
	}

	@Test
	void rejectNeverModifiesThePublicResource() {
		User reporter = persistUser(Role.USER);
		User moderator = persistUser(Role.MODERATOR);
		CommunityResource resource = persistResource("API Reject Preserves Target");
		String reportId = createReport(reporter, resource.getId(),
				"{\"issueType\":\"ADDRESS\",\"explanation\":\"Claiming a change.\",\"proposedAddressLine1\":\"Should Not Apply\"}");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/moderation/correction-reports/" + reportId + "/reject", HttpMethod.POST,
				new HttpEntity<>("{\"reason\":\"Not verifiable.\"}", authedJsonHeaders(moderator)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> resourceDetail = restTemplate.getForEntity("/api/v1/resources/slug/" + resource.getSlug(), String.class);
		assertThat(resourceDetail.getBody()).doesNotContain("Should Not Apply");
	}

	private String createReport(User reporter, UUID resourceId, String body) {
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resourceId + "/correction-reports", HttpMethod.POST,
				new HttpEntity<>(body, authedJsonHeaders(reporter)), String.class);
		return response.getBody().split("\"id\":\"")[1].split("\"")[0];
	}

	private CommunityResource persistResource(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		Category category = categoryRepository.save(
				new Category(name + " Cat", (name + " cat").toLowerCase(Locale.ROOT), "mod-cr-api-cat-" + marker, null));
		CommunityResource resource = new CommunityResource(category, name, SlugGenerator.generate(name).orElseThrow(),
				"An existing description.", "1 Original St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.FREE, null, null);
		return resourceRepository.saveAndFlush(resource);
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

}
