package com.hfxconnect.resourcesubmission;

import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.common.text.SlugGenerator;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceValidation;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure normalization and format validation for a proposed resource
 * submission, independent of persistence or category lookup (which
 * {@link ResourceSubmissionService} handles). Reuses
 * {@link ResourceValidation}'s field-level helpers directly for every rule a
 * submission shares with real resource creation (province, postal code,
 * phone, email, website scheme, bounded-length trimming) rather than
 * duplicating them — see {@code ResourceValidation}'s own Javadoc for why
 * those helpers were made reusable in Milestone 8B.
 */
final class ResourceSubmissionValidation {

	static final int NAME_MAX_LENGTH = ResourceValidation.NAME_MAX_LENGTH;
	static final int SHORT_DESCRIPTION_MAX_LENGTH = 300;
	static final int FULL_DESCRIPTION_MAX_LENGTH = ResourceValidation.DESCRIPTION_MAX_LENGTH;
	static final int ELIGIBILITY_MAX_LENGTH = ResourceValidation.ELIGIBILITY_MAX_LENGTH;
	static final int ACCESSIBILITY_MAX_LENGTH = 1000;

	private ResourceSubmissionValidation() {
	}

	record Normalized(
			String name,
			String normalizedName,
			String shortDescription,
			String fullDescription,
			String addressLine1,
			String addressLine2,
			String city,
			String province,
			String postalCode,
			String phone,
			String email,
			String websiteUrl,
			String eligibilityInformation,
			String accessibilityInformation) {
	}

	static Normalized validate(
			String name, String shortDescription, String fullDescription, String addressLine1, String addressLine2,
			String city, String province, String postalCode, String phone, String email, String websiteUrl,
			String eligibilityInformation, String accessibilityInformation) {

		Map<String, String> errors = new LinkedHashMap<>();

		String normalizedName = ResourceValidation.requireBounded(errors, "name", name, NAME_MAX_LENGTH, "Resource name");
		if (normalizedName != null && SlugGenerator.generate(normalizedName).isEmpty()) {
			errors.put("name", "Resource name must contain at least one letter or number.");
		}

		String normalizedShortDescription = ResourceValidation.requireBounded(
				errors, "shortDescription", shortDescription, SHORT_DESCRIPTION_MAX_LENGTH, "Short description");
		String normalizedFullDescription = ResourceValidation.optionalBounded(
				errors, "fullDescription", fullDescription, FULL_DESCRIPTION_MAX_LENGTH, "Full description");
		String normalizedAddressLine1 = ResourceValidation.requireBounded(
				errors, "addressLine1", addressLine1, ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Address line 1");
		String normalizedAddressLine2 = ResourceValidation.optionalBounded(
				errors, "addressLine2", addressLine2, ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Address line 2");
		String normalizedCity = ResourceValidation.requireBounded(errors, "city", city, ResourceValidation.CITY_MAX_LENGTH, "City");
		String normalizedProvince = ResourceValidation.validateProvince(errors, province);
		String normalizedPostalCode = ResourceValidation.validatePostalCode(errors, postalCode);
		String normalizedPhone = ResourceValidation.validatePhone(errors, phone);
		String normalizedEmail = ResourceValidation.validateEmail(errors, email);
		String normalizedWebsiteUrl = ResourceValidation.validateWebsiteUrl(errors, websiteUrl);
		String normalizedEligibility = ResourceValidation.optionalBounded(
				errors, "eligibilityInformation", eligibilityInformation, ELIGIBILITY_MAX_LENGTH, "Eligibility information");
		String normalizedAccessibility = ResourceValidation.optionalBounded(
				errors, "accessibilityInformation", accessibilityInformation, ACCESSIBILITY_MAX_LENGTH, "Accessibility information");

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted resource proposal contains invalid information.", errors);
		}

		String normalizedForDuplicateCheck = normalizedName.toLowerCase(Locale.ROOT);

		return new Normalized(normalizedName, normalizedForDuplicateCheck, normalizedShortDescription,
				normalizedFullDescription, normalizedAddressLine1, normalizedAddressLine2, normalizedCity,
				normalizedProvince, normalizedPostalCode, normalizedPhone, normalizedEmail, normalizedWebsiteUrl,
				normalizedEligibility, normalizedAccessibility);
	}

	static CostType resolveCostType(CostType costType) {
		return costType == null ? CostType.UNKNOWN : costType;
	}

}
