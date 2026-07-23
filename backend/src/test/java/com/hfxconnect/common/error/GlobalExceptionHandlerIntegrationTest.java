package com.hfxconnect.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers {@link GlobalExceptionHandler} behavior that isn't specific to any
 * one domain. Discovered during Milestone 3B verification: requesting a
 * genuinely unmapped path returned {@code 500 INTERNAL_ERROR} instead of
 * {@code 404}, because Spring's {@code NoResourceFoundException} fell
 * through to the generic exception handler. Fixed with a dedicated handler;
 * this test guards the fix.
 *
 * <p>Originally used {@code /api/v1/resources} itself as the example
 * unmapped path (there was no resource controller yet in Milestone 3B).
 * Milestone 3C mapped that exact route, so this now uses a path that is
 * guaranteed to stay unmapped regardless of future milestones.
 */
@AutoConfigureTestRestTemplate
class GlobalExceptionHandlerIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String UNMAPPED_PATH = "/api/v1/this-route-will-never-exist";

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void anUnmappedRouteReturnsNotFoundRatherThanAServerError() {
		ResponseEntity<ApiError> response = restTemplate.getForEntity(UNMAPPED_PATH, ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
	}

	@Test
	void anUnmappedRouteResponseDoesNotLeakInternalDetails() {
		ResponseEntity<String> response = restTemplate.getForEntity(UNMAPPED_PATH, String.class);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().toLowerCase()).doesNotContain(
				"exception", "stacktrace", "noresourcefoundexception", "springframework");
	}

}
