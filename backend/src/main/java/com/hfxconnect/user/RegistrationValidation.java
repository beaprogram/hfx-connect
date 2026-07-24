package com.hfxconnect.user;

import com.hfxconnect.common.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure normalization and format validation for registration input,
 * independent of persistence (which {@link RegistrationService} handles,
 * since duplicate-email detection needs the database). Mirrors
 * {@code com.hfxconnect.resource.ResourceValidation}'s shape: every check
 * expressible without a database round-trip lives here so it's directly
 * unit-testable, and every violation is accumulated before throwing (rather
 * than failing on the first one) so the client sees the complete picture in
 * one response.
 */
final class RegistrationValidation {

	static final int EMAIL_MAX_LENGTH = 180;
	static final int PASSWORD_MIN_LENGTH = 8;

	/**
	 * BCrypt's underlying algorithm accepts at most 72 <em>bytes</em> of
	 * input, not 72 Java {@code char}s — {@code BCryptPasswordEncoder.encode}
	 * throws {@code IllegalArgumentException} for anything longer (verified
	 * directly against the real encoder, not assumed). A Java {@code char}
	 * count is the wrong thing to compare against this limit: most non-ASCII
	 * characters (accented letters, CJK characters, emoji, ...) encode to
	 * more than one UTF-8 byte each, so a password with 72 {@code char}s can
	 * still exceed 72 bytes. Comparing the actual UTF-8-encoded byte length
	 * against this limit is what actually prevents an oversized password
	 * from ever reaching the encoder and throwing there instead of failing
	 * validation cleanly.
	 */
	static final int PASSWORD_MAX_BYTES = 72;

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	/**
	 * A small, fixed denylist of the most commonly leaked/guessed passwords
	 * (per widely published breach-corpus frequency lists), checked
	 * case-insensitively. Deliberately not a large external wordlist or
	 * dedicated password-strength library — this milestone's brief calls for
	 * rejecting "commonly unsafe input where practical", not a full
	 * password-security framework.
	 */
	private static final Set<String> COMMON_PASSWORDS = Set.of(
			"password", "password1", "12345678", "123456789", "qwerty123",
			"letmein1", "iloveyou", "admin1234", "welcome1", "monkey123",
			"football1", "abc123456", "passw0rd", "qwertyuiop", "dragon123",
			"sunshine1", "princess1", "baseball1", "trustno1", "superman1");

	private RegistrationValidation() {
	}

	record Normalized(String email, String normalizedEmail, String password) {
	}

	static Normalized validate(String email, String password) {
		Map<String, String> errors = new LinkedHashMap<>();

		String normalizedEmail = validateEmail(errors, email);
		validatePassword(errors, password, normalizedEmail);

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted registration contains invalid information.", errors);
		}

		String trimmedEmail = email.trim();
		return new Normalized(trimmedEmail, normalizedEmail, password);
	}

	private static String validateEmail(Map<String, String> errors, String email) {
		if (email == null || email.isBlank()) {
			errors.put("email", "Email is required.");
			return null;
		}
		String trimmed = email.trim();
		if (trimmed.length() > EMAIL_MAX_LENGTH) {
			errors.put("email", "Email must be at most " + EMAIL_MAX_LENGTH + " characters.");
			return null;
		}
		String normalized = trimmed.toLowerCase(Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			errors.put("email", "Email must be a valid email address.");
			return null;
		}
		return normalized;
	}

	private static void validatePassword(Map<String, String> errors, String password, String normalizedEmail) {
		if (password == null || password.isEmpty()) {
			errors.put("password", "Password is required.");
			return;
		}
		if (password.length() < PASSWORD_MIN_LENGTH) {
			errors.put("password", "Password must be at least " + PASSWORD_MIN_LENGTH + " characters.");
			return;
		}
		int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
		if (byteLength > PASSWORD_MAX_BYTES) {
			errors.put("password", "Password must be at most " + PASSWORD_MAX_BYTES
					+ " bytes when encoded as UTF-8 (some characters, such as accented letters, "
					+ "symbols, or emoji, use more than one byte each).");
			return;
		}
		if (password.isBlank()) {
			errors.put("password", "Password must not consist only of whitespace.");
			return;
		}
		if (COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
			errors.put("password", "This password is too common. Please choose a different one.");
			return;
		}
		String localPart = normalizedEmail != null && normalizedEmail.contains("@")
				? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
				: null;
		if (localPart != null && localPart.length() >= 3
				&& password.toLowerCase(Locale.ROOT).contains(localPart)) {
			errors.put("password", "Password must not contain your email address.");
		}
	}

}
