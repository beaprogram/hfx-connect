package com.hfxconnect.moderation;

import com.hfxconnect.security.CurrentUserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single place that constructs and persists a {@link
 * ModerationAuditEvent} — both review services go through this rather than
 * calling {@link ModerationAuditEventRepository} directly, so "an audit
 * event's actor always comes from the authenticated principal, never a
 * request parameter" is enforced in exactly one place.
 */
@Component
class ModerationAuditRecorder {

	private final ModerationAuditEventRepository auditEventRepository;

	ModerationAuditRecorder(ModerationAuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	void record(ContributionType contributionType, UUID contributionId, ModerationAction action,
			ModerationDecision decision, CurrentUserPrincipal actor, String reviewReason, UUID affectedResourceId,
			Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
		auditEventRepository.save(new ModerationAuditEvent(contributionType, contributionId, action, decision,
				actor.userId(), actor.email(), reviewReason, affectedResourceId, beforeSnapshot, afterSnapshot));
	}

}
