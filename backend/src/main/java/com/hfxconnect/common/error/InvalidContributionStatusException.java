package com.hfxconnect.common.error;

/**
 * A caller tried to withdraw a resource submission or correction report
 * that is no longer {@code PENDING_REVIEW} (already withdrawn, or already
 * moved into a moderation outcome). Shared across
 * {@code com.hfxconnect.resourcesubmission} and
 * {@code com.hfxconnect.correctionreport} (Milestone 8B) since both
 * domains have byte-for-byte the same rule and error shape.
 */
public class InvalidContributionStatusException extends BadRequestException {

	public InvalidContributionStatusException(String message) {
		super("INVALID_CONTRIBUTION_STATUS", message);
	}

}
