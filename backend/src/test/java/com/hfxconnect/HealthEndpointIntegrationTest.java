package com.hfxconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@AutoConfigureTestRestTemplate
class HealthEndpointIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void healthEndpointReportsUpWhenTheDatabaseIsReachable() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}

	@Test
	void healthEndpointDoesNotLeakCredentialsOrConnectionDetailsOrComponentBreakdown() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		String body = response.getBody();
		assertThat(body).isNotNull();
		// show-details=when-authorized means anonymous requests must not see a
		// "components" breakdown (which would include the db sub-status) or any
		// connection/credential details.
		assertThat(body.toLowerCase()).doesNotContain(
				"password", "jdbc:", "hikari", "exception", "stacktrace", "\"components\"", "\"db\"");
	}

}
