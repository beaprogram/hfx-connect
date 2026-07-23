package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HTTP request body for creating a resource. Bean Validation here catches
 * the simplest, order-independent violations (missing/oversized fields)
 * declaratively; format rules that depend on normalization order (province,
 * postal code, phone, email, website scheme) are validated by
 * {@link ResourceValidation} in the service layer instead — see
 * {@code docs/architecture/backend-architecture.md}.
 */
@Schema(description = "Fields required to create a resource. The slug is derived from the name and cannot be supplied directly. New resources always start UNVERIFIED — verification is a moderator-controlled workflow introduced in Milestone 9, not something a caller can request.")
public record ResourceCreateRequest(

		@Schema(description = "Must reference an existing, active category.", example = "1")
		@NotNull(message = "Category is required.")
		Long categoryId,

		@Schema(example = "Halifax Central Library")
		@NotBlank(message = "Resource name is required.")
		@Size(max = ResourceValidation.NAME_MAX_LENGTH, message = "Resource name must be at most " + ResourceValidation.NAME_MAX_LENGTH + " characters.")
		String name,

		@Schema(example = "A full-service public library with free WiFi, study rooms, and community programs.")
		@NotBlank(message = "Description is required.")
		@Size(max = ResourceValidation.DESCRIPTION_MAX_LENGTH, message = "Description must be at most " + ResourceValidation.DESCRIPTION_MAX_LENGTH + " characters.")
		String description,

		@Schema(example = "5381 Spring Garden Rd")
		@NotBlank(message = "Address line 1 is required.")
		@Size(max = ResourceValidation.ADDRESS_LINE_MAX_LENGTH, message = "Address line 1 must be at most " + ResourceValidation.ADDRESS_LINE_MAX_LENGTH + " characters.")
		String addressLine1,

		@Schema(example = "Suite 200")
		@Size(max = ResourceValidation.ADDRESS_LINE_MAX_LENGTH, message = "Address line 2 must be at most " + ResourceValidation.ADDRESS_LINE_MAX_LENGTH + " characters.")
		String addressLine2,

		@Schema(example = "Halifax")
		@NotBlank(message = "City is required.")
		@Size(max = ResourceValidation.CITY_MAX_LENGTH, message = "City must be at most " + ResourceValidation.CITY_MAX_LENGTH + " characters.")
		String city,

		@Schema(description = "Two-letter Canadian province/territory code.", example = "NS")
		@NotBlank(message = "Province is required.")
		String province,

		@Schema(description = "Canadian postal code.", example = "B3J 2K9")
		@NotBlank(message = "Postal code is required.")
		String postalCode,

		@Schema(example = "(902) 555-0100")
		@Size(max = ResourceValidation.PHONE_MAX_LENGTH, message = "Phone must be at most " + ResourceValidation.PHONE_MAX_LENGTH + " characters.")
		String phone,

		@Schema(example = "info@example.org")
		@Size(max = ResourceValidation.EMAIL_MAX_LENGTH, message = "Email must be at most " + ResourceValidation.EMAIL_MAX_LENGTH + " characters.")
		String email,

		@Schema(example = "https://example.org")
		@Size(max = ResourceValidation.WEBSITE_URL_MAX_LENGTH, message = "Website URL must be at most " + ResourceValidation.WEBSITE_URL_MAX_LENGTH + " characters.")
		String websiteUrl,

		@Schema(description = "One of FREE, LOW_COST, PAID, UNKNOWN. Defaults to UNKNOWN when omitted.")
		CostType costType,

		@Schema(example = "Free with a library card; day passes available for visitors.")
		@Size(max = ResourceValidation.COST_DETAILS_MAX_LENGTH, message = "Cost details must be at most " + ResourceValidation.COST_DETAILS_MAX_LENGTH + " characters.")
		String costDetails,

		@Schema(example = "Open to the public; no membership required.")
		@Size(max = ResourceValidation.ELIGIBILITY_MAX_LENGTH, message = "Eligibility must be at most " + ResourceValidation.ELIGIBILITY_MAX_LENGTH + " characters.")
		String eligibility) {

	CreateResourceCommand toCommand() {
		return new CreateResourceCommand(categoryId, name, description, addressLine1, addressLine2, city, province,
				postalCode, phone, email, websiteUrl, costType, costDetails, eligibility);
	}

}
