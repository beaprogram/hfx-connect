package com.hfxconnect.common.error;

/**
 * The item is no longer in a reviewable pending state by the time a
 * decision was about to be applied to it — either it was withdrawn, or a
 * concurrent decision already committed first. Used for resource
 * submissions/correction reports (Milestone 9A) and, reused as-is per this
 * codebase's "shared pure utilities extracted on second use" pattern,
 * organization profiles/resource-ownership claims (Milestone 10A). See
 * ADR-016's "Concurrency Control" section: the row-level lock this
 * exception follows from is what makes this an authoritative database-
 * transaction outcome, not a stale pre-check. Promoted here from
 * {@code com.hfxconnect.moderation.ContributionAlreadyReviewedException}
 * — same class name, code, and message preserved exactly, since the
 * Milestone 9A API contract (already documented and tested) must not
 * change shape just because the class moved package.
 */
public class ContributionAlreadyReviewedException extends ConflictException {

	public ContributionAlreadyReviewedException() {
		super("CONTRIBUTION_ALREADY_REVIEWED",
				"This contribution is no longer pending review — it was withdrawn or already decided.");
	}

}
