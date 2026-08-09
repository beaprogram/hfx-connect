package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The public view of a {@link OrganizationVerificationStatus#VERIFIED}
 * organization (Milestone 10A) — {@code OrganizationService.getPublicBySlug}
 * never returns this for a pending/rejected/suspended organization (404
 * instead). Never includes {@code ownerUserId}, {@code verificationReason},
 * or any audit/review metadata.
 */
@Schema(description = "A verified organization's public profile.")
public record PublicOrganizationResponse(
		UUID id,
		String name,
		String slug,
		String description,
		String websiteUrl,
		String publicEmail,
		String phone,
		String addressLine1,
		String city,
		String province,
		String postalCode,
		OrganizationVerificationStatus verificationStatus,
		Instant verifiedAt) {

	static PublicOrganizationResponse from(Organization organization) {
		return new PublicOrganizationResponse(
				organization.getId(),
				organization.getName(),
				organization.getSlug(),
				organization.getDescription(),
				organization.getWebsiteUrl(),
				organization.getPublicEmail(),
				organization.getPhone(),
				organization.getAddressLine1(),
				organization.getCity(),
				organization.getProvince(),
				organization.getPostalCode(),
				organization.getVerificationStatus(),
				organization.getVerifiedAt());
	}

}
