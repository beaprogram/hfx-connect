package com.hfxconnect.resourcesubmission;

import com.hfxconnect.resource.CostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for proposing a new resource. Deliberately has no field for
 * {@code status}, {@code submittedByUserId}, {@code active}, {@code
 * verificationStatus}, or a coordinate — every one of those is either
 * server-assigned ({@code status} always starts {@code PENDING_REVIEW},
 * {@code submittedByUserId} always comes from the authenticated principal)
 * or simply doesn't exist as a concept for something that isn't a real
 * resource yet (location, hours, and verification are all Milestone
 * 9/publication concerns).
 */
@Schema(description = "Fields describing a proposed new community resource. Submitting this does not create a public resource — it creates a pending item visible only to you, awaiting review.")
public record ResourceSubmissionCreateRequest(

		@Schema(description = "Must reference an existing, active category.", example = "1")
		@NotNull(message = "Category is required.")
		Long categoryId,

		@Schema(example = "Halifax Central Library")
		@NotBlank(message = "Resource name is required.")
		@Size(max = ResourceSubmissionValidation.NAME_MAX_LENGTH, message = "Resource name must be at most " + ResourceSubmissionValidation.NAME_MAX_LENGTH + " characters.")
		String name,

		@Schema(description = "A one-line summary shown in list views.", example = "Public library with free WiFi and study rooms.")
		@NotBlank(message = "Short description is required.")
		@Size(max = ResourceSubmissionValidation.SHORT_DESCRIPTION_MAX_LENGTH, message = "Short description must be at most " + ResourceSubmissionValidation.SHORT_DESCRIPTION_MAX_LENGTH + " characters.")
		String shortDescription,

		@Schema(description = "Optional longer description.", example = "A full-service public library with study rooms and community programs.")
		@Size(max = ResourceSubmissionValidation.FULL_DESCRIPTION_MAX_LENGTH, message = "Full description must be at most " + ResourceSubmissionValidation.FULL_DESCRIPTION_MAX_LENGTH + " characters.")
		String fullDescription,

		@Schema(example = "5381 Spring Garden Rd")
		@NotBlank(message = "Address line 1 is required.")
		String addressLine1,

		@Schema(example = "Suite 200")
		String addressLine2,

		@Schema(example = "Halifax")
		@NotBlank(message = "City is required.")
		String city,

		@Schema(description = "Two-letter Canadian province/territory code.", example = "NS")
		@NotBlank(message = "Province is required.")
		String province,

		@Schema(description = "Canadian postal code.", example = "B3J 2K9")
		@NotBlank(message = "Postal code is required.")
		String postalCode,

		@Schema(example = "(902) 555-0100")
		String phone,

		@Schema(example = "info@example.org")
		String email,

		@Schema(example = "https://example.org")
		String websiteUrl,

		@Schema(description = "One of FREE, LOW_COST, PAID, UNKNOWN. Defaults to UNKNOWN when omitted.")
		CostType costType,

		@Schema(example = "Open to the public; no membership required.")
		@Size(max = ResourceSubmissionValidation.ELIGIBILITY_MAX_LENGTH, message = "Eligibility information must be at most " + ResourceSubmissionValidation.ELIGIBILITY_MAX_LENGTH + " characters.")
		String eligibilityInformation,

		@Schema(example = "Wheelchair-accessible entrance and elevator.")
		@Size(max = ResourceSubmissionValidation.ACCESSIBILITY_MAX_LENGTH, message = "Accessibility information must be at most " + ResourceSubmissionValidation.ACCESSIBILITY_MAX_LENGTH + " characters.")
		String accessibilityInformation) {
}
