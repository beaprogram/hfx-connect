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
 * rather than an open {@code "*"} policy.
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
	void preflightFromAnUnconfiguredOriginIsRejected() {
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin("https://not-the-real-frontend.example.com");
		headers.set("Access-Control-Request-Method", "GET");

		ResponseEntity<Void> response = restTemplate.exchange("/api/v1/resources", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
	}

}
