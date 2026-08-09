package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The full admin-facing view of one resource-ownership claim: the claiming
 * organization's identity and current verification state, the target
 * resource, its current owning organization (if any — {@code
 * currentResourceOrganizationId} is read fresh from the resource, never
 * inferred from the claim's own status), and the claim's own review state.
 */
@Schema(description = "The full admin-facing view of one resource-ownership claim.")
public record AdminOwnershipClaimDetailResponse(
		UUID id,
		UUID organizationId,
		String organizationName,
		String organizationSlug,
		OrganizationVerificationStatus organizationVerificationStatus,
		ClaimedResourceSummaryResponse resource,
		UUID currentResourceOrganizationId,
		ResourceOwnershipClaimStatus status,
		Instant requestedAt,
		UUID reviewedByUserId,
		Instant reviewedAt,
		String reviewReason) {
}
