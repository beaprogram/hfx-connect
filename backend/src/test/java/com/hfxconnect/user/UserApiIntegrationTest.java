package com.hfxconnect.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full HTTP-layer integration tests against the real database (via the
 * shared Testcontainers singleton container) and the real {@code BCryptPasswordEncoder}
 * bean (not mocked) — this is the layer that proves password hashing, not
 * just its plumbing, actually works end to end.
 *
 * <p>Unlike {@link UserRepositoryIntegrationTest}, these requests are handled
 * by the embedded server's own thread, so test-method {@code @Transactional}
 * rollback does not apply here. Each test uses a unique, UUID-suffixed email
 * instead of relying on cleanup, so tests remain independent of execution
 * order and of each other.
 */
@AutoConfigureTestRestTemplate
class UserApiIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String VALID_PASSWORD = "correcthorsebattery";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private DataSource dataSource;

	@Test
	void registerReturns201WithSafeResponseBody() {
		String email = "register-" + UUID.randomUUID() + "@example.org";

		ResponseEntity<UserResponse> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, VALID_PASSWORD), UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().id()).isNotNull();
		assertThat(response.getBody().email()).isEqualTo(email);
		assertThat(response.getBody().role()).isEqualTo(Role.USER);
		assertThat(response.getBody().status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(response.getBody().emailVerified()).isFalse();
		assertThat(response.getBody().createdAt()).isNotNull();
	}

	@Test
	void registerResponseBodyNeverContainsAPasswordField() {
		String email = "leak-check-" + UUID.randomUUID() + "@example.org";

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, VALID_PASSWORD), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().toLowerCase()).doesNotContain("password", "hash");
	}

	@Test
	void registeredPasswordIsStoredHashedNotInPlaintext() {
		String email = "hash-storage-check-" + UUID.randomUUID() + "@example.org";

		restTemplate.postForEntity("/api/v1/auth/register", registrationRequest(email, VALID_PASSWORD), UserResponse.class);

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String storedHash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE normalized_email = ?", String.class, email);

		assertThat(storedHash).isNotEqualTo(VALID_PASSWORD);
		assertThat(storedHash).doesNotContain(VALID_PASSWORD);
		assertThat(storedHash).startsWith("$2a$").containsPattern("\\$2[aby]\\$12\\$.+");
	}

	@Test
	void missingEmailIsRejectedWithValidationError() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest("", VALID_PASSWORD), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void missingEmailFieldEntirelyIsRejectedWithValidationError() {
		HttpEntity<String> request = jsonEntity("{\"password\":\"" + VALID_PASSWORD + "\"}");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void invalidEmailFormatIsRejected() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest("not-an-email", VALID_PASSWORD), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(response.getBody()).contains("\"email\"");
	}

	@Test
	void missingPasswordIsRejected() {
		HttpEntity<String> request = jsonEntity("{\"email\":\"missing-password-" + UUID.randomUUID() + "@example.org\"}");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void weakPasswordIsRejected() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest("weak-pw-" + UUID.randomUUID() + "@example.org", "short"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(response.getBody()).contains("\"password\"");
	}

	@Test
	void commonPasswordIsRejected() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest("common-pw-" + UUID.randomUUID() + "@example.org", "password1"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void duplicateEmailIsRejectedWithConflict() {
		String email = "duplicate-" + UUID.randomUUID() + "@example.org";
		ResponseEntity<UserResponse> first = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, VALID_PASSWORD), UserResponse.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> second = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, VALID_PASSWORD), String.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(second.getBody()).contains("\"code\":\"USER_CONFLICT\"");
	}

	@Test
	void differentlyCasedDuplicateEmailIsRejectedWithConflict() {
		String email = "case-duplicate-" + UUID.randomUUID() + "@example.org";
		ResponseEntity<UserResponse> first = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, VALID_PASSWORD), UserResponse.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> second = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email.toUpperCase(), VALID_PASSWORD), String.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(second.getBody()).contains("\"code\":\"USER_CONFLICT\"");
	}

	@Test
	void malformedJsonIsRejectedWithoutLeakingInternals() {
		HttpEntity<String> request = jsonEntity("{ this is not valid json");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
		assertThat(response.getBody().toLowerCase())
				.doesNotContain("exception", "stacktrace", "com.fasterxml", "com.hfxconnect");
	}

	@Test
	void missingRequestBodyIsRejected() {
		HttpEntity<String> request = jsonEntity("");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
	}

	/**
	 * Documents and locks in the project's deliberately chosen behavior for
	 * privilege-bearing fields (see {@link RegistrationRequest}'s Javadoc for
	 * why strict rejection was investigated and not implemented for this
	 * project's specific Jackson 3.x/Spring Boot 4.1 stack): submitting one
	 * has no effect and does not fail the request — privilege escalation is
	 * impossible regardless, since {@link RegistrationRequest} has no field
	 * for any of these values to bind to in the first place.
	 */
	@Test
	void submittingARoleFieldHasNoEffectAndDoesNotEscalatePrivilege() {
		String email = "no-escalation-role-" + UUID.randomUUID() + "@example.org";
		HttpEntity<String> request = jsonEntity(
				"{\"email\":\"" + email + "\",\"password\":\"" + VALID_PASSWORD + "\",\"role\":\"ADMIN\"}");

		ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().role()).isEqualTo(Role.USER);
	}

	@Test
	void submittingAStatusFieldHasNoEffect() {
		String email = "no-escalation-status-" + UUID.randomUUID() + "@example.org";
		HttpEntity<String> request = jsonEntity(
				"{\"email\":\"" + email + "\",\"password\":\"" + VALID_PASSWORD + "\",\"status\":\"SUSPENDED\"}");

		ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().status()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void submittingAnEmailVerifiedFieldHasNoEffect() {
		String email = "no-escalation-verified-" + UUID.randomUUID() + "@example.org";
		HttpEntity<String> request = jsonEntity(
				"{\"email\":\"" + email + "\",\"password\":\"" + VALID_PASSWORD + "\",\"emailVerified\":true}");

		ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().emailVerified()).isFalse();
	}

	@Test
	void submittingAPasswordHashFieldHasNoEffectAndTheRealPasswordIsStillHashedNormally() {
		String email = "no-escalation-hash-" + UUID.randomUUID() + "@example.org";
		String forgedHash = "$2a$12$forgedforgedforgedforgedforgedforgedforgedforgedforgedfor";
		HttpEntity<String> request = jsonEntity(
				"{\"email\":\"" + email + "\",\"password\":\"" + VALID_PASSWORD + "\",\"passwordHash\":\"" + forgedHash + "\"}");

		ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String storedHash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE normalized_email = ?", String.class, email);
		assertThat(storedHash).isNotEqualTo(forgedHash);
	}

	@Test
	void aPrivilegeFieldInTheRequestDoesNotLeakInternalDetails() {
		String email = "no-escalation-leak-check-" + UUID.randomUUID() + "@example.org";
		HttpEntity<String> request = jsonEntity(
				"{\"email\":\"" + email + "\",\"password\":\"" + VALID_PASSWORD + "\",\"role\":\"ADMIN\"}");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

		assertThat(response.getBody().toLowerCase()).doesNotContain(
				"exception", "stacktrace", "com.fasterxml", "com.hfxconnect", "unrecognizedpropertyexception");
	}

	@Test
	void anOversizedByteLengthPasswordIsRejectedWithTheStandardValidationErrorShape() {
		// 72 repetitions of 'é' (2 bytes each in UTF-8): 72 Java chars, but
		// 144 UTF-8 bytes — exceeds BCrypt's 72-byte limit despite satisfying
		// a naive 72-character check.
		String oversizedPassword = "é".repeat(72);
		String email = "byte-limit-api-" + UUID.randomUUID() + "@example.org";

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, oversizedPassword), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(response.getBody()).contains("\"password\"");
		assertThat(response.getBody()).contains("\"timestamp\"");
		assertThat(response.getBody().toLowerCase()).doesNotContain(
				"exception", "stacktrace", "illegalargumentexception", "bcrypt", "com.hfxconnect");
	}

	@Test
	void aSeventyTwoByteAsciiPasswordIsAcceptedThroughTheFullApi() {
		String password = "a1" + "b".repeat(70);
		String email = "byte-limit-accept-" + UUID.randomUUID() + "@example.org";

		ResponseEntity<UserResponse> response = restTemplate.postForEntity(
				"/api/v1/auth/register", registrationRequest(email, password), UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void openApiDocumentIncludesTheRegistrationEndpoint() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("/api/v1/auth/register");
	}

	@Test
	void categoryApiRemainsFunctionalAlongsideRegistration() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void resourceApiRemainsFunctionalAlongsideRegistration() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void healthEndpointRemainsUpAlongsideRegistration() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}

	private static HttpEntity<RegistrationRequest> registrationRequest(String email, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(new RegistrationRequest(email, password), headers);
	}

	private static HttpEntity<String> jsonEntity(String rawJson) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(rawJson, headers);
	}

}
