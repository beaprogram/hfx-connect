package com.hfxconnect.correctionreport;

/**
 * A correction report's review state. Matches the database check
 * constraint {@code correction_reports_status_check} exactly. Milestone 8B
 * only ever writes {@link #PENDING_REVIEW} (on creation) and
 * {@link #WITHDRAWN} (via the owner's own withdrawal) — {@link #APPROVED}
 * and {@link #REJECTED} exist so Milestone 9's moderation workflow has a
 * schema to write into, but nothing in this milestone assigns them.
 */
public enum CorrectionReportStatus {
	PENDING_REVIEW,
	APPROVED,
	REJECTED,
	WITHDRAWN
}
