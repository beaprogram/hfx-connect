package com.hfxconnect.resource;

import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.common.text.SlugGenerator;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure normalization and format validation for resource fields, independent
 * of persistence or category rules (which {@link ResourceService} handles,
 * since they need the database). Every check that can be expressed without a
 * database round-trip lives here so it's directly unit-testable.
 *
 * <p>Accumulates every violation before throwing (rather than failing on the
 * first one) so callers see the complete picture in one response, matching
 * how {@code MethodArgumentNotValidException} handling works for HTTP
 * request bodies elsewhere in the project.
 */
final class ResourceValidation {

	static final int NAME_MAX_LENGTH = 180;
	static final int DESCRIPTION_MAX_LENGTH = 4000;
	static final int ADDRESS_LINE_MAX_LENGTH = 200;
	static final int CITY_MAX_LENGTH = 100;
	static final int PHONE_MAX_LENGTH = 40;
	static final int EMAIL_MAX_LENGTH = 180;
	static final int WEBSITE_URL_MAX_LENGTH = 500;
	static final int COST_DETAILS_MAX_LENGTH = 500;
	static final int ELIGIBILITY_MAX_LENGTH = 1000;

	private static final Set<String> VALID_PROVINCES = Set.of(
			"AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT");

	/** Canonical "A1A 1A1" form. Canada Post excludes D, F, I, O, Q, and U from the first character. */
	private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("^[ABCEGHJKLMNPRSTVXY][0-9][A-Z] [0-9][A-Z][0-9]$");

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private ResourceValidation() {
	}

	record Normalized(
			String name,
			String slug,
			String description,
			String addressLine1,
			String addressLine2,
			String city,
			String province,
			String postalCode,
			String phone,
			String email,
			String websiteUrl,
			String costDetails,
			String eligibility) {
	}

	static Normalized validate(
			String name, String description, String addressLine1, String addressLine2, String city,
			String province, String postalCode, String phone, String email, String websiteUrl,
			String costDetails, String eligibility) {

		Map<String, String> errors = new LinkedHashMap<>();

		String normalizedName = requireBounded(errors, "name", name, NAME_MAX_LENGTH, "Resource name");
		String slug = null;
		if (normalizedName != null) {
			slug = SlugGenerator.generate(normalizedName).orElse(null);
			if (slug == null) {
				errors.put("name", "Resource name must contain at least one letter or number.");
			}
		}

		String normalizedDescription = requireBounded(errors, "description", description, DESCRIPTION_MAX_LENGTH, "Description");
		String normalizedAddressLine1 = requireBounded(errors, "addressLine1", addressLine1, ADDRESS_LINE_MAX_LENGTH, "Address line 1");
		String normalizedAddressLine2 = optionalBounded(errors, "addressLine2", addressLine2, ADDRESS_LINE_MAX_LENGTH, "Address line 2");
		String normalizedCity = requireBounded(errors, "city", city, CITY_MAX_LENGTH, "City");
		String normalizedProvince = validateProvince(errors, province);
		String normalizedPostalCode = validatePostalCode(errors, postalCode);
		String normalizedPhone = validatePhone(errors, phone);
		String normalizedEmail = validateEmail(errors, email);
		String normalizedWebsiteUrl = validateWebsiteUrl(errors, websiteUrl);
		String normalizedCostDetails = optionalBounded(errors, "costDetails", costDetails, COST_DETAILS_MAX_LENGTH, "Cost details");
		String normalizedEligibility = optionalBounded(errors, "eligibility", eligibility, ELIGIBILITY_MAX_LENGTH, "Eligibility");

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted resource contains invalid information.", errors);
		}

		return new Normalized(normalizedName, slug, normalizedDescription, normalizedAddressLine1,
				normalizedAddressLine2, normalizedCity, normalizedProvince, normalizedPostalCode, normalizedPhone,
				normalizedEmail, normalizedWebsiteUrl, normalizedCostDetails, normalizedEligibility);
	}

	private static String requireBounded(Map<String, String> errors, String field, String value, int maxLength, String label) {
		String normalized = value == null ? "" : collapseWhitespace(value);
		if (normalized.isEmpty()) {
			errors.put(field, label + " is required.");
			return null;
		}
		if (normalized.length() > maxLength) {
			errors.put(field, label + " must be at most " + maxLength + " characters.");
			return null;
		}
		return normalized;
	}

	private static String optionalBounded(Map<String, String> errors, String field, String value, int maxLength, String label) {
		if (value == null) {
			return null;
		}
		String normalized = collapseWhitespace(value);
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > maxLength) {
			errors.put(field, label + " must be at most " + maxLength + " characters.");
			return null;
		}
		return normalized;
	}

	private static String validateProvince(Map<String, String> errors, String province) {
		if (province == null || province.isBlank()) {
			errors.put("province", "Province is required.");
			return null;
		}
		String normalized = province.trim().toUpperCase(Locale.ROOT);
		if (!VALID_PROVINCES.contains(normalized)) {
			errors.put("province", "Province must be a valid two-letter Canadian province or territory code.");
			return null;
		}
		return normalized;
	}

	private static String validatePostalCode(Map<String, String> errors, String postalCode) {
		if (postalCode == null || postalCode.isBlank()) {
			errors.put("postalCode", "Postal code is required.");
			return null;
		}
		String stripped = postalCode.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
		String normalized = stripped.length() == 6
				? stripped.substring(0, 3) + " " + stripped.substring(3)
				: stripped;
		if (!POSTAL_CODE_PATTERN.matcher(normalized).matches()) {
			errors.put("postalCode", "Postal code must be a valid Canadian postal code (e.g. 'B3H 4R2').");
			return null;
		}
		return normalized;
	}

	private static String validatePhone(Map<String, String> errors, String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		String normalized = collapseWhitespace(phone);
		if (normalized.length() > PHONE_MAX_LENGTH) {
			errors.put("phone", "Phone must be at most " + PHONE_MAX_LENGTH + " characters.");
			return null;
		}
		long digitCount = normalized.chars().filter(Character::isDigit).count();
		if (digitCount < 7 || digitCount > 15) {
			errors.put("phone", "Phone must contain between 7 and 15 digits.");
			return null;
		}
		return normalized;
	}

	private static String validateEmail(Map<String, String> errors, String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() > EMAIL_MAX_LENGTH) {
			errors.put("email", "Email must be at most " + EMAIL_MAX_LENGTH + " characters.");
			return null;
		}
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			errors.put("email", "Email must be a valid email address.");
			return null;
		}
		return normalized;
	}

	private static String validateWebsiteUrl(Map<String, String> errors, String websiteUrl) {
		if (websiteUrl == null || websiteUrl.isBlank()) {
			return null;
		}
		String normalized = websiteUrl.trim();
		if (normalized.length() > WEBSITE_URL_MAX_LENGTH) {
			errors.put("websiteUrl", "Website URL must be at most " + WEBSITE_URL_MAX_LENGTH + " characters.");
			return null;
		}
		URI uri;
		try {
			uri = new URI(normalized);
		} catch (URISyntaxException e) {
			errors.put("websiteUrl", "Website URL is not a valid URL.");
			return null;
		}
		String scheme = uri.getScheme();
		if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
			errors.put("websiteUrl", "Website URL must use the http or https scheme.");
			return null;
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			errors.put("websiteUrl", "Website URL must include a host.");
			return null;
		}
		return normalized;
	}

	private static String collapseWhitespace(String input) {
		return input.trim().replaceAll("\\s+", " ");
	}

}
