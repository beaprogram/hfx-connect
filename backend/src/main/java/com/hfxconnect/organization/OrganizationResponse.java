package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The current user's own organization profile (Milestone 10A). Never
 * includes the reviewing admin's identity — only the safe
 * {@code verificationReason} text, the same owner-visible-reason-vs-
 * reviewer-identity split Milestone 9A already established.
 */
@Schema(description = "The current user's own organization profile.")
public record OrganizationResponse(
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
		Instant verifiedAt,
		String verificationReason,
		Instant createdAt,
		Instant updatedAt) {

	static OrganizationResponse from(Organization organization) {
		return new OrganizationResponse(
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
				organization.getVerifiedAt(),
				organization.getVerificationReason(),
				organization.getCreatedAt(),
				organization.getUpdatedAt());
	}

}
