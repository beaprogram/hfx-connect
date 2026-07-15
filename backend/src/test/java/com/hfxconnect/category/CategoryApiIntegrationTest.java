package com.hfxconnect.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import java.util.UUID;
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
 */
@AutoConfigureTestRestTemplate
class CategoryApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

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
	void listWithAnActiveFalseFilterIsEmptyBecauseNoInactiveCategoryCanBeCreatedYet() {
		ResponseEntity<CategoryPageResponse> response = restTemplate.getForEntity(
				"/api/v1/categories?active=false", CategoryPageResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().content()).isEmpty();
		assertThat(response.getBody().totalElements()).isEqualTo(0);
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

	private static HttpEntity<CategoryCreateRequest> createRequest(String name, String description) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(new CategoryCreateRequest(name, description), headers);
	}

	private static HttpEntity<String> jsonEntity(String rawJson) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(rawJson, headers);
	}

}
