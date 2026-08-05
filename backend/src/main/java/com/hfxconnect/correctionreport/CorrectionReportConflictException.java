package com.hfxconnect.correctionreport;

import com.hfxconnect.common.error.ConflictException;

/**
 * The caller already has a {@code PENDING_REVIEW} report for the same
 * resource and issue type — the accidental-double-submit guard (see
 * {@code correction_reports_pending_duplicate_key}, a partial unique index
 * scoped to {@code PENDING_REVIEW} rows only). A user reporting two
 * genuinely different issues on the same resource, or re-reporting after
 * their first report is resolved, is never blocked by this.
 */
public class CorrectionReportConflictException extends ConflictException {

	CorrectionReportConflictException(String message) {
		super("CORRECTION_REPORT_CONFLICT", message);
	}

	static CorrectionReportConflictException duplicatePending() {
		return new CorrectionReportConflictException(
				"You already have a pending report for this issue on this resource.");
	}

}
