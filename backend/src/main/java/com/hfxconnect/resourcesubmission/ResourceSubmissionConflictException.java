package com.hfxconnect.resourcesubmission;

import com.hfxconnect.common.error.ConflictException;

/**
 * The caller already has a {@code PENDING_REVIEW} submission for the same
 * category and (normalized) name — the accidental-double-submit guard, not
 * a "you may only ever submit this once" rule (see
 * {@code resource_submissions_pending_duplicate_key}, a partial unique
 * index scoped to {@code PENDING_REVIEW} rows only).
 */
public class ResourceSubmissionConflictException extends ConflictException {

	ResourceSubmissionConflictException(String message) {
		super("RESOURCE_SUBMISSION_CONFLICT", message);
	}

	static ResourceSubmissionConflictException duplicatePending(String name) {
		return new ResourceSubmissionConflictException(
				"You already have a pending submission for '" + name + "' in this category.");
	}

}
