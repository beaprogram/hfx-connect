package com.hfxconnect.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
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
 * Verifies {@link WebCorsConfig}'s actual runtime behavior: a real preflight
 * request from the configured frontend origin is allowed, and one from an
 * arbitrary, unconfigured origin is not — proving this is a genuine allowlist
 * rather than an open {@code "*"} policy. Also verifies the Milestone 5B
 * {@code allowCredentials(true)} change (see ADR-008) took effect for the
 * allowed origin specifically, without changing the unconfigured-origin
 * rejection behavior at all.
 */
@AutoConfigureTestRestTemplate
class CorsConfigurationIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void preflightFromTheConfiguredFrontendOriginIsAllowed() {
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin("http://localhost:3000");
		headers.set("Access-Control-Request-Method", "GET");

		ResponseEntity<Void> response = restTemplate.exchange("/api/v1/resources", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
	}

	@Test
	void preflightFromTheConfiguredOriginAllowsCredentials() {
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin("http://localhost:3000");
		headers.set("Access-Control-Request-Method", "POST");

		ResponseEntity<Void> response = restTemplate.exchange("/api/v1/auth/login", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials")).isEqualTo("true");
	}

	@Test
	void preflightForAProtectedRouteAllowsTheAuthorizationHeader() {
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin("http://localhost:3000");
		headers.set("Access-Control-Request-Method", "GET");
		headers.set("Access-Control-Request-Headers", "Authorization");

		ResponseEntity<Void> response = restTemplate.exchange("/api/v1/users/me", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getHeaders().getAccessControlAllowHeaders()).contains("Authorization");
	}

	@Test
	void preflightAllowsPutAndDeleteForTheSavedResourcesRoutes() {
		HttpHeaders putHeaders = new HttpHeaders();
		putHeaders.setOrigin("http://localhost:3000");
		putHeaders.set("Access-Control-Request-Method", "PUT");
		putHeaders.set("Access-Control-Request-Headers", "Authorization");

		ResponseEntity<Void> putResponse = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/11111111-1111-1111-1111-111111111111", HttpMethod.OPTIONS,
				new HttpEntity<>(putHeaders), Void.class);

		assertThat(putResponse.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.PUT);

		HttpHeaders deleteHeaders = new HttpHeaders();
		deleteHeaders.setOrigin("http://localhost:3000");
		deleteHeaders.set("Access-Control-Request-Method", "DELETE");
		deleteHeaders.set("Access-Control-Request-Headers", "Authorization");

		ResponseEntity<Void> deleteResponse = restTemplate.exchange(
				"/api/v1/users/me/saved-resources/11111111-1111-1111-1111-111111111111", HttpMethod.OPTIONS,
				new HttpEntity<>(deleteHeaders), Void.class);

		assertThat(deleteResponse.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.DELETE);
	}

	@Test
	void preflightFromAnUnconfiguredOriginIsRejected() {
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin("https://not-the-real-frontend.example.com");
		headers.set("Access-Control-Request-Method", "GET");

		ResponseEntity<Void> response = restTemplate.exchange("/api/v1/resources", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
		assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials")).isNull();
	}

}
