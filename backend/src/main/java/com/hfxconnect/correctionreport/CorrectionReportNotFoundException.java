package com.hfxconnect.correctionreport;

import com.hfxconnect.common.error.NotFoundException;
import java.util.UUID;

/**
 * No correction report exists with the given id that also belongs to the
 * caller — the same "same 404 whether missing or someone else's" ownership-
 * privacy rule {@code ResourceSubmissionNotFoundException} already applies.
 *
 * <p>{@link #byId}: {@code public} as of Milestone 9A — also thrown by
 * {@code com.hfxconnect.moderation} for a genuinely missing report id.
 */
public class CorrectionReportNotFoundException extends NotFoundException {

	private CorrectionReportNotFoundException(String message) {
		super("CORRECTION_REPORT_NOT_FOUND", message);
	}

	public static CorrectionReportNotFoundException byId(UUID id) {
		return new CorrectionReportNotFoundException("No correction report exists with id " + id + ".");
	}

}
