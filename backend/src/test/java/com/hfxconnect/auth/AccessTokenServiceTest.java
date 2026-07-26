package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.user.Role;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests of {@link AccessTokenService} — no Spring context, no
 * database. Constructs the service directly with test-only secrets, the
 * same way {@code CategoryServiceTest} et al. construct their service under
 * test directly rather than booting a context for pure logic.
 */
class AccessTokenServiceTest {

	private static final String VALID_SECRET = "a".repeat(32);
	private static final String OTHER_SECRET = "b".repeat(32);
	private static final String ISSUER = "hfx-connect-test";

	private final AccessTokenService service = new AccessTokenService(VALID_SECRET, ISSUER, 900);

	@Test
	void issuedTokenHasTheConfiguredLifetime() {
		AccessTokenService.IssuedAccessToken token = service.issue(UUID.randomUUID(), Role.USER);

		assertThat(token.expiresInSeconds()).isEqualTo(900);
		assertThat(token.token()).isNotBlank();
	}

	@Test
	void aValidTokenSignatureValidatesAndReturnsTheOriginalClaims() {
		UUID userId = UUID.randomUUID();
		AccessTokenService.IssuedAccessToken token = service.issue(userId, Role.ORGANIZATION);

		AccessTokenService.AccessTokenClaims claims = service.validate(token.token());

		assertThat(claims.userId()).isEqualTo(userId);
		assertThat(claims.role()).isEqualTo(Role.ORGANIZATION);
	}

	@Test
	void aTokenSignedWithADifferentKeyIsRejected() {
		AccessTokenService otherService = new AccessTokenService(OTHER_SECRET, ISSUER, 900);
		String tokenSignedByOther = otherService.issue(UUID.randomUUID(), Role.USER).token();

		assertThatThrownBy(() -> service.validate(tokenSignedByOther)).isInstanceOf(JwtException.class);
	}

	@Test
	void anExpiredTokenIsRejected() {
		AccessTokenService alreadyExpiredService = new AccessTokenService(VALID_SECRET, ISSUER, -60);
		String expiredToken = alreadyExpiredService.issue(UUID.randomUUID(), Role.USER).token();

		assertThatThrownBy(() -> service.validate(expiredToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void aTokenWithAnUnexpectedIssuerIsRejected() {
		AccessTokenService wrongIssuerService = new AccessTokenService(VALID_SECRET, "some-other-issuer", 900);
		String token = wrongIssuerService.issue(UUID.randomUUID(), Role.USER).token();

		assertThatThrownBy(() -> service.validate(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void aMalformedTokenIsRejected() {
		assertThatThrownBy(() -> service.validate("not.a.jwt")).isInstanceOf(JwtException.class);
	}

	@Test
	void requiredClaimsArePresentInTheDecodedPayload() {
		UUID userId = UUID.randomUUID();
		String token = service.issue(userId, Role.MODERATOR).token();

		String payloadJson = decodePayload(token);

		assertThat(payloadJson).contains("\"sub\":\"" + userId + "\"");
		assertThat(payloadJson).contains("\"role\":\"MODERATOR\"");
		assertThat(payloadJson).contains("\"iss\":\"" + ISSUER + "\"");
		assertThat(payloadJson).contains("\"iat\"");
		assertThat(payloadJson).contains("\"exp\"");
		assertThat(payloadJson).contains("\"jti\"");
	}

	@Test
	void noSensitiveClaimsAppearInTheDecodedPayload() {
		String token = service.issue(UUID.randomUUID(), Role.USER).token();

		String payloadJson = decodePayload(token).toLowerCase();

		assertThat(payloadJson).doesNotContain("password", "passwordhash", "email", "refreshtoken", "secret");
	}

	@Test
	void aSigningSecretShorterThan256BitsFailsAtConstructionTime() {
		// Keys.hmacShaKeyFor's own validation — see AccessTokenService's
		// class Javadoc for why this project doesn't duplicate this check.
		assertThatThrownBy(() -> new AccessTokenService("too-short", ISSUER, 900))
				.isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class)
				.hasMessageContaining("256");
	}

	@Test
	void aSigningSecretOfAtLeast256BitsConstructsSuccessfully() {
		assertThat(new AccessTokenService(VALID_SECRET, ISSUER, 900)).isNotNull();
	}

	private static String decodePayload(String token) {
		String[] parts = token.split("\\.");
		return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
	}

}
