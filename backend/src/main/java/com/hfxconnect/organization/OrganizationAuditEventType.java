package com.hfxconnect.organization;

/**
 * What an {@link OrganizationAuditEvent} row records (Milestone 10A).
 * Matches {@code organization_audit_events_event_type_check}. A focused,
 * separate event vocabulary from {@code com.hfxconnect.moderation
 * .ModerationAction} — organization identity/ownership events are a
 * different domain from resource-contribution review, even though both
 * are "a decision that happened," so forcing them into one shared enum
 * would blur two genuinely different meanings (ADR-017).
 */
public enum OrganizationAuditEventType {
	ORGANIZATION_SUBMITTED,
	ORGANIZATION_VERIFIED,
	ORGANIZATION_REJECTED,
	ORGANIZATION_SUSPENDED,
	OWNERSHIP_CLAIM_SUBMITTED,
	OWNERSHIP_CLAIM_APPROVED,
	OWNERSHIP_CLAIM_REJECTED,
	OWNERSHIP_CLAIM_WITHDRAWN
}
