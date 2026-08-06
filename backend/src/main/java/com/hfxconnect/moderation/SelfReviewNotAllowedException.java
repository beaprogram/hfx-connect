package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ForbiddenException;

/**
 * A MODERATOR or ADMIN attempted to review their own resource submission or
 * correction report. Applies to every role, including ADMIN — see ADR-016's
 * "Self-Review Prevention" section for why this is not bypassable.
 */
public class SelfReviewNotAllowedException extends ForbiddenException {

	public SelfReviewNotAllowedException() {
		super("SELF_REVIEW_NOT_ALLOWED", "A moderator or admin cannot review their own contribution.");
	}

}
