package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.common.error.ValidationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ResourceValidationTest {

	@Test
	void normalizesWhitespaceAcrossFreeTextFields() {
		ResourceValidation.Normalized result = validBaseline();

		assertThat(result.name()).isEqualTo("Food Bank");
		assertThat(result.city()).isEqualTo("Halifax");
	}

	@Test
	void collapsesRepeatedInternalWhitespaceInName() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"  Food   Bank  ", "Description", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, null, null, null, null);

		assertThat(result.name()).isEqualTo("Food Bank");
	}

	@Test
	void generatesADeterministicSlugFromTheName() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Employment & Career Support", "Description", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, null, null, null, null);

		assertThat(result.slug()).isEqualTo("employment-career-support");
	}

	@Test
	void rejectsANameThatProducesAnEmptySlug() {
		assertValidationError(
				() -> ResourceValidation.validate("&&&", "Description", "123 Main St", null, "Halifax", "NS",
						"B3H 4R2", null, null, null, null, null),
				"name");
	}

	@Test
	void rejectsABlankName() {
		assertValidationError(
				() -> ResourceValidation.validate("   ", "Description", "123 Main St", null, "Halifax", "NS",
						"B3H 4R2", null, null, null, null, null),
				"name");
	}

	@Test
	void rejectsANameLongerThanTheDatabaseLimit() {
		String tooLong = "a".repeat(ResourceValidation.NAME_MAX_LENGTH + 1);

		assertValidationError(
				() -> ResourceValidation.validate(tooLong, "Description", "123 Main St", null, "Halifax", "NS",
						"B3H 4R2", null, null, null, null, null),
				"name");
	}

	@Test
	void rejectsADescriptionLongerThanTheDatabaseLimit() {
		String tooLong = "a".repeat(ResourceValidation.DESCRIPTION_MAX_LENGTH + 1);

		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", tooLong, "123 Main St", null, "Halifax", "NS",
						"B3H 4R2", null, null, null, null, null),
				"description");
	}

	@Test
	void normalizesProvinceToUppercase() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "ns", "B3H 4R2",
				null, null, null, null, null);

		assertThat(result.province()).isEqualTo("NS");
	}

	@Test
	void rejectsAProvinceThatIsNotARealCanadianCode() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"ZZ", "B3H 4R2", null, null, null, null, null),
				"province");
	}

	@Test
	void normalizesPostalCodeToUppercaseWithOneSpace() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "NS", "b3h4r2",
				null, null, null, null, null);

		assertThat(result.postalCode()).isEqualTo("B3H 4R2");
	}

	@Test
	void normalizesAnAlreadySpacedPostalCode() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "NS", "  b3h   4r2  ",
				null, null, null, null, null);

		assertThat(result.postalCode()).isEqualTo("B3H 4R2");
	}

	@Test
	void rejectsAMalformedPostalCode() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "12345", null, null, null, null, null),
				"postalCode");
	}

	@Test
	void rejectsAPostalCodeStartingWithAnExcludedLetter() {
		// Canada Post never issues postal codes starting with D, F, I, O, Q, or U.
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "D3H 4R2", null, null, null, null, null),
				"postalCode");
	}

	@Test
	void acceptsAPracticalPhoneNumberFormat() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				"(902) 555-1234", null, null, null, null);

		assertThat(result.phone()).isEqualTo("(902) 555-1234");
	}

	@Test
	void rejectsAPhoneNumberWithTooFewDigits() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "B3H 4R2", "12345", null, null, null, null),
				"phone");
	}

	@Test
	void treatsPhoneAsOptional() {
		ResourceValidation.Normalized result = validBaseline();

		assertThat(result.phone()).isNull();
	}

	@Test
	void normalizesEmailToLowercase() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, "Contact@Example.COM", null, null, null);

		assertThat(result.email()).isEqualTo("contact@example.com");
	}

	@Test
	void rejectsAMalformedEmail() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "B3H 4R2", null, "not-an-email", null, null, null),
				"email");
	}

	@Test
	void acceptsHttpsWebsiteUrls() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, null, "https://example.org/food-bank", null, null);

		assertThat(result.websiteUrl()).isEqualTo("https://example.org/food-bank");
	}

	@Test
	void rejectsJavascriptSchemeWebsiteUrls() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "B3H 4R2", null, null, "javascript:alert(1)", null, null),
				"websiteUrl");
	}

	@Test
	void rejectsDataSchemeWebsiteUrls() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "B3H 4R2", null, null, "data:text/html,<script>alert(1)</script>", null, null),
				"websiteUrl");
	}

	@Test
	void rejectsFileSchemeWebsiteUrls() {
		assertValidationError(
				() -> ResourceValidation.validate("Food Bank", "Description", "123 Main St", null, "Halifax",
						"NS", "B3H 4R2", null, null, "file:///etc/passwd", null, null),
				"websiteUrl");
	}

	@Test
	void treatsOptionalFieldsAsNullWhenBlank() {
		ResourceValidation.Normalized result = ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", "   ", "Halifax", "NS", "B3H 4R2",
				"  ", "", "   ", "  ", "");

		assertThat(result.addressLine2()).isNull();
		assertThat(result.phone()).isNull();
		assertThat(result.email()).isNull();
		assertThat(result.websiteUrl()).isNull();
		assertThat(result.costDetails()).isNull();
		assertThat(result.eligibility()).isNull();
	}

	@Test
	void accumulatesMultipleFieldErrorsInOneException() {
		assertThatThrownBy(() -> ResourceValidation.validate(
				"", "", "123 Main St", null, "Halifax", "ZZ", "invalid",
				null, null, null, null, null))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> {
					var fieldErrors = ((ValidationException) ex).getFieldErrors();
					assertThat(fieldErrors).containsKeys("name", "description", "province", "postalCode");
				});
	}

	private static ResourceValidation.Normalized validBaseline() {
		return ResourceValidation.validate(
				"Food Bank", "Description", "123 Main St", null, "Halifax", "NS", "B3H 4R2",
				null, null, null, null, null);
	}

	private static void assertValidationError(ThrowingCallable callable, String field) {
		assertThatThrownBy(callable)
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors()).containsKey(field));
	}

}
