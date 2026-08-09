package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The full admin-facing view of one organization. {@code ownerUserId}/
 * {@code verifiedByUserId} are raw account ids, never resolved to an email —
 * the same "an admin wanting human identity uses the audit-events endpoint's
 * point-in-time {@code actorEmail} snapshot" reasoning {@code
 * ResourceSubmissionModerationDetailResponse.reviewedByUserId} already
 * established (ADR-016). Never includes password/session/token data.
 */
@Schema(description = "The full admin-facing view of one organization.")
public record AdminOrganizationDetailResponse(
		UUID id,
		UUID ownerUserId,
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
		UUID verifiedByUserId,
		Instant verifiedAt,
		String verificationReason,
		Instant createdAt,
		Instant updatedAt) {

	static AdminOrganizationDetailResponse from(Organization organization) {
		return new AdminOrganizationDetailResponse(
				organization.getId(),
				organization.getOwnerUserId(),
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
				organization.getVerifiedByUserId(),
				organization.getVerifiedAt(),
				organization.getVerificationReason(),
				organization.getCreatedAt(),
				organization.getUpdatedAt());
	}

}
