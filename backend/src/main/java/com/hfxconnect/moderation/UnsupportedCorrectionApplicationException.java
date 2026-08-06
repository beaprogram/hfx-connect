package com.hfxconnect.moderation;

import com.hfxconnect.common.error.BadRequestException;
import com.hfxconnect.correctionreport.IssueType;

/**
 * {@code applyProposedChanges=true} was requested for an issue type this
 * workflow has no automated field mapping for. See ADR-016's "Correction
 * Application Policy" section: {@link IssueType#OPERATING_HOURS} has no
 * structured proposed-schedule data to apply (V9's schema never captured
 * one), and {@link IssueType#DUPLICATE_RESOURCE} has no automated merge —
 * both require a moderator to reject with an explanatory reason, or handle
 * the underlying change manually through the existing operating-hours/
 * resource-update endpoints, rather than this workflow pretending to
 * automate something it does not.
 */
public class UnsupportedCorrectionApplicationException extends BadRequestException {

	public UnsupportedCorrectionApplicationException(IssueType issueType) {
		super("UNSUPPORTED_CORRECTION_APPLICATION",
				"Issue type " + issueType + " has no supported automatic field application; reject with a reason instead.");
	}

}
