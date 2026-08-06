package com.hfxconnect.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "One moderation audit trail entry. Moderator/admin-only — never exposed to a contribution's owner or publicly.")
public record ModerationAuditEventResponse(
		UUID id,
		ContributionType contributionType,
		UUID contributionId,
		ModerationAction action,
		ModerationDecision decision,
		UUID actorUserId,
		String actorEmail,
		String reviewReason,
		UUID affectedResourceId,
		Map<String, Object> beforeSnapshot,
		Map<String, Object> afterSnapshot,
		Instant createdAt) {

	static ModerationAuditEventResponse from(ModerationAuditEvent event) {
		return new ModerationAuditEventResponse(
				event.getId(),
				event.getContributionType(),
				event.getContributionId(),
				event.getAction(),
				event.getDecision(),
				event.getActorUserId(),
				event.getActorEmail(),
				event.getReviewReason(),
				event.getAffectedResourceId(),
				event.getBeforeSnapshot(),
				event.getAfterSnapshot(),
				event.getCreatedAt());
	}

}
