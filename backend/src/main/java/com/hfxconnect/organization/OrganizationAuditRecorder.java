package com.hfxconnect.organization;

import com.hfxconnect.security.CurrentUserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single place that constructs and persists an {@link
 * OrganizationAuditEvent} — every organization/claim service goes through
 * this rather than calling {@link OrganizationAuditEventRepository}
 * directly, the same reasoning {@code com.hfxconnect.moderation
 * .ModerationAuditRecorder} already established.
 */
@Component
class OrganizationAuditRecorder {

	private final OrganizationAuditEventRepository auditEventRepository;

	OrganizationAuditRecorder(OrganizationAuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	void record(OrganizationAuditEventType eventType, UUID organizationId, UUID resourceId, UUID claimId,
			CurrentUserPrincipal actor, String reviewReason, Map<String, Object> beforeSnapshot,
			Map<String, Object> afterSnapshot) {
		auditEventRepository.save(new OrganizationAuditEvent(eventType, organizationId, resourceId, claimId,
				actor.userId(), actor.email(), reviewReason, beforeSnapshot, afterSnapshot));
	}

}
