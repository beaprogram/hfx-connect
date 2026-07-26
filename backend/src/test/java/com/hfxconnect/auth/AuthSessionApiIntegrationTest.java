package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.user.RegistrationRequest;
import com.hfxconnect.user.UserResponse;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Full HTTP-layer integration tests for login, refresh, and logout against
 * the real database (via the shared Testcontainers singleton container) and
 * real token/cookie machinery — not mocked. See ADR-008 for the design this
 * verifies end to end.
 *
 * <p>{@link TestRestTemplate} does not automatically manage cookies across
 * requests the way a browser would, so cookie values are extracted from
 * {@code Set-Cookie} response headers and re-attached as a {@code Cookie}
 * request header manually — see {@link #cookieHeaderValue} /
 * {@link #requestWithCookie}.
 */
@AutoConfigureTestRestTemplate
class AuthSessionApiIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String VALID_PASSWORD = "correcthorsebattery";
	private static final String COOKIE_NAME = RefreshCookieConfig.COOKIE_NAME;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private DataSource dataSource;

	// ---- Login ----

	@Test
	void loginWithValidCredentialsReturns200WithAccessTokenAndSetsRefreshCookie() {
		String email = registerNewUser();

		ResponseEntity<LoginResponse> response = login(email, VALID_PASSWORD);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().accessToken()).isNotBlank();
		assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
		assertThat(response.getBody().expiresIn()).isGreaterThan(0);
		assertThat(response.getBody().user().email()).isEqualTo(email);
		assertThat(setCookieHeader(response)).isNotNull();
	}

	@Test
	void loginResponseBodyNeverContainsARefreshTokenField() {
		String email = registerNewUser();

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/login", loginRequestEntity(email, VALID_PASSWORD), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().toLowerCase()).doesNotContain("refreshtoken", "password", "hash");
	}

	@Test
	void loginSetsACookieThatIsHttpOnlyPathScopedAndSameSiteLax() {
		String email = registerNewUser();

		ResponseEntity<LoginResponse> response = login(email, VALID_PASSWORD);

		String setCookie = setCookieHeader(response);
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).contains("Path=/api/v1/auth");
		assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
		// Local/test default is AUTH_COOKIE_SECURE=false — the Secure
		// attribute must be genuinely absent, not just unasserted, since a
		// plain-HTTP local/test client could never present it back otherwise.
		assertThat(setCookie).doesNotContainIgnoringCase("Secure");
	}

	@Test
	void wrongPasswordReturnsGenericAuthenticationFailed() {
		String email = registerNewUser();

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/login", loginRequestEntity(email, "the-wrong-password"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_FAILED\"");
		assertThat(response.getBody()).contains("Invalid email or password.");
	}

	@Test
	void unknownEmailReturnsTheExactSameGenericResponseAsWrongPassword() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/login", loginRequestEntity("nobody-" + UUID.randomUUID() + "@example.org", "irrelevant"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_FAILED\"");
		assertThat(response.getBody()).contains("Invalid email or password.");
	}

	@Test
	void loginIsCaseInsensitiveOnEmailLikeRegistrationIs() {
		String email = registerNewUser();

		ResponseEntity<LoginResponse> response = login(email.toUpperCase(), VALID_PASSWORD);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void malformedJsonOnLoginIsRejectedWithoutLeakingInternals() {
		HttpEntity<String> request = jsonEntity("{ not valid json");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"MALFORMED_REQUEST\"");
		assertThat(response.getBody().toLowerCase()).doesNotContain("exception", "stacktrace", "com.hfxconnect");
	}

	@Test
	void missingPasswordFieldOnLoginIsRejectedAsValidationError() {
		HttpEntity<String> request = jsonEntity("{\"email\":\"someone@example.org\"}");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"");
	}

	@Test
	void aCorrectPasswordAgainstASuspendedAccountReturns403AccountUnavailable() {
		String email = registerNewUser();
		setAccountStatus(email, "SUSPENDED");

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/login", loginRequestEntity(email, VALID_PASSWORD), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"ACCOUNT_UNAVAILABLE\"");
	}

	// ---- Refresh ----

	@Test
	void refreshForASuspendedAccountReturns403AndConsumesTheToken() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String cookie = cookieValue(loginResponse);
		setAccountStatus(email, "SUSPENDED");

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(cookie), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"ACCOUNT_UNAVAILABLE\"");

		// The presented token must not remain usable even if the account is
		// later reactivated — it was already consumed by this attempt.
		setAccountStatus(email, "ACTIVE");
		ResponseEntity<String> retry = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(cookie), String.class);
		assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshWithAValidCookieReturns200WithANewAccessTokenAndARotatedCookie() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String originalCookie = cookieValue(loginResponse);

		ResponseEntity<LoginResponse> refreshResponse = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(originalCookie), LoginResponse.class);

		assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refreshResponse.getBody().accessToken()).isNotBlank().isNotEqualTo(loginResponse.getBody().accessToken());
		String newCookie = cookieValue(refreshResponse);
		assertThat(newCookie).isNotBlank().isNotEqualTo(originalCookie);
	}

	@Test
	void presentingTheOriginalCookieAgainAfterARefreshIsRejectedAsReused() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String originalCookie = cookieValue(loginResponse);
		restTemplate.postForEntity("/api/v1/auth/refresh", requestWithCookie(originalCookie), LoginResponse.class);

		ResponseEntity<String> secondAttempt = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(originalCookie), String.class);

		assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(secondAttempt.getBody()).contains("\"code\":\"REFRESH_TOKEN_REUSED\"");
	}

	@Test
	void reuseDetectionAlsoRevokesTheRotatedSessionItself() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String originalCookie = cookieValue(loginResponse);
		ResponseEntity<LoginResponse> firstRefresh = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(originalCookie), LoginResponse.class);
		String rotatedCookie = cookieValue(firstRefresh);

		// Trigger reuse detection with the pre-rotation cookie...
		restTemplate.postForEntity("/api/v1/auth/refresh", requestWithCookie(originalCookie), String.class);

		// ...which must also have killed the session the first refresh
		// legitimately produced, even though it was never itself reused.
		ResponseEntity<String> attemptWithRotatedCookie = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(rotatedCookie), String.class);
		assertThat(attemptWithRotatedCookie.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshWithNoCookieAtAllIsRejectedAsAuthenticationRequired() {
		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/refresh", HttpEntity.EMPTY, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void refreshWithAGarbageCookieValueIsRejectedAsInvalid() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie("not-a-real-token-" + UUID.randomUUID()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"INVALID_REFRESH_TOKEN\"");
	}

	@Test
	void refreshWithAnExpiredSessionIsRejected() {
		String email = registerNewUser();
		UUID userId = findUserId(email);
		String rawToken = "expired-raw-token-" + UUID.randomUUID();
		insertRefreshSession(userId, rawToken, Instant.now().minusSeconds(60), null);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(rawToken), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"REFRESH_TOKEN_EXPIRED\"");
	}

	@Test
	void refreshErrorResponsesDoNotLeakInternalDetails() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie("garbage-" + UUID.randomUUID()), String.class);

		assertThat(response.getBody().toLowerCase()).doesNotContain(
				"exception", "stacktrace", "sql", "com.hfxconnect", "token_hash");
	}

	// ---- Logout ----

	@Test
	void logoutRevokesTheSessionClearsTheCookieAndReturns204() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String cookie = cookieValue(loginResponse);

		ResponseEntity<Void> logoutResponse = restTemplate.postForEntity(
				"/api/v1/auth/logout", requestWithCookie(cookie), Void.class);

		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		String clearCookieHeader = setCookieHeader(logoutResponse);
		assertThat(clearCookieHeader).contains("Max-Age=0");
	}

	@Test
	void refreshAfterLogoutIsRejected() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String cookie = cookieValue(loginResponse);
		restTemplate.postForEntity("/api/v1/auth/logout", requestWithCookie(cookie), Void.class);

		ResponseEntity<String> refreshAttempt = restTemplate.postForEntity(
				"/api/v1/auth/refresh", requestWithCookie(cookie), String.class);

		assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void repeatedLogoutWithTheSameCookieRemainsSafeAndIdempotent() {
		String email = registerNewUser();
		ResponseEntity<LoginResponse> loginResponse = login(email, VALID_PASSWORD);
		String cookie = cookieValue(loginResponse);

		restTemplate.postForEntity("/api/v1/auth/logout", requestWithCookie(cookie), Void.class);
		ResponseEntity<Void> secondLogout = restTemplate.postForEntity(
				"/api/v1/auth/logout", requestWithCookie(cookie), Void.class);

		assertThat(secondLogout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutWithNoCookieAtAllStillReturns204Safely() {
		ResponseEntity<Void> response = restTemplate.postForEntity("/api/v1/auth/logout", HttpEntity.EMPTY, Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutWithAnUnknownCookieValueStillReturns204Safely() {
		ResponseEntity<Void> response = restTemplate.postForEntity(
				"/api/v1/auth/logout", requestWithCookie("unknown-" + UUID.randomUUID()), Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	// ---- OpenAPI and regression ----

	@Test
	void openApiDocumentIncludesTheNewAuthEndpoints() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("/api/v1/auth/login");
		assertThat(response.getBody()).contains("/api/v1/auth/refresh");
		assertThat(response.getBody()).contains("/api/v1/auth/logout");
	}

	@Test
	void registrationRemainsFunctionalAlongsideLoginRefreshLogout() {
		ResponseEntity<UserResponse> response = restTemplate.postForEntity(
				"/api/v1/auth/register",
				new HttpEntity<>(new RegistrationRequest("regression-" + UUID.randomUUID() + "@example.org", VALID_PASSWORD)),
				UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void categoryApiRemainsFunctionalAlongsideAuthenticationSessions() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/categories", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void resourceApiRemainsFunctionalAlongsideAuthenticationSessions() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resources", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void healthEndpointRemainsUpAlongsideAuthenticationSessions() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}

	// ---- Helpers ----

	private String registerNewUser() {
		String email = "session-check-" + UUID.randomUUID() + "@example.org";
		ResponseEntity<UserResponse> response = restTemplate.postForEntity(
				"/api/v1/auth/register", new HttpEntity<>(new RegistrationRequest(email, VALID_PASSWORD)), UserResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return email;
	}

	private ResponseEntity<LoginResponse> login(String email, String password) {
		return restTemplate.postForEntity("/api/v1/auth/login", loginRequestEntity(email, password), LoginResponse.class);
	}

	private static HttpEntity<LoginRequest> loginRequestEntity(String email, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(new LoginRequest(email, password), headers);
	}

	private static HttpEntity<String> jsonEntity(String rawJson) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(rawJson, headers);
	}

	private static HttpEntity<Void> requestWithCookie(String cookieValue) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, COOKIE_NAME + "=" + cookieValue);
		return new HttpEntity<>(null, headers);
	}

	private static String setCookieHeader(ResponseEntity<?> response) {
		return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
	}

	private static String cookieValue(ResponseEntity<?> response) {
		String setCookie = setCookieHeader(response);
		assertThat(setCookie).isNotNull();
		String prefix = COOKIE_NAME + "=";
		int start = setCookie.indexOf(prefix) + prefix.length();
		int end = setCookie.indexOf(';', start);
		return end == -1 ? setCookie.substring(start) : setCookie.substring(start, end);
	}

	private void setAccountStatus(String email, String status) {
		new JdbcTemplate(dataSource).update(
				"UPDATE users SET status = ? WHERE normalized_email = ?", status, email);
	}

	private UUID findUserId(String email) {
		return new JdbcTemplate(dataSource).queryForObject(
				"SELECT id FROM users WHERE normalized_email = ?", UUID.class, email);
	}

	private void insertRefreshSession(UUID userId, String rawToken, Instant expiresAt, Instant revokedAt) {
		new JdbcTemplate(dataSource).update(
				"INSERT INTO refresh_sessions (id, user_id, token_hash, family_id, expires_at, revoked_at) VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), userId, RefreshTokenGenerator.hash(rawToken), UUID.randomUUID(),
				Timestamp.from(expiresAt), revokedAt == null ? null : Timestamp.from(revokedAt));
	}

}
