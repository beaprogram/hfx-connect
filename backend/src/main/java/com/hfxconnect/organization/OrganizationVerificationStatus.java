package com.hfxconnect.organization;

/**
 * An organization profile's identity-verification state (Milestone 10A) —
 * distinct from {@code com.hfxconnect.resource.VerificationStatus} (content
 * verification on a single resource): different domains that happen to
 * share a word. Matches {@code organizations_verification_status_check}
 * exactly. Reuses the exact "PENDING_VERIFICATION" spelling
 * {@code com.hfxconnect.user.AccountStatus} already established for the
 * analogous concept, rather than inventing a differently-named equivalent.
 *
 * <p>{@link #SUSPENDED} is reached only from {@link #VERIFIED} (an admin
 * action on a previously-verified organization) — see ADR-017's
 * "Organization Suspension" section.
 */
public enum OrganizationVerificationStatus {
	PENDING_VERIFICATION,
	VERIFIED,
	REJECTED,
	SUSPENDED
}
