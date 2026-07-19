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
 * genuinely unmapped path (there is still no resource controller) returned
 * {@code 500 INTERNAL_ERROR} instead of {@code 404}, because Spring's
 * {@code NoResourceFoundException} fell through to the generic exception
 * handler. Fixed with a dedicated handler; this test guards the fix.
 */
@AutoConfigureTestRestTemplate
class GlobalExceptionHandlerIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void anUnmappedRouteReturnsNotFoundRatherThanAServerError() {
		ResponseEntity<ApiError> response = restTemplate.getForEntity("/api/v1/resources", ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
	}

	@Test
	void anUnmappedRouteResponseDoesNotLeakInternalDetails() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources", String.class);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().toLowerCase()).doesNotContain(
				"exception", "stacktrace", "noresourcefoundexception", "springframework");
	}

}
