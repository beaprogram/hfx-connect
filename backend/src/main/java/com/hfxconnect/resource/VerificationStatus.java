package com.hfxconnect.resource;

/**
 * Whether a resource's information has been checked against a trusted
 * source. New resources always start {@code UNVERIFIED}; only the
 * Milestone 9 moderation workflow will be able to set {@code VERIFIED} — no
 * mutator exists for this field yet, deliberately. Matches the database
 * check constraint {@code resources_verification_status_valid} exactly.
 */
public enum VerificationStatus {
	UNVERIFIED,
	VERIFIED
}
