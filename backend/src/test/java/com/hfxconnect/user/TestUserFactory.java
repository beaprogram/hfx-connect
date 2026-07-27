package com.hfxconnect.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds an unpersisted {@link User} with an arbitrary {@link Role}/
 * {@link AccountStatus}, for integration tests exercising role-gated
 * authorization (Milestone 5C). Registration's public API only ever produces
 * {@code USER}/{@code ACTIVE} accounts by design (see {@code User}'s own
 * Javadoc), so there is no way to obtain an {@code ADMIN}/{@code MODERATOR}/
 * {@code ORGANIZATION} account, or a non-{@code ACTIVE} one, through the HTTP
 * API at all — tests that need one construct it directly via {@code User}'s
 * package-private full constructor (this factory lives in the same package
 * for exactly that reason) and persist it with {@code UserRepository.saveAndFlush}.
 */
public final class TestUserFactory {

	private static final String PLACEHOLDER_PASSWORD_HASH = "$2a$12$placeholderPlaceholderPlaceholderPlace";

	private TestUserFactory() {
	}

	public static User withRole(Role role) {
		return withRoleAndStatus(role, AccountStatus.ACTIVE);
	}

	public static User withRoleAndStatus(Role role, AccountStatus status) {
		String email = role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@example.org";
		Instant now = Instant.now();
		return new User(null, email, email, PLACEHOLDER_PASSWORD_HASH, role, status, true, now, now);
	}

	/**
	 * Rebuilds {@code existing} with a different role and/or status but the
	 * same identity (id, email, password hash) — for tests that need to
	 * simulate an administrator changing an already-persisted account (e.g.
	 * a promotion or suspension) and confirm the effect on a request using a
	 * token issued before the change. {@code User} has no setters at all
	 * (see its own Javadoc), so this constructs a new instance and expects
	 * the caller to {@code saveAndFlush} it as an update to the same row.
	 */
	public static User withChangedRoleAndStatus(User existing, Role role, AccountStatus status) {
		return new User(existing.getId(), existing.getEmail(), existing.getNormalizedEmail(),
				existing.getPasswordHash(), role, status, existing.isEmailVerified(),
				existing.getCreatedAt(), Instant.now());
	}

}
