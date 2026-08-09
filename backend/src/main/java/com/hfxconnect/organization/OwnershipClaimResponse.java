package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One of the current organization's own resource-ownership claims (Milestone 10A). */
@Schema(description = "One of the current organization's resource-ownership claims.")
public record OwnershipClaimResponse(
		UUID id,
		ClaimedResourceSummaryResponse resource,
		ResourceOwnershipClaimStatus status,
		Instant requestedAt,
		Instant reviewedAt,
		String reviewReason) {

	static OwnershipClaimResponse from(ResourceOwnershipClaim claim, ClaimedResourceSummaryResponse resource) {
		return new OwnershipClaimResponse(
				claim.getId(), resource, claim.getStatus(), claim.getRequestedAt(), claim.getReviewedAt(), claim.getReviewReason());
	}

}
