package com.hfxconnect.user;

/**
 * Account lifecycle status. Persisted as the enum's name (a string), not its
 * ordinal — see {@code V4__create_users_table.sql}'s {@code users_status_valid}
 * check constraint, which is the authoritative list of valid values.
 *
 * <p>Deliberately independent of {@code User.emailVerified}: registration
 * (Milestone 5A) always produces {@link #ACTIVE} with {@code emailVerified =
 * false} — see ADR-007 for why an unverified account is still usable rather
 * than stuck in a {@link #PENDING_VERIFICATION} state with no real
 * verification mechanism to leave it from. {@link #SUSPENDED} and
 * {@link #DEACTIVATED} exist for later milestones (Milestone 9 moderation);
 * no Milestone 5A code path produces them.
 */
public enum AccountStatus {
	ACTIVE,
	PENDING_VERIFICATION,
	SUSPENDED,
	DEACTIVATED
}
