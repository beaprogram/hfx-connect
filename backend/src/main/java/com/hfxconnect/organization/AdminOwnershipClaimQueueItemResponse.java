package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One admin ownership-claim queue row — deliberately compact, the same posture {@code AdminOrganizationQueueItemResponse} already establishes. */
@Schema(description = "A compact resource-ownership-claim queue row.")
public record AdminOwnershipClaimQueueItemResponse(
		UUID id,
		UUID organizationId,
		String organizationName,
		ClaimedResourceSummaryResponse resource,
		ResourceOwnershipClaimStatus status,
		Instant requestedAt) {
}
