package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ConflictException;

/**
 * The contribution is no longer {@code PENDING_REVIEW} by the time this
 * decision was about to be applied — either it was withdrawn, or another
 * moderator's concurrent decision already committed first. See ADR-016's
 * "Concurrency Control" section: the row-level lock this exception follows
 * from is what makes this an authoritative database-transaction outcome, not
 * a stale pre-check.
 */
public class ContributionAlreadyReviewedException extends ConflictException {

	public ContributionAlreadyReviewedException() {
		super("CONTRIBUTION_ALREADY_REVIEWED",
				"This contribution is no longer pending review — it was withdrawn or already decided.");
	}

}
