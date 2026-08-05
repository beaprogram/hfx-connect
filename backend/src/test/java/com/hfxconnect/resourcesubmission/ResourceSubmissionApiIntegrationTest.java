package com.hfxconnect.resourcesubmission;

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
 * Full HTTP-layer integration tests for {@link ResourceSubmissionController}
 * against the real database, following {@code SavedResourceApiIntegrationTest}'s
 * established pattern (Milestone 8A).
 */
@AutoConfigureTestRestTemplate
class ResourceSubmissionApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	@Test
	void createWithNoTokenIsRejected() {
		Category category = category("No Token Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "No Token Item"), jsonHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void createAsUserSucceeds() {
		assertCreateSucceeds(Role.USER);
	}

	@Test
	void createAsOrganizationSucceeds() {
		assertCreateSucceeds(Role.ORGANIZATION);
	}

	@Test
	void createAsModeratorSucceeds() {
		assertCreateSucceeds(Role.MODERATOR);
	}

	@Test
	void createAsAdminSucceeds() {
		assertCreateSucceeds(Role.ADMIN);
	}

	private void assertCreateSucceeds(Role role) {
		User user = persistUser(role);
		Category category = category(role + " Create Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), role + " Item"), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).contains("\"status\":\"PENDING_REVIEW\"");
		assertThat(response.getHeaders().getLocation()).isNotNull();
	}

	@Test
	void requestUserIdIsNeverAccepted() {
		User user = persistUser(Role.USER);
		Category category = category("No Client User Id Check");
		String maliciousUserId = UUID.randomUUID().toString();
		String body = String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"Injected Owner Item\",\"shortDescription\":\"A short description.\","
						+ "\"addressLine1\":\"123 Main St\",\"city\":\"Halifax\",\"province\":\"NS\",\"postalCode\":\"B3H 4R2\","
						+ "\"costType\":\"UNKNOWN\",\"submittedByUserId\":\"%s\"}",
				category.getId(), maliciousUserId);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(body, authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		ResponseEntity<String> ownList = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(user)), String.class);
		assertThat(ownList.getBody()).contains("Injected Owner Item");
	}

	@Test
	void invalidCategoryReturnsNotFound() {
		User user = persistUser(Role.USER);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(999999999L, "No Category Item"), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"CATEGORY_NOT_FOUND\"");
	}

	@Test
	void inactiveCategoryReturnsBadRequest() {
		User user = persistUser(Role.USER);
		Category category = category("Inactive API Check");
		category.deactivate();
		categoryRepository.saveAndFlush(category);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Inactive Category Item"), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INACTIVE_CATEGORY\"");
	}

	@Test
	void blankNameReturnsValidationError() {
		User user = persistUser(Role.USER);
		Category category = category("Blank Name API Check");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), ""), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void malformedJsonReturnsBadRequest() {
		User user = persistUser(Role.USER);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>("not json", authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
	}

	@Test
	void duplicatePendingReturnsConflict() {
		User user = persistUser(Role.USER);
		Category category = category("Duplicate API Check");
		restTemplate.exchange("/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Duplicate API Item"), authedJsonHeaders(user)), String.class);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Duplicate API Item"), authedJsonHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_SUBMISSION_CONFLICT\"");
	}

	@Test
	void listWithNoTokenIsRejected() {
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.GET, new HttpEntity<>(null, new HttpHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void listReturnsOnlyOwnSubmissions() {
		User owner = persistUser(Role.USER);
		User other = persistUser(Role.USER);
		Category category = category("List Isolation API Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.exchange("/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Owned " + marker), authedJsonHeaders(owner)), String.class);
		restTemplate.exchange("/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Not Owned " + marker), authedJsonHeaders(other)), String.class);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.GET, new HttpEntity<>(null, authHeaders(owner)), String.class);

		assertThat(response.getBody()).contains("Owned " + marker).doesNotContain("Not Owned " + marker);
	}

	@Test
	void anotherUsersSubmissionDetailReturnsNotFound() {
		User owner = persistUser(Role.USER);
		User other = persistUser(Role.USER);
		Category category = category("Detail Isolation API Check");
		ResponseEntity<CreatedSubmission> created = createSubmission(owner, category, "Not Yours API");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions/" + created.getBody().id, HttpMethod.GET,
				new HttpEntity<>(null, authHeaders(other)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_SUBMISSION_NOT_FOUND\"");
	}

	@Test
	void withdrawOwnSubmissionSucceeds() {
		User user = persistUser(Role.USER);
		Category category = category("Withdraw API Check");
		ResponseEntity<CreatedSubmission> created = createSubmission(user, category, "Withdraw Me API");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions/" + created.getBody().id + "/withdraw", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(user)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"WITHDRAWN\"");
	}

	@Test
	void withdrawAnotherUsersSubmissionReturnsNotFound() {
		User owner = persistUser(Role.USER);
		User other = persistUser(Role.USER);
		Category category = category("Withdraw Isolation API Check");
		ResponseEntity<CreatedSubmission> created = createSubmission(owner, category, "Not Yours Withdraw API");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions/" + created.getBody().id + "/withdraw", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(other)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void publicResourceListNeverIncludesSubmissions() {
		User user = persistUser(Role.USER);
		Category category = category("Not Public Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.exchange("/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), "Should Not Appear " + marker), authedJsonHeaders(user)), String.class);

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources?q=Should+Not+Appear+" + marker, String.class);

		assertThat(response.getBody()).doesNotContain("Should Not Appear " + marker);
	}

	private ResponseEntity<CreatedSubmission> createSubmission(User user, Category category, String name) {
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me/resource-submissions", HttpMethod.POST,
				new HttpEntity<>(createBody(category.getId(), name), authedJsonHeaders(user)), String.class);
		String id = response.getBody().split("\"id\":\"")[1].split("\"")[0];
		CreatedSubmission created = new CreatedSubmission();
		created.id = id;
		return ResponseEntity.status(response.getStatusCode()).body(created);
	}

	private static class CreatedSubmission {
		String id;
	}

	private String createBody(Long categoryId, String name) {
		return String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"%s\",\"shortDescription\":\"A short description.\","
						+ "\"addressLine1\":\"123 Main St\",\"city\":\"Halifax\",\"province\":\"NS\","
						+ "\"postalCode\":\"B3H 4R2\",\"costType\":\"UNKNOWN\"}",
				categoryId, name);
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
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
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "rs-api-cat-" + marker, null));
	}

}
