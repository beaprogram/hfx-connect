package com.hfxconnect.user;

/**
 * Account roles. Persisted as the enum's name (a string), not its ordinal —
 * see {@code V4__create_users_table.sql}'s {@code users_role_valid} check
 * constraint, which is the authoritative list of valid values.
 *
 * <p>Registration (Milestone 5A) can only ever produce {@link #USER} — see
 * {@code RegistrationService}. {@link #ORGANIZATION}, {@link #MODERATOR}, and
 * {@link #ADMIN} exist so the column and its constraint don't need a later
 * migration once Milestone 5C (role-based authorization) and later
 * organization/moderation milestones need them.
 */
public enum Role {
	USER,
	ORGANIZATION,
	MODERATOR,
	ADMIN
}
