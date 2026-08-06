package com.hfxconnect.moderation;

/**
 * What a {@link ModerationAuditEvent} row records. See ADR-016's "Audit
 * Model" section for the exact rule this application follows: {@link
 * #REVIEW_DECISION} is used only when a decision produces no concrete
 * resource-table effect (every rejection, and an approval where the
 * moderator applied no change and requested no deactivation); {@link
 * #RESOURCE_CREATED}, {@link #RESOURCE_UPDATED}, and {@link
 * #RESOURCE_DEACTIVATED} are used exactly when there is one, and that row
 * itself carries the decision/reason/actor rather than needing a separate
 * companion row. A single approval can therefore produce more than one row
 * (for example a correction report approved with both
 * {@code applyProposedChanges} and {@code deactivateResource}), but never a
 * redundant {@code REVIEW_DECISION} row alongside an effect row for the same
 * decision.
 */
public enum ModerationAction {
	REVIEW_DECISION,
	RESOURCE_CREATED,
	RESOURCE_UPDATED,
	RESOURCE_DEACTIVATED
}
