package com.hfxconnect.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Full HTTP-layer integration tests against the real database (via the
 * shared Testcontainers singleton container).
 *
 * <p>Unlike {@link CategoryRepositoryIntegrationTest}, these requests are
 * handled by the embedded server's own thread, so test-method
 * {@code @Transactional} rollback does not apply here. Each test uses a
 * unique, UUID-suffixed category name/slug instead of relying on cleanup, so
 * tests remain independent of execution order and of each other.
 *
 * <p>{@code POST} requires an {@code ADMIN} access token as of Milestone
 * 5C — every write request here carries one, issued for a fresh {@code ADMIN}
 * test account created in {@link #issueAdminSession()}. The authorization
 * matrix itself (which roles are rejected, and how) is
 * {@link com.hfxconnect.security.AuthorizationMatrixApiIntegrationTest}'s
 * job, not this regression suite's — this file's POST tests exist to prove
 * the category business logic still works correctly *given* a valid ADMIN
 * caller, unchanged from before authorization existed.
 */
@AutoConfigureTestRestTemplate
class CategoryApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	private HttpHeaders adminAuthHeaders;

	@BeforeEach
	void issueAdminSession() {
		User admin = userRepository.saveAndFlush(TestUserFactory.withRole(Role.ADMIN));
		adminAuthHeaders = new HttpHeaders();
		adminAuthHeaders.setBearerAuth(accessTokenService.issue(admin.getId(), Role.ADMIN).token());
	}

	@Test
	void createReturns201WithLocationHeaderAndBody() {
		String name = "Employment Support " + UUID.randomUUID();

		ResponseEntity<CategoryResponse> response = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(name, "Job search and career support."), CategoryResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getHeaders().getLocation()).isNotNull();
		assertThat(response.getHeaders().getLocation().toString()).endsWith("/api/v1/categories/" + response.getBody().id());
		assertThat(response.getBody().name()).isEqualTo(name);
		assertThat(response.getBody().active()).isTrue();
		assertThat(response.getBody().id()).isNotNull();
	}

	@Test
	void blankNameIsRejectedWithValidationError() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/categories", createRequest("   ", null), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(response.getBody()).contains("\"name\"");
	}

	@Test
	void missingNameFieldIsRejectedWithValidationError() {
		HttpEntity<String> request = jsonEntity("{\"description\":\"No name field at all.\"}");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/categories", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void excessiveNameLengthIsRejected() {
		String tooLong = "a".repeat(121);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(tooLong, null), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void malformedJsonIsRejectedWithoutLeakingInternals() {
		HttpEntity<String> request = jsonEntity("{ this is not valid json");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/categories", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
		assertThat(response.getBody().toLowerCase())
				.doesNotContain("exception", "stacktrace", "com.fasterxml", "com.hfxconnect");
	}

	@Test
	void duplicateCategoryNameIsRejectedWithConflict() {
		String name = "Duplicate Check " + UUID.randomUUID();
		ResponseEntity<CategoryResponse> first = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(name, null), CategoryResponse.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> second = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(name.toUpperCase(), null), String.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(second.getBody()).contains("\"code\":\"CATEGORY_CONFLICT\"");
	}

	@Test
	void createdCategoryCanBeRetrievedById() {
		String name = "Retrieve By Id Check " + UUID.randomUUID();
		CategoryResponse created = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(name, null), CategoryResponse.class).getBody();

		ResponseEntity<CategoryResponse> response = restTemplate.getForEntity(
				"/api/v1/categories/" + created.id(), CategoryResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().name()).isEqualTo(name);
	}

	@Test
	void createdCategoryCanBeRetrievedBySlug() {
		String name = "Retrieve By Slug Check " + UUID.randomUUID();
		CategoryResponse created = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(name, null), CategoryResponse.class).getBody();

		ResponseEntity<CategoryResponse> response = restTemplate.getForEntity(
				"/api/v1/categories/slug/" + created.slug(), CategoryResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().slug()).isEqualTo(created.slug());
	}

	@Test
	void missingCategoryReturns404WithDocumentedErrorShape() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories/99999999", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"CATEGORY_NOT_FOUND\"");
		assertThat(response.getBody()).contains("\"timestamp\"");
		assertThat(response.getBody()).contains("\"status\":404");
	}

	@Test
	void listReturnsAPageContainingACreatedCategory() {
		String name = "List Check " + UUID.randomUUID();
		restTemplate.postForEntity("/api/v1/categories", createRequest(name, null), CategoryResponse.class);

		ResponseEntity<CategoryPageResponse> response = restTemplate.getForEntity(
				"/api/v1/categories?page=0&size=50", CategoryPageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(CategoryResponse::name).contains(name);
		assertThat(response.getBody().size()).isEqualTo(50);
		assertThat(response.getBody().page()).isEqualTo(0);
	}

	@Test
	void activeFalseFilterReturnsOnlyInactiveCategories() {
		// The category domain itself has no deactivation endpoint (Milestone
		// 3A), but several other domains' own tests (resource creation,
		// resource submissions) legitimately deactivate a category directly
		// via the repository as their own test fixture, in this same shared,
		// never-rolled-back database — so this test cannot assume no
		// inactive category exists globally. It creates and deactivates its
		// own uniquely-named category instead, and asserts the filter's
		// actual behavior (only inactive categories, and a known active one
		// excluded), independent of whatever else the shared database holds.
		String marker = UUID.randomUUID().toString();
		String activeName = "Active Filter Check " + marker;
		String inactiveName = "Inactive Filter Check " + marker;
		restTemplate.postForEntity("/api/v1/categories", createRequest(activeName, null), CategoryResponse.class);
		CategoryResponse createdInactive = restTemplate.postForEntity(
				"/api/v1/categories", createRequest(inactiveName, null), CategoryResponse.class).getBody();
		Category inactive = categoryRepository.findById(createdInactive.id()).orElseThrow();
		inactive.deactivate();
		categoryRepository.saveAndFlush(inactive);

		ResponseEntity<CategoryPageResponse> response = restTemplate.getForEntity(
				"/api/v1/categories?active=false&size=100", CategoryPageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(CategoryResponse::name)
				.contains(inactiveName)
				.doesNotContain(activeName);
	}

	@Test
	void negativePageIsRejectedWithInvalidPagination() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories?page=-1", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_PAGINATION\"");
	}

	@Test
	void sizeAboveTheMaximumIsRejectedWithInvalidPagination() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories?size=1000", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_PAGINATION\"");
	}

	@Test
	void openApiDocumentIncludesTheCategoryEndpoints() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("/api/v1/categories");
		assertThat(response.getBody()).contains("/api/v1/categories/{id}");
		assertThat(response.getBody()).contains("/api/v1/categories/slug/{slug}");
	}

	private HttpEntity<CategoryCreateRequest> createRequest(String name, String description) {
		HttpHeaders headers = authedJsonHeaders();
		return new HttpEntity<>(new CategoryCreateRequest(name, description), headers);
	}

	private HttpEntity<String> jsonEntity(String rawJson) {
		HttpHeaders headers = authedJsonHeaders();
		return new HttpEntity<>(rawJson, headers);
	}

	private HttpHeaders authedJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.addAll(adminAuthHeaders);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

}
