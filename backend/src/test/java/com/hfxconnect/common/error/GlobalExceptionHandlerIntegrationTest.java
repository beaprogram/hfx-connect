package com.hfxconnect.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 *
 * <p><strong>Milestone 5C consequence:</strong> {@code SecurityConfig}'s
 * {@code anyRequest().authenticated()} default now runs ahead of Spring
 * MVC's own routing, so an <em>unauthenticated</em> request to this unmapped
 * path never reaches {@code NoResourceFoundException} at all — it is
 * rejected {@code 401} first (see
 * {@link #anUnauthenticatedRequestToAnUnmappedRouteIsRejectedBeforeRoutingIsAttempted()}).
 * The original {@code 404}-from-{@code GlobalExceptionHandler} behavior this
 * class exists to guard is therefore verified here with a valid (any-role)
 * access token, so the request actually reaches Spring MVC's routing layer.
 */
@AutoConfigureTestRestTemplate
class GlobalExceptionHandlerIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String UNMAPPED_PATH = "/api/v1/this-route-will-never-exist";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	@Test
	void anUnmappedRouteReturnsNotFoundRatherThanAServerError() {
		ResponseEntity<ApiError> response = restTemplate.exchange(
				UNMAPPED_PATH, HttpMethod.GET, authenticatedRequest(), ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
	}

	@Test
	void anUnmappedRouteResponseDoesNotLeakInternalDetails() {
		ResponseEntity<String> response = restTemplate.exchange(
				UNMAPPED_PATH, HttpMethod.GET, authenticatedRequest(), String.class);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().toLowerCase()).doesNotContain(
				"exception", "stacktrace", "noresourcefoundexception", "springframework");
	}

	@Test
	void anUnauthenticatedRequestToAnUnmappedRouteIsRejectedBeforeRoutingIsAttempted() {
		ResponseEntity<ApiError> response = restTemplate.getForEntity(UNMAPPED_PATH, ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_REQUIRED");
	}

	private HttpEntity<Void> authenticatedRequest() {
		User user = userRepository.saveAndFlush(TestUserFactory.withRole(Role.USER));
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessTokenService.issue(user.getId(), Role.USER).token());
		return new HttpEntity<>(headers);
	}

}
