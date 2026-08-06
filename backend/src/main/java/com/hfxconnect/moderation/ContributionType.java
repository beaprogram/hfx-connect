package com.hfxconnect.moderation;

/**
 * Which of the two Milestone 8B contribution domains a
 * {@link ModerationAuditEvent} (or a moderation queue entry) refers to.
 * Matches {@code moderation_audit_events_contribution_type_check} exactly.
 */
public enum ContributionType {
	RESOURCE_SUBMISSION,
	CORRECTION_REPORT
}
