package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "One organization/ownership audit trail entry. ADMIN-only — never exposed to an organization owner or publicly.")
public record OrganizationAuditEventResponse(
		UUID id,
		OrganizationAuditEventType eventType,
		UUID organizationId,
		UUID resourceId,
		UUID claimId,
		UUID actorUserId,
		String actorEmail,
		String reviewReason,
		Map<String, Object> beforeSnapshot,
		Map<String, Object> afterSnapshot,
		Instant createdAt) {

	static OrganizationAuditEventResponse from(OrganizationAuditEvent event) {
		return new OrganizationAuditEventResponse(
				event.getId(),
				event.getEventType(),
				event.getOrganizationId(),
				event.getResourceId(),
				event.getClaimId(),
				event.getActorUserId(),
				event.getActorEmail(),
				event.getReviewReason(),
				event.getBeforeSnapshot(),
				event.getAfterSnapshot(),
				event.getCreatedAt());
	}

}
