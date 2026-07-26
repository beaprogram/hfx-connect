package com.hfxconnect.auth;

import com.hfxconnect.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates this project's own short-lived signed access tokens.
 * See ADR-008 for the full design rationale (why JJWT, why HS256, why these
 * specific claims and no others, why the signing secret is externalized
 * rather than generated at startup).
 *
 * <p><strong>Signing-secret strength is validated at startup by
 * {@link Keys#hmacShaKeyFor}</strong>, not by a custom check here — it throws
 * {@link WeakKeyException} unconditionally for any key under 256 bits (32
 * bytes), which is the exact same minimum this project would otherwise have
 * had to enforce by hand. Discovered empirically while writing this class's
 * own tests (a hand-rolled {@code @PostConstruct} length check turned out to
 * be genuinely unreachable dead code, since construction already fails
 * first) — a good example of verifying a library's actual behavior instead
 * of assuming a manual check is needed on top of it.
 *
 * <p>Nothing in Milestone 5B actually calls {@link #validate}: no endpoint in
 * this milestone requires a valid access token to reach it (protected routes
 * are Milestone 5C). It is implemented and tested now anyway because a
 * correct, tested access-token format is this milestone's deliverable —
 * Milestone 5C's authorization work is what will call it.
 */
@Service
public class AccessTokenService {

	private final String issuer;
	private final long accessTokenTtlSeconds;
	private final SecretKey signingKey;

	public AccessTokenService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.issuer}") String issuer,
			@Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
		this.issuer = issuer;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
		// Throws WeakKeyException (a JwtException) immediately for any
		// secret under 256 bits (32 bytes) — see class Javadoc.
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public record IssuedAccessToken(String token, long expiresInSeconds) {
	}

	public record AccessTokenClaims(UUID userId, Role role) {
	}

	/**
	 * Claims are deliberately minimal: {@code sub} (user id), {@code role}
	 * (Milestone 5C's near-term consumer), {@code iss}/{@code iat}/
	 * {@code exp}, and a random {@code jti} for log correlation only — no
	 * email, no password/hash, no refresh-token material. See ADR-008.
	 */
	public IssuedAccessToken issue(UUID userId, Role role) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(Duration.ofSeconds(accessTokenTtlSeconds));

		String token = Jwts.builder()
				.subject(userId.toString())
				.claim("role", role.name())
				.issuer(issuer)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.id(UUID.randomUUID().toString())
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();

		return new IssuedAccessToken(token, accessTokenTtlSeconds);
	}

	/**
	 * Verifies the signature (rejecting any token not signed with this exact
	 * key — including an unsigned {@code alg: none} token, which
	 * {@code parseSignedClaims} never accepts regardless), the expiration,
	 * and the issuer. Throws {@link JwtException} (JJWT's own hierarchy —
	 * {@code ExpiredJwtException}, {@code SignatureException},
	 * {@code MalformedJwtException}, {@code IncorrectClaimException}, ...)
	 * on any failure; this milestone has no caller that needs to distinguish
	 * them, so none are translated into {@code ApiException} subclasses yet.
	 */
	public AccessTokenClaims validate(String token) {
		Claims claims = Jwts.parser()
				.requireIssuer(issuer)
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		UUID userId = UUID.fromString(claims.getSubject());
		Role role = Role.valueOf(claims.get("role", String.class));
		return new AccessTokenClaims(userId, role);
	}

}
