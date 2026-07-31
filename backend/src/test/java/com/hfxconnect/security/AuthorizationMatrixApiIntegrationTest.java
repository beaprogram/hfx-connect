package com.hfxconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryCreateRequest;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.resource.ResourceCreateRequest;
import com.hfxconnect.user.AccountStatus;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.SecretKey;
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
 * The authorization matrix Milestone 5C's brief calls for explicitly: every
 * role against every role-gated route, plus the request-authentication edge
 * cases (missing/malformed/invalid-signature/expired/wrong-issuer/deleted-
 * user/disabled-account tokens) and the "current DB state is authoritative,
 * not a stale JWT claim" guarantee ADR-009 makes. See {@code SecurityConfig}
 * for the policy under test and ADR-009 for the full design rationale.
 *
 * <p>Tokens for the invalid-token cases are built directly with JJWT here
 * (not through {@link AccessTokenService}, which cannot produce an invalid
 * token by construction) — this is the only way to construct a
 * deliberately-wrong-signature/expired/wrong-issuer token for negative
 * testing.
 */
@AutoConfigureTestRestTemplate
class AuthorizationMatrixApiIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String TEST_SIGNING_SECRET = "test-only-signing-secret-never-used-outside-the-test-suite-32bytes";
	private static final String ISSUER = "hfx-connect";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private AccessTokenService accessTokenService;

	// ---- Category creation: role matrix ----

	@Test
	void categoryCreationWithNoTokenIsRejectedWithAuthenticationRequired() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/categories", categoryRequest(), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void categoryCreationAsUserIsForbidden() {
		assertCategoryCreationStatus(Role.USER, HttpStatus.FORBIDDEN);
	}

	@Test
	void categoryCreationAsOrganizationIsForbidden() {
		assertCategoryCreationStatus(Role.ORGANIZATION, HttpStatus.FORBIDDEN);
	}

	@Test
	void categoryCreationAsModeratorIsForbidden() {
		assertCategoryCreationStatus(Role.MODERATOR, HttpStatus.FORBIDDEN);
	}

	@Test
	void categoryCreationAsAdminSucceeds() {
		assertCategoryCreationStatus(Role.ADMIN, HttpStatus.CREATED);
	}

	@Test
	void categoryCreationForbiddenResponseUsesTheStandardAccessDeniedShape() {
		HttpHeaders headers = authHeaders(Role.USER);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/categories", new HttpEntity<>(categoryRequest(), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"ACCESS_DENIED\"");
		assertThat(response.getBody()).contains("You do not have permission to perform this action.");
		assertThat(response.getBody().toLowerCase(Locale.ROOT)).doesNotContain(
				"hasrole", "exception", "stacktrace", "springframework", "com.hfxconnect");
	}

	// ---- Resource creation: role matrix ----

	@Test
	void resourceCreationWithNoTokenIsRejectedWithAuthenticationRequired() {
		Category category = activeCategory();

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/resources", resourceRequest(category.getId()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void resourceCreationAsUserIsForbidden() {
		assertResourceCreationStatus(Role.USER, HttpStatus.FORBIDDEN);
	}

	@Test
	void resourceCreationAsOrganizationIsForbidden() {
		assertResourceCreationStatus(Role.ORGANIZATION, HttpStatus.FORBIDDEN);
	}

	@Test
	void resourceCreationAsModeratorSucceeds() {
		assertResourceCreationStatus(Role.MODERATOR, HttpStatus.CREATED);
	}

	@Test
	void resourceCreationAsAdminSucceeds() {
		assertResourceCreationStatus(Role.ADMIN, HttpStatus.CREATED);
	}

	// ---- Operating-hours replacement: role matrix (Milestone 6B — see ADR-011) ----

	@Test
	void operatingHoursReplacementWithNoTokenIsRejectedWithAuthenticationRequired() {
		Category category = activeCategory();
		UUID resourceId = createResource(category.getId());

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resourceId + "/operating-hours", HttpMethod.PUT,
				new HttpEntity<>(operatingHoursRequest(), jsonHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void operatingHoursReplacementAsUserIsForbidden() {
		assertOperatingHoursReplacementStatus(Role.USER, HttpStatus.FORBIDDEN);
	}

	@Test
	void operatingHoursReplacementAsOrganizationIsForbidden() {
		assertOperatingHoursReplacementStatus(Role.ORGANIZATION, HttpStatus.FORBIDDEN);
	}

	@Test
	void operatingHoursReplacementAsModeratorSucceeds() {
		assertOperatingHoursReplacementStatus(Role.MODERATOR, HttpStatus.OK);
	}

	@Test
	void operatingHoursReplacementAsAdminSucceeds() {
		assertOperatingHoursReplacementStatus(Role.ADMIN, HttpStatus.OK);
	}

	// ---- Location replacement: role matrix (Milestone 7A — see ADR-012) ----

	@Test
	void locationReplacementWithNoTokenIsRejectedWithAuthenticationRequired() {
		Category category = activeCategory();
		UUID resourceId = createResource(category.getId());

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resourceId + "/location", HttpMethod.PUT,
				new HttpEntity<>(locationRequest(), jsonHeaders()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void locationReplacementAsUserIsForbidden() {
		assertLocationReplacementStatus(Role.USER, HttpStatus.FORBIDDEN);
	}

	@Test
	void locationReplacementAsOrganizationIsForbidden() {
		assertLocationReplacementStatus(Role.ORGANIZATION, HttpStatus.FORBIDDEN);
	}

	@Test
	void locationReplacementAsModeratorSucceeds() {
		assertLocationReplacementStatus(Role.MODERATOR, HttpStatus.OK);
	}

	@Test
	void locationReplacementAsAdminSucceeds() {
		assertLocationReplacementStatus(Role.ADMIN, HttpStatus.OK);
	}

	// ---- GET /api/v1/resources/nearby: public regardless of authentication ----

	@Test
	void nearbySearchIsPublicForAnAnonymousCaller() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/resources/nearby?latitude=44.6488&longitude=-63.5752", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void nearbySearchIsAlsoAccessibleToAnAuthenticatedUser() {
		HttpHeaders headers = authHeaders(Role.USER);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/nearby?latitude=44.6488&longitude=-63.5752", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	// ---- GET /api/v1/users/me: request authentication edge cases ----

	@Test
	void currentUserWithNoTokenIsRejected() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/me", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void currentUserWithAMalformedAuthorizationHeaderIsRejected() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, "NotBearer some-garbage-value");

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void currentUserWithAStructurallyInvalidTokenIsRejected() {
		ResponseEntity<String> response = getMeWithToken("Bearer not-a-real-jwt-at-all");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void currentUserWithAnInvalidSignatureIsRejected() {
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = tokenWithWrongSignature(user.getId(), Role.USER);

		ResponseEntity<String> response = getMeWithToken("Bearer " + token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void currentUserWithAnExpiredTokenIsRejected() {
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = expiredToken(user.getId(), Role.USER);

		ResponseEntity<String> response = getMeWithToken("Bearer " + token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void currentUserWithTheWrongIssuerIsRejected() {
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = tokenWithWrongIssuer(user.getId(), Role.USER);

		ResponseEntity<String> response = getMeWithToken("Bearer " + token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void currentUserForADeletedAccountIsRejected() {
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = accessTokenService.issue(user.getId(), Role.USER).token();
		userRepository.delete(user);
		userRepository.flush();

		ResponseEntity<String> response = getMeWithToken("Bearer " + token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void anAccountSuspendedAfterTokenIssuanceLosesAccessOnItsNextRequest() {
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = accessTokenService.issue(user.getId(), Role.USER).token();

		// Confirm the token works before suspension.
		assertThat(getMeWithToken("Bearer " + token).getStatusCode()).isEqualTo(HttpStatus.OK);

		userRepository.saveAndFlush(TestUserFactory.withChangedRoleAndStatus(user, Role.USER, AccountStatus.SUSPENDED));

		ResponseEntity<String> response = getMeWithToken("Bearer " + token);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
	}

	@Test
	void currentUserWithAValidTokenReturnsTheAuthenticatedAccountOnly() {
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = accessTokenService.issue(user.getId(), Role.USER).token();

		ResponseEntity<String> response = getMeWithToken("Bearer " + token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"id\":\"" + user.getId() + "\"");
		assertThat(response.getBody()).contains("\"email\":\"" + user.getEmail() + "\"");
		assertThat(response.getBody().toLowerCase(Locale.ROOT)).doesNotContain("passwordhash", "\"hash\"");
	}

	@Test
	void aRoleChangeAfterTokenIssuanceTakesEffectOnTheNextRequestNotTheStaleClaim() {
		// Issue a token while the account is a USER — its JWT `role` claim is
		// permanently "USER". Promote the account to ADMIN afterward and
		// confirm the SAME (already-issued, unchanged) token can now create a
		// category — proving the current database role is what authorizes
		// the request, not the token's own stale claim. See ADR-009.
		User user = persistUser(Role.USER, AccountStatus.ACTIVE);
		String token = accessTokenService.issue(user.getId(), Role.USER).token();

		userRepository.saveAndFlush(TestUserFactory.withChangedRoleAndStatus(user, Role.ADMIN, AccountStatus.ACTIVE));

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/categories", new HttpEntity<>(categoryRequest(), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	// ---- Helpers ----

	private void assertCategoryCreationStatus(Role role, HttpStatus expected) {
		HttpHeaders headers = authHeaders(role);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/categories", new HttpEntity<>(categoryRequest(), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(expected);
	}

	private void assertResourceCreationStatus(Role role, HttpStatus expected) {
		Category category = activeCategory();
		HttpHeaders headers = authHeaders(role);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/resources", new HttpEntity<>(resourceRequest(category.getId()), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(expected);
	}

	private void assertOperatingHoursReplacementStatus(Role role, HttpStatus expected) {
		Category category = activeCategory();
		UUID resourceId = createResource(category.getId());
		HttpHeaders headers = authHeaders(role);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resourceId + "/operating-hours", HttpMethod.PUT,
				new HttpEntity<>(operatingHoursRequest(), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(expected);
	}

	private void assertLocationReplacementStatus(Role role, HttpStatus expected) {
		Category category = activeCategory();
		UUID resourceId = createResource(category.getId());
		HttpHeaders headers = authHeaders(role);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/resources/" + resourceId + "/location", HttpMethod.PUT,
				new HttpEntity<>(locationRequest(), headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(expected);
	}

	private static String locationRequest() {
		return "{\"latitude\":44.6488,\"longitude\":-63.5752}";
	}

	private UUID createResource(Long categoryId) {
		HttpHeaders headers = authHeaders(Role.ADMIN);
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response = restTemplate.postForEntity(
				"/api/v1/resources", new HttpEntity<>(resourceRequest(categoryId), headers), String.class);
		String body = response.getBody();
		String id = body.substring(body.indexOf("\"id\":\"") + 6);
		return UUID.fromString(id.substring(0, id.indexOf('"')));
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static String operatingHoursRequest() {
		return "{\"hours\":[{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":\"09:00\",\"closesAt\":\"17:00\"}]}";
	}

	private ResponseEntity<String> getMeWithToken(String authorizationHeaderValue) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
		return restTemplate.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private HttpHeaders authHeaders(Role role) {
		User user = persistUser(role, AccountStatus.ACTIVE);
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessTokenService.issue(user.getId(), role).token());
		return headers;
	}

	private User persistUser(Role role, AccountStatus status) {
		return userRepository.saveAndFlush(TestUserFactory.withRoleAndStatus(role, status));
	}

	private Category activeCategory() {
		String marker = UUID.randomUUID().toString();
		String name = "Authz Matrix Category " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "authz-cat-" + marker, null));
	}

	private static CategoryCreateRequest categoryRequest() {
		return new CategoryCreateRequest("Authz Matrix " + UUID.randomUUID(), "Created by the authorization matrix test.");
	}

	private static ResourceCreateRequest resourceRequest(Long categoryId) {
		return new ResourceCreateRequest(categoryId, "Authz Matrix Resource " + UUID.randomUUID(),
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, null, null, null, null, null);
	}

	private static String tokenWithWrongSignature(UUID userId, Role role) {
		SecretKey wrongKey = Keys.hmacShaKeyFor(
				"a-completely-different-signing-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
		return signedToken(userId, role, ISSUER, Instant.now(), Instant.now().plusSeconds(900), wrongKey);
	}

	private static String expiredToken(UUID userId, Role role) {
		SecretKey key = Keys.hmacShaKeyFor(TEST_SIGNING_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant expiredAt = Instant.now().minusSeconds(60);
		return signedToken(userId, role, ISSUER, expiredAt.minusSeconds(900), expiredAt, key);
	}

	private static String tokenWithWrongIssuer(UUID userId, Role role) {
		SecretKey key = Keys.hmacShaKeyFor(TEST_SIGNING_SECRET.getBytes(StandardCharsets.UTF_8));
		return signedToken(userId, role, "not-the-real-issuer", Instant.now(), Instant.now().plusSeconds(900), key);
	}

	private static String signedToken(
			UUID userId, Role role, String issuer, Instant issuedAt, Instant expiresAt, SecretKey key) {
		return Jwts.builder()
				.subject(userId.toString())
				.claim("role", role.name())
				.issuer(issuer)
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiresAt))
				.id(UUID.randomUUID().toString())
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

}
