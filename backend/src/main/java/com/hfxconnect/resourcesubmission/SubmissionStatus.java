package com.hfxconnect.resourcesubmission;

/**
 * A resource submission's review state. Matches the database check
 * constraint {@code resource_submissions_status_check} exactly. Milestone
 * 8B only ever writes {@link #PENDING_REVIEW} (on creation) and
 * {@link #WITHDRAWN} (via the owner's own withdrawal) — {@link #APPROVED}
 * and {@link #REJECTED} exist so Milestone 9's moderation workflow has a
 * schema to write into, but nothing in this milestone assigns them.
 */
public enum SubmissionStatus {
	PENDING_REVIEW,
	APPROVED,
	REJECTED,
	WITHDRAWN
}
