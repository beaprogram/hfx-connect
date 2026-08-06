package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ConflictException;

/**
 * Applying a correction report's changes (or its deactivation) could not be
 * completed. The report is left {@code PENDING_REVIEW} — never marked
 * {@code APPROVED} when application fails, and no partial resource mutation
 * is left behind (the whole review transaction rolls back).
 */
public class CorrectionApplicationConflictException extends ConflictException {

	private CorrectionApplicationConflictException(String message) {
		super("CORRECTION_APPLICATION_CONFLICT", message);
	}

	/** The report's target resource is no longer available (deleted, its foreign key set null since submission). */
	public static CorrectionApplicationConflictException targetUnavailable() {
		return new CorrectionApplicationConflictException(
				"This report's target resource is no longer available; its changes cannot be applied.");
	}

	/** Applying the merged field set produced an invalid or conflicting resource state. */
	public static CorrectionApplicationConflictException applicationConflict() {
		return new CorrectionApplicationConflictException(
				"Applying this report's proposed changes conflicts with existing resource data.");
	}

}
