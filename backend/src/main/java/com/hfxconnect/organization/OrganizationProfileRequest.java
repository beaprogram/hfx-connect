package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The request body for both creating and updating an organization profile
 * (Milestone 10A) — one shape for both, since the editable field set is
 * identical. There is deliberately no field for {@code ownerUserId},
 * {@code verificationStatus}, {@code verifiedBy}, or {@code verifiedAt} —
 * all four are server-assigned; see {@code OrganizationService}.
 */
@Schema(description = "An organization profile's owner-editable fields. Every field except name is optional.")
public record OrganizationProfileRequest(
		@NotBlank(message = "Organization name is required.") @Schema(example = "Halifax Newcomer Services") String name,
		@Schema(example = "Settlement support and language classes for newcomers to Halifax.") String description,
		@Schema(example = "https://example.org") String websiteUrl,
		@Schema(example = "contact@example.org") String publicEmail,
		@Schema(example = "902-555-0123") String phone,
		@Schema(example = "123 Main St") String addressLine1,
		@Schema(example = "Halifax") String city,
		@Schema(example = "NS") String province,
		@Schema(example = "B3H 4R2") String postalCode) {
}
