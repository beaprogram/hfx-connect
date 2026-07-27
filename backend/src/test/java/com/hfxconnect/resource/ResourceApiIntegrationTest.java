package com.hfxconnect.resource;

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
 * shared Testcontainers singleton container), following exactly the pattern
 * {@code CategoryApiIntegrationTest} established.
 *
 * <p>There is no HTTP endpoint for update/deactivate in this milestone (see
 * {@code ResourceController}'s class Javadoc), so tests that need a
 * deactivated resource to verify "inactive resources are invisible
 * publicly" use {@link ResourceService#deactivate} directly rather than
 * through HTTP — the only way to reach that state right now.
 *
 * <p>{@code POST} requires an {@code ADMIN}-or-{@code MODERATOR} access
 * token as of Milestone 5C — every write request here carries an
 * {@code ADMIN} one, issued in {@link #issueAdminSession()}. The full
 * authorization matrix lives in
 * {@link com.hfxconnect.security.AuthorizationMatrixApiIntegrationTest}.
 */
@AutoConfigureTestRestTemplate
class ResourceApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ResourceService resourceService;

	@Autowired
	private UserRepository userRepository;

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
	void createReturns201WithLocationHeaderAndCategorySummary() {
		Category category = activeCategory("Create Check");
		String name = "Halifax Food Bank " + UUID.randomUUID();

		ResponseEntity<ResourceResponse> response = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getHeaders().getLocation()).isNotNull();
		assertThat(response.getHeaders().getLocation().toString()).endsWith("/api/v1/resources/" + response.getBody().id());
		assertThat(response.getBody().name()).isEqualTo(name);
		assertThat(response.getBody().active()).isTrue();
		assertThat(response.getBody().verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
		assertThat(response.getBody().category().id()).isEqualTo(category.getId());
		assertThat(response.getBody().category().name()).isEqualTo(category.getName());
	}

	@Test
	void missingCategoryIdIsRejectedWithValidationError() {
		HttpEntity<String> request = jsonEntity(
				"{\"name\":\"No Category\",\"description\":\"Test.\",\"addressLine1\":\"123 Main St\","
						+ "\"city\":\"Halifax\",\"province\":\"NS\",\"postalCode\":\"B3H 4R2\"}");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/resources", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"categoryId\"");
	}

	@Test
	void nonexistentCategoryReturns404WithDocumentedErrorShape() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(-1L, "Nonexistent Category Resource"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"CATEGORY_NOT_FOUND\"", "\"timestamp\"", "\"status\":404");
	}

	@Test
	void inactiveCategoryIsRejected() {
		Category category = activeCategory("Inactive Check");
		category.deactivate();
		categoryRepository.saveAndFlush(category);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "Inactive Category Resource"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INACTIVE_CATEGORY\"");
	}

	@Test
	void blankNameIsRejectedWithValidationError() {
		Category category = activeCategory("Blank Name Check");

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "   "), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"name\"");
	}

	@Test
	void missingDescriptionIsRejectedWithValidationError() {
		Category category = activeCategory("Missing Description Check");
		HttpEntity<String> request = jsonEntity(String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"No Description\",\"addressLine1\":\"123 Main St\","
						+ "\"city\":\"Halifax\",\"province\":\"NS\",\"postalCode\":\"B3H 4R2\"}",
				category.getId()));

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/resources", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"description\"");
	}

	@Test
	void invalidEmailIsRejected() {
		Category category = activeCategory("Invalid Email Check");
		HttpEntity<String> request = jsonEntity(String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"Invalid Email Resource\",\"description\":\"Test.\","
						+ "\"addressLine1\":\"123 Main St\",\"city\":\"Halifax\",\"province\":\"NS\","
						+ "\"postalCode\":\"B3H 4R2\",\"email\":\"not-an-email\"}",
				category.getId()));

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/resources", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"email\"");
	}

	@Test
	void unsafeWebsiteSchemeIsRejected() {
		Category category = activeCategory("Unsafe Website Check");
		HttpEntity<String> request = jsonEntity(String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"Unsafe Website Resource\",\"description\":\"Test.\","
						+ "\"addressLine1\":\"123 Main St\",\"city\":\"Halifax\",\"province\":\"NS\","
						+ "\"postalCode\":\"B3H 4R2\",\"websiteUrl\":\"javascript:alert(1)\"}",
				category.getId()));

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/resources", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"websiteUrl\"");
	}

	@Test
	void invalidPostalCodeIsRejected() {
		Category category = activeCategory("Invalid Postal Code Check");
		HttpEntity<String> request = jsonEntity(String.format(Locale.ROOT,
				"{\"categoryId\":%d,\"name\":\"Invalid Postal Code Resource\",\"description\":\"Test.\","
						+ "\"addressLine1\":\"123 Main St\",\"city\":\"Halifax\",\"province\":\"NS\","
						+ "\"postalCode\":\"12345\"}",
				category.getId()));

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/resources", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"postalCode\"");
	}

	@Test
	void malformedJsonIsRejectedWithoutLeakingInternals() {
		HttpEntity<String> request = jsonEntity("{ this is not valid json");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/resources", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
		assertThat(response.getBody().toLowerCase())
				.doesNotContain("exception", "stacktrace", "com.fasterxml", "com.hfxconnect");
	}

	@Test
	void duplicateResourceSlugIsRejectedWithConflict() {
		Category category = activeCategory("Duplicate Slug Check");
		String name = "Duplicate Resource " + UUID.randomUUID();
		ResponseEntity<ResourceResponse> first = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> second = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), String.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(second.getBody()).contains("\"code\":\"RESOURCE_CONFLICT\"");
	}

	@Test
	void createdResourceCanBeRetrievedById() {
		Category category = activeCategory("Retrieve By Id Check");
		String name = "Retrieve By Id Resource " + UUID.randomUUID();
		ResourceResponse created = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class).getBody();

		ResponseEntity<ResourceResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/" + created.id(), ResourceResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().name()).isEqualTo(name);
	}

	@Test
	void createdResourceCanBeRetrievedBySlug() {
		Category category = activeCategory("Retrieve By Slug Check");
		String name = "Retrieve By Slug Resource " + UUID.randomUUID();
		ResourceResponse created = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class).getBody();

		ResponseEntity<ResourceResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/slug/" + created.slug(), ResourceResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().slug()).isEqualTo(created.slug());
	}

	@Test
	void missingResourceByIdReturns404WithDocumentedErrorShape() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/" + UUID.randomUUID(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"", "\"timestamp\"", "\"status\":404");
	}

	@Test
	void missingResourceBySlugReturns404() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/slug/does-not-exist-" + UUID.randomUUID(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void deactivatedResourceIsNotFoundThroughEitherPublicDetailRoute() {
		Category category = activeCategory("Deactivated Visibility Check");
		String name = "Deactivated Resource " + UUID.randomUUID();
		ResourceResponse created = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class).getBody();

		resourceService.deactivate(created.id());

		assertThat(restTemplate.getForEntity("/api/v1/resources/" + created.id(), String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(restTemplate.getForEntity("/api/v1/resources/slug/" + created.slug(), String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deactivatedResourceIsExcludedFromPublicListing() {
		Category category = activeCategory("Deactivated Listing Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse created = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "Listed Then Deactivated " + marker),
				ResourceResponse.class).getBody();
		resourceService.deactivate(created.id());

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?categoryId=" + category.getId() + "&size=100", ResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.doesNotContain("Listed Then Deactivated " + marker);
	}

	@Test
	void listReturnsAPageContainingACreatedResource() {
		Category category = activeCategory("List Check");
		String name = "List Check Resource " + UUID.randomUUID();
		restTemplate.postForEntity("/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?page=0&size=50", ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name).contains(name);
		assertThat(response.getBody().size()).isEqualTo(50);
	}

	@Test
	void categoryFilterOnlyReturnsResourcesInThatCategory() {
		Category categoryA = activeCategory("Filter Category A");
		Category categoryB = activeCategory("Filter Category B");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(categoryA.getId(), "In A " + marker), ResourceResponse.class);
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(categoryB.getId(), "In B " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?categoryId=" + categoryA.getId() + "&size=100", ResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.contains("In A " + marker)
				.doesNotContain("In B " + marker);
	}

	@Test
	void negativePageIsRejectedWithInvalidPagination() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources?page=-1", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_PAGINATION\"");
	}

	@Test
	void sizeAboveTheMaximumIsRejectedWithInvalidPagination() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources?size=1000", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_PAGINATION\"");
	}

	@Test
	void unsupportedSortValueIsRejected() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources?sort=bogus", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_SORT\"");
	}

	@Test
	void openApiDocumentIncludesTheResourceEndpoints() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("/api/v1/resources");
		assertThat(response.getBody()).contains("/api/v1/resources/{id}");
		assertThat(response.getBody()).contains("/api/v1/resources/slug/{slug}");
	}

	@Test
	void categoryEndpointsStillWorkAlongsideResourceEndpoints() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories?size=1", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private Category activeCategory(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "cat-" + marker, null));
	}

	private HttpEntity<ResourceCreateRequest> createRequest(Long categoryId, String name) {
		HttpHeaders headers = authedJsonHeaders();
		ResourceCreateRequest request = new ResourceCreateRequest(categoryId, name,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, null, null, null, null, null);
		return new HttpEntity<>(request, headers);
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
