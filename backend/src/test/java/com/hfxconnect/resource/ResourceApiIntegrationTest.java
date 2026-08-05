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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
		String marker = UUID.randomUUID().toString();
		String name = "List Check Resource " + marker;
		restTemplate.postForEntity("/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class);

		// Scoped with the same marker every other resource-creating test in
		// this file uses (q=/categoryId=) — a plain, unscoped page-0 query
		// implicitly assumed "fewer than size resources exist across the
		// whole shared test database," which stopped holding once enough
		// other test classes (Milestone 8B's resourcesubmission/
		// correctionreport suites among them) started creating their own
		// resources in the same shared Testcontainers database.
		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + marker + "&page=0&size=50", ResourcePageResponse.class);

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

	// ---- Keyword search (Milestone 6A) — see ADR-010 ----

	@Test
	void searchByKeywordReturnsTheMatchingResource() {
		Category category = activeCategory("Search Keyword Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "Halifax Central Library " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("Central Library " + marker), ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.contains("Halifax Central Library " + marker);
	}

	@Test
	void searchIsCaseInsensitiveOverHttp() {
		Category category = activeCategory("Search Case Insensitive Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "Newcomer Support " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("NEWCOMER SUPPORT " + marker.toUpperCase(Locale.ROOT)), ResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.contains("Newcomer Support " + marker);
	}

	@Test
	void searchCombinesWithCategoryIdOverHttp() {
		Category categoryA = activeCategory("Search Http Combo A");
		Category categoryB = activeCategory("Search Http Combo B");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(categoryA.getId(), "In A " + marker), ResourceResponse.class);
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(categoryB.getId(), "In B " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode(marker) + "&categoryId=" + categoryA.getId(), ResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.containsExactly("In A " + marker);
	}

	@Test
	void searchCombinesWithSortAndPaginationOverHttp() {
		Category category = activeCategory("Search Http Sort Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "B Http Sorted " + marker), ResourceResponse.class);
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "A Http Sorted " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("Http Sorted " + marker) + "&size=1&page=0", ResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.containsExactly("A Http Sorted " + marker);
		assertThat(response.getBody().totalElements()).isEqualTo(2);
	}

	@Test
	void blankQOverHttpBehavesAsNoKeywordFilter() {
		Category category = activeCategory("Search Http Blank Check");
		String name = "Blank Http Query " + UUID.randomUUID();
		restTemplate.postForEntity("/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("   ") + "&categoryId=" + category.getId(), ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name).contains(name);
	}

	@Test
	void queryOverTheMaximumLengthReturns400() {
		String tooLong = "a".repeat(ResourceSearchQuery.MAX_LENGTH + 1);

		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources?q=" + tooLong, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_SEARCH_QUERY\"");
	}

	@Test
	void aPercentSignInTheQueryIsTreatedAsALiteralCharacterOverHttp() {
		Category category = activeCategory("Search Http Wildcard Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "Fifty Percent Off " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("nonexistent%" + marker), ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).isEmpty();
	}

	@Test
	void noMatchingResourcesReturns200WithEmptyContentNotAnError() {
		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("no-resource-should-ever-match-" + UUID.randomUUID()), ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).isEmpty();
		assertThat(response.getBody().totalElements()).isZero();
	}

	@Test
	void searchRequiresNoAuthenticationToken() {
		Category category = activeCategory("Search Public Access Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), "Public Search " + marker), ResourceResponse.class);

		// No Authorization header attached — a plain, unauthenticated GET.
		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode("Public Search " + marker), ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.contains("Public Search " + marker);
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
	void openApiDocumentIncludesTheKeywordSearchParameter() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"name\":\"q\"");
	}

	// ---- Cost/verification/openNow filters and operating hours (Milestone 6B) — see ADR-011 ----

	@Test
	void costTypeFilterOverHttpReturnsOnlyMatchingResources() {
		Category category = activeCategory("Http Cost Filter Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Free " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode(marker) + "&costType=FREE", ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		// A brand-new resource defaults to CostType.UNKNOWN (createRequest passes no costType), so FREE never matches.
		assertThat(response.getBody().content()).isEmpty();
	}

	@Test
	void invalidCostTypeOverHttpReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources?costType=NOT_REAL", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_COST_TYPE\"");
	}

	@Test
	void verificationStatusFilterOverHttpReturnsOnlyMatchingResources() {
		Category category = activeCategory("Http Verification Filter Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Unverified " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode(marker) + "&verificationStatus=UNVERIFIED", ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.contains("Http Unverified " + marker);
	}

	@Test
	void invalidVerificationStatusOverHttpReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources?verificationStatus=NOT_REAL", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_VERIFICATION_STATUS\"");
	}

	@Test
	void malformedOpenNowOverHttpReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources?openNow=maybe", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_OPEN_NOW_FILTER\"");
	}

	@Test
	void openNowTrueExcludesResourcesWithNoScheduleOverHttp() {
		Category category = activeCategory("Http Open Now No Schedule Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http No Schedule " + marker), ResourceResponse.class);

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode(marker) + "&openNow=true", ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).isEmpty();
		assertThat(response.getBody().totalElements()).isZero();
	}

	@Test
	void combinedFiltersOverHttpNarrowToTheExactMatch() {
		Category category = activeCategory("Http Combined Filters Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Combined " + marker), ResourceResponse.class).getBody();
		putOpenNowSchedule(created.id());

		ResponseEntity<ResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources?q=" + encode(marker) + "&categoryId=" + category.getId()
						+ "&verificationStatus=UNVERIFIED&openNow=true", ResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(ResourceSummaryResponse::name)
				.containsExactly("Http Combined " + marker);
	}

	@Test
	void resourceDetailIncludesTheHoursSection() {
		Category category = activeCategory("Http Detail Hours Check");
		String name = "Http Detail Hours Resource " + UUID.randomUUID();
		ResourceResponse created = restTemplate.postForEntity(
				"/api/v1/resources", createRequest(category.getId(), name), ResourceResponse.class).getBody();

		ResponseEntity<ResourceResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/" + created.id(), ResourceResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().hours()).isNotNull();
		assertThat(response.getBody().hours().timezone()).isEqualTo("America/Halifax");
		assertThat(response.getBody().hours().hoursStatus()).isEqualTo(HoursStatus.UNKNOWN);
		assertThat(response.getBody().hours().openNow()).isNull();
	}

	@Test
	void operatingHoursTimesSerializeAsIsoLocalTimeWithSeconds() {
		Category category = activeCategory("Time Format Check");
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Time Format Resource " + UUID.randomUUID()),
				ResourceResponse.class).getBody();
		HttpEntity<String> request = jsonEntity(
				"{\"hours\":[{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":\"09:00\",\"closesAt\":\"17:30\"}]}");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + created.id() + "/operating-hours", HttpMethod.PUT, request, String.class);

		// Confirms the actual wire format frontend Zod schemas/formatters must
		// parse: Jackson's default JSR-310 LocalTime serialization always
		// includes seconds ("09:00:00"), never the zero-seconds-omitted
		// "09:00" that LocalTime#toString() alone would produce.
		assertThat(response.getBody()).contains("\"opensAt\":\"09:00:00\"", "\"closesAt\":\"17:30:00\"");
	}

	@Test
	void replaceOperatingHoursAsAdminSucceeds() {
		Category category = activeCategory("Http Replace Hours Check");
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Replace Hours Resource " + UUID.randomUUID()),
				ResourceResponse.class).getBody();

		HttpEntity<String> request = jsonEntity(
				"{\"hours\":[{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":\"09:00\",\"closesAt\":\"17:00\"}]}");

		ResponseEntity<OperatingHoursResponse> response = restTemplate.exchange(
				"/api/v1/resources/" + created.id() + "/operating-hours", HttpMethod.PUT, request,
				OperatingHoursResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().weeklyHours()).hasSize(1);
		assertThat(response.getBody().weeklyHours().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

		ResponseEntity<ResourceResponse> detail = restTemplate.getForEntity(
				"/api/v1/resources/" + created.id(), ResourceResponse.class);
		assertThat(detail.getBody().hours().weeklyHours()).hasSize(1);
	}

	@Test
	void replaceOperatingHoursRejectsAnInvalidScheduleWith400() {
		Category category = activeCategory("Http Replace Hours Invalid Check");
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Invalid Hours Resource " + UUID.randomUUID()),
				ResourceResponse.class).getBody();

		HttpEntity<String> request = jsonEntity(
				"{\"hours\":[{\"dayOfWeek\":\"MONDAY\",\"closed\":true,\"opensAt\":\"09:00\"}]}");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + created.id() + "/operating-hours", HttpMethod.PUT, request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void replaceOperatingHoursForAMissingResourceReturns404() {
		HttpEntity<String> request = jsonEntity(
				"{\"hours\":[{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":\"09:00\",\"closesAt\":\"17:00\"}]}");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + UUID.randomUUID() + "/operating-hours", HttpMethod.PUT, request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void openApiDocumentIncludesTheNewFilterParametersAndOperatingHoursEndpoint() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"name\":\"costType\"");
		assertThat(response.getBody()).contains("\"name\":\"verificationStatus\"");
		assertThat(response.getBody()).contains("\"name\":\"openNow\"");
		assertThat(response.getBody()).contains("/api/v1/resources/{id}/operating-hours");
	}

	// ---- Resource location and nearby search (Milestone 7A) — see ADR-012 ----

	private static final double LIBRARY_LAT = 44.6488;
	private static final double LIBRARY_LON = -63.5752;
	private static final double DALHOUSIE_LAT = 44.6366;
	private static final double DALHOUSIE_LON = -63.5934;
	private static final double TORONTO_LAT = 43.6532;
	private static final double TORONTO_LON = -79.3832;

	@Test
	void replaceLocationAsAdminSucceeds() {
		Category category = activeCategory("Http Replace Location Check");
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Replace Location Resource " + UUID.randomUUID()),
				ResourceResponse.class).getBody();

		HttpEntity<String> request = jsonEntity(
				String.format(Locale.ROOT, "{\"latitude\":%s,\"longitude\":%s}", LIBRARY_LAT, LIBRARY_LON));
		ResponseEntity<ResourceLocationResponse> response = restTemplate.exchange(
				"/api/v1/resources/" + created.id() + "/location", HttpMethod.PUT, request,
				ResourceLocationResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().resourceId()).isEqualTo(created.id());
		assertThat(response.getBody().latitude()).isEqualTo(LIBRARY_LAT);
		assertThat(response.getBody().longitude()).isEqualTo(LIBRARY_LON);
	}

	@Test
	void replaceLocationRejectsInvalidCoordinatesWith400() {
		Category category = activeCategory("Http Invalid Location Check");
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Invalid Location Resource " + UUID.randomUUID()),
				ResourceResponse.class).getBody();

		HttpEntity<String> request = jsonEntity("{\"latitude\":91,\"longitude\":0}");
		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + created.id() + "/location", HttpMethod.PUT, request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"", "\"latitude\"");
	}

	@Test
	void replaceLocationForAMissingResourceReturns404() {
		HttpEntity<String> request = jsonEntity(
				String.format(Locale.ROOT, "{\"latitude\":%s,\"longitude\":%s}", LIBRARY_LAT, LIBRARY_LON));

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + UUID.randomUUID() + "/location", HttpMethod.PUT, request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void nearbyReturnsResourcesWithinTheRadiusOrderedNearestFirst() {
		Category category = activeCategory("Http Nearby Order Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse near = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby Near " + marker), ResourceResponse.class).getBody();
		ResourceResponse far = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby Far " + marker), ResourceResponse.class).getBody();
		putLocation(near.id(), DALHOUSIE_LAT, DALHOUSIE_LON);
		putLocation(far.id(), LIBRARY_LAT + 0.02, LIBRARY_LON + 0.02);

		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&radiusKm=10&q=" + encode(marker),
				NearbyResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Http Nearby Near " + marker, "Http Nearby Far " + marker);
	}

	@Test
	void nearbyExcludesAResourceWithNoLocation() {
		Category category = activeCategory("Http Nearby No Location Check");
		String marker = UUID.randomUUID().toString();
		restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby No Location " + marker), ResourceResponse.class);

		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&radiusKm=50&q=" + encode(marker),
				NearbyResourcePageResponse.class);

		assertThat(response.getBody().content()).isEmpty();
		assertThat(response.getBody().totalElements()).isZero();
	}

	@Test
	void nearbyUsesTheDefaultRadiusOverHttp() {
		Category category = activeCategory("Http Nearby Default Radius Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby Default Radius " + marker), ResourceResponse.class).getBody();
		putLocation(created.id(), DALHOUSIE_LAT, DALHOUSIE_LON);

		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&q=" + encode(marker),
				NearbyResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Http Nearby Default Radius " + marker);
	}

	@Test
	void nearbyMissingLatitudeReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?longitude=" + LIBRARY_LON, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_LATITUDE\"");
	}

	@Test
	void nearbyMissingLongitudeReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_LONGITUDE\"");
	}

	@Test
	void nearbyOutOfRangeLatitudeReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=91&longitude=" + LIBRARY_LON, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_LATITUDE\"");
	}

	@Test
	void nearbyOutOfRangeLongitudeReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=181", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_LONGITUDE\"");
	}

	@Test
	void nearbyRadiusAboveTheMaximumReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON + "&radiusKm=51",
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_RADIUS\"");
	}

	@Test
	void nearbyNonPositiveRadiusReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON + "&radiusKm=0",
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_RADIUS\"");
	}

	@Test
	void nearbyNoMatchesReturns200WithEmptyContentNotAnError() {
		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + TORONTO_LAT + "&longitude=" + TORONTO_LON
						+ "&radiusKm=1&q=" + encode("no-resource-should-ever-match-" + UUID.randomUUID()),
				NearbyResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).isEmpty();
		assertThat(response.getBody().totalElements()).isZero();
	}

	@Test
	void nearbyCombinesWithCostTypeAndVerificationStatusOverHttp() {
		Category category = activeCategory("Http Nearby Combined Filters Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby Combined " + marker), ResourceResponse.class).getBody();
		putLocation(created.id(), LIBRARY_LAT, LIBRARY_LON);

		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&radiusKm=5&q=" + encode(marker) + "&verificationStatus=UNVERIFIED",
				NearbyResourcePageResponse.class);

		assertThat(response.getBody().content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Http Nearby Combined " + marker);
	}

	@Test
	void nearbyPaginationTotalsAreCorrectOverHttp() {
		Category category = activeCategory("Http Nearby Pagination Check");
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
					createRequest(category.getId(), "Http Nearby Paginated " + marker + " " + i),
					ResourceResponse.class).getBody();
			putLocation(created.id(), LIBRARY_LAT, LIBRARY_LON);
		}

		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&radiusKm=5&q=" + encode(marker) + "&size=2",
				NearbyResourcePageResponse.class);

		assertThat(response.getBody().content()).hasSize(2);
		assertThat(response.getBody().totalElements()).isEqualTo(3);
		assertThat(response.getBody().totalPages()).isEqualTo(2);
	}

	@Test
	void nearbyResponseIncludesCoordinatesAndDistance() {
		Category category = activeCategory("Http Nearby Result Shape Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby Result Shape " + marker), ResourceResponse.class).getBody();
		putLocation(created.id(), LIBRARY_LAT, LIBRARY_LON);

		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&radiusKm=1&q=" + encode(marker),
				NearbyResourcePageResponse.class);

		NearbyResourceSummaryResponse result = response.getBody().content().get(0);
		assertThat(result.latitude()).isEqualTo(LIBRARY_LAT);
		assertThat(result.longitude()).isEqualTo(LIBRARY_LON);
		assertThat(result.distanceMeters()).isNotNull();
	}

	@Test
	void nearbyRequiresNoAuthenticationToken() {
		Category category = activeCategory("Http Nearby Public Access Check");
		String marker = UUID.randomUUID().toString();
		ResourceResponse created = restTemplate.postForEntity("/api/v1/resources",
				createRequest(category.getId(), "Http Nearby Public " + marker), ResourceResponse.class).getBody();
		putLocation(created.id(), LIBRARY_LAT, LIBRARY_LON);

		// No Authorization header attached — a plain, unauthenticated GET.
		ResponseEntity<NearbyResourcePageResponse> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=" + LIBRARY_LAT + "&longitude=" + LIBRARY_LON
						+ "&radiusKm=1&q=" + encode(marker),
				NearbyResourcePageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).extracting(NearbyResourceSummaryResponse::name)
				.contains("Http Nearby Public " + marker);
	}

	@Test
	void openApiDocumentIncludesNearbyAndLocationEndpoints() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("/api/v1/resources/nearby");
		assertThat(response.getBody()).contains("/api/v1/resources/{id}/location");
		assertThat(response.getBody()).contains("\"name\":\"latitude\"");
		assertThat(response.getBody()).contains("\"name\":\"longitude\"");
		assertThat(response.getBody()).contains("\"name\":\"radiusKm\"");
	}

	@Test
	void categoryEndpointsStillWorkAlongsideResourceEndpoints() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories?size=1", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private void putLocation(UUID resourceId, double latitude, double longitude) {
		HttpEntity<String> request = jsonEntity(
				String.format(Locale.ROOT, "{\"latitude\":%s,\"longitude\":%s}", latitude, longitude));
		restTemplate.exchange("/api/v1/resources/" + resourceId + "/location", HttpMethod.PUT, request, String.class);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

	/**
	 * Sets a schedule guaranteed to be OPEN right now (now-1h to now+1h,
	 * America/Halifax). The entry's day-of-week is derived from the window's
	 * own start instant, not "today" — necessary so this stays correct even
	 * when "now" falls within the first hour after midnight, where
	 * {@code now.minusHours(1)} rolls back into the previous calendar day
	 * (see the equivalent helper in ResourceServiceIntegrationTest for the
	 * full explanation).
	 */
	private void putOpenNowSchedule(UUID resourceId) {
		ZonedDateTime start = ZonedDateTime.now(ZoneId.of("America/Halifax")).minusHours(1);
		ZonedDateTime end = ZonedDateTime.now(ZoneId.of("America/Halifax")).plusHours(1);
		DayOfWeek entryDay = start.getDayOfWeek();
		HttpEntity<String> request = jsonEntity(String.format(Locale.ROOT,
				"{\"hours\":[{\"dayOfWeek\":\"%s\",\"closed\":false,\"opensAt\":\"%s\",\"closesAt\":\"%s\"}]}",
				entryDay, start.toLocalTime(), end.toLocalTime()));
		restTemplate.exchange("/api/v1/resources/" + resourceId + "/operating-hours", HttpMethod.PUT, request, String.class);
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
