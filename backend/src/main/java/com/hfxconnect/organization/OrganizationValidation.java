package com.hfxconnect.organization;

import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.common.text.SlugGenerator;
import com.hfxconnect.resource.ResourceValidation;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure normalization and format validation for an organization profile.
 * Reuses {@link ResourceValidation}'s field-level helpers directly for
 * every rule an organization's public contact fields share with a
 * resource's own (province, postal code, phone, email, website scheme) —
 * the same "shared pure utilities extracted on second use" pattern
 * {@code ResourceSubmissionValidation}/{@code CorrectionReportValidation}
 * already established in Milestone 8B, now extended to a third caller.
 */
final class OrganizationValidation {

	static final int NAME_MAX_LENGTH = ResourceValidation.NAME_MAX_LENGTH;
	static final int DESCRIPTION_MAX_LENGTH = 2000;

	private OrganizationValidation() {
	}

	record Normalized(
			String name,
			String normalizedName,
			String description,
			String websiteUrl,
			String publicEmail,
			String phone,
			String addressLine1,
			String city,
			String province,
			String postalCode) {
	}

	/**
	 * Every field except {@code name} is optional — an organization profile
	 * may legitimately have no public address yet (see the blueprint's
	 * "address_line_1, nullable" note), unlike a resource, whose address is
	 * always required.
	 */
	static Normalized validate(String name, String description, String websiteUrl, String publicEmail, String phone,
			String addressLine1, String city, String province, String postalCode) {
		Map<String, String> errors = new LinkedHashMap<>();

		String normalizedName = ResourceValidation.requireBounded(errors, "name", name, NAME_MAX_LENGTH, "Organization name");
		if (normalizedName != null && SlugGenerator.generate(normalizedName).isEmpty()) {
			errors.put("name", "Organization name must contain at least one letter or number.");
		}

		String normalizedDescription = ResourceValidation.optionalBounded(
				errors, "description", description, DESCRIPTION_MAX_LENGTH, "Description");
		String normalizedWebsiteUrl = ResourceValidation.validateWebsiteUrl(errors, websiteUrl);
		String normalizedPublicEmail = ResourceValidation.validateEmail(errors, publicEmail);
		String normalizedPhone = ResourceValidation.validatePhone(errors, phone);
		String normalizedAddressLine1 = ResourceValidation.optionalBounded(
				errors, "addressLine1", addressLine1, ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Address line 1");
		String normalizedCity = ResourceValidation.optionalBounded(
				errors, "city", city, ResourceValidation.CITY_MAX_LENGTH, "City");
		String normalizedProvince = validateOptionalProvince(errors, province);
		String normalizedPostalCode = validateOptionalPostalCode(errors, postalCode);

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted organization profile contains invalid information.", errors);
		}

		String normalizedForSlug = normalizedName == null ? null : normalizedName.toLowerCase(Locale.ROOT);

		return new Normalized(normalizedName, normalizedForSlug, normalizedDescription, normalizedWebsiteUrl,
				normalizedPublicEmail, normalizedPhone, normalizedAddressLine1, normalizedCity, normalizedProvince,
				normalizedPostalCode);
	}

	/**
	 * {@link ResourceValidation#validateProvince} treats blank as a required-field
	 * error (correct for a resource, whose province is always required) —
	 * an organization's province is optional, so blank/absent is valid here
	 * and only a genuinely present-but-invalid code is rejected.
	 */
	private static String validateOptionalProvince(Map<String, String> errors, String province) {
		if (province == null || province.isBlank()) {
			return null;
		}
		return ResourceValidation.validateProvince(errors, province);
	}

	private static String validateOptionalPostalCode(Map<String, String> errors, String postalCode) {
		if (postalCode == null || postalCode.isBlank()) {
			return null;
		}
		return ResourceValidation.validatePostalCode(errors, postalCode);
	}

}
