package com.hfxconnect.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Pure refresh-token generation and hashing, independent of persistence. See
 * ADR-008 for why a 256-bit {@link SecureRandom} value (not a JWT, not
 * database-ID-derived) and SHA-256 (not BCrypt) are the right tools here.
 */
final class RefreshTokenGenerator {

	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private RefreshTokenGenerator() {
	}

	/** 256 bits of cryptographically secure randomness, Base64URL-encoded (no padding), no embedded structure of any kind. */
	static String generateRawToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * SHA-256 hex digest of the raw token — the only form ever persisted.
	 * Deterministic (the same raw token always hashes identically, which is
	 * what makes an exact-match database lookup by hash possible at all),
	 * unlike BCrypt's salted output.
	 */
	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is a mandatory JDK algorithm (JLS/JCA guarantee) — this
			// is unreachable on any conforming JVM.
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

}
