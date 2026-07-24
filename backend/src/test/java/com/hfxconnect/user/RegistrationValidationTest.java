package com.hfxconnect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.common.error.ValidationException;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link RegistrationValidation}'s password-length
 * policy, specifically the UTF-8-byte-vs-Java-char distinction BCrypt's
 * 72-byte input limit requires. See {@link RegistrationValidation#PASSWORD_MAX_BYTES}'s
 * Javadoc and ADR-007 for why a Java {@code char} count is the wrong thing to
 * compare against BCrypt's limit.
 */
class RegistrationValidationTest {

	private static final String EMAIL = "byte-limit-check@example.org";

	@Test
	void aSeventyTwoByteAsciiPasswordIsAccepted() {
		String password = "a1" + "b".repeat(70);
		assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

		RegistrationValidation.Normalized result = RegistrationValidation.validate(EMAIL, password);

		assertThat(result.password()).isEqualTo(password);
	}

	@Test
	void aSeventyThreeByteAsciiPasswordIsRejected() {
		String password = "a1" + "b".repeat(71);
		assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);

		assertValidationError(() -> RegistrationValidation.validate(EMAIL, password), "password");
	}

	@Test
	void aValidMultibyteUnicodePasswordBelowTheByteLimitIsAccepted() {
		// 20 repetitions of a 3-byte-in-UTF-8 CJK character = 60 bytes, 20 chars.
		String password = "通".repeat(20) + "aB3!";
		assertThat(password.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(72);

		RegistrationValidation.Normalized result = RegistrationValidation.validate(EMAIL, password);

		assertThat(result.password()).isEqualTo(password);
	}

	@Test
	void aPasswordWithNoMoreThanSeventyTwoCharactersButMoreThanSeventyTwoBytesIsRejected() {
		// 72 repetitions of 'é' (2 bytes each in UTF-8) = 72 chars, 144 bytes —
		// exactly the case a naive String.length() check would wrongly accept.
		String password = "é".repeat(72);
		assertThat(password.length()).isEqualTo(72);
		assertThat(password.getBytes(StandardCharsets.UTF_8).length).isEqualTo(144);

		assertValidationError(() -> RegistrationValidation.validate(EMAIL, password), "password");
	}

	@Test
	void theOversizedPasswordErrorMessageExplainsTheByteLimitWithoutInternalDetails() {
		String password = "é".repeat(72);

		assertThatThrownBy(() -> RegistrationValidation.validate(EMAIL, password))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> {
					String message = ((ValidationException) ex).getFieldErrors().get("password");
					assertThat(message).contains("72").contains("bytes").contains("UTF-8");
					assertThat(message.toLowerCase()).doesNotContain(
							"bcrypt", "exception", "stacktrace", "com.hfxconnect", "illegalargument");
				});
	}

	private static void assertValidationError(ThrowingCallable callable, String field) {
		assertThatThrownBy(callable)
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors()).containsKey(field));
	}

}
