package com.hfxconnect.common.error;

/**
 * A moderator/admin attempted to review their own resource submission or
 * correction report (Milestone 9A), or an admin attempted to verify/reject/
 * approve their own organization or its ownership claim (Milestone 10A).
 * Applies to every role, including {@code ADMIN} — self-review is a
 * conflict-of-interest rule, not a permission gate, so no role is exempt
 * from it. Promoted here from {@code com.hfxconnect.moderation} once a
 * second domain needed byte-for-byte the same rule and error shape — the
 * same "shared pure utilities extracted on second use, not preemptively"
 * pattern this codebase already documents (see
 * {@code docs/architecture/backend-architecture.md}).
 */
public class SelfReviewNotAllowedException extends ForbiddenException {

	public SelfReviewNotAllowedException() {
		super("SELF_REVIEW_NOT_ALLOWED", "You cannot review your own contribution.");
	}

}
