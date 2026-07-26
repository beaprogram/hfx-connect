package com.hfxconnect.common.text;

import java.util.Locale;

/**
 * The single, shared email-normalization rule (trim, then lowercase with
 * {@link Locale#ROOT} so behavior doesn't depend on server locale) used
 * everywhere an email needs to match against {@code users.normalized_email}
 * — registration's own duplicate check and login's account lookup must use
 * byte-for-byte the same rule, or {@code User@Example.org} could register
 * successfully but fail to log back in as {@code user@example.org}.
 *
 * <p>Extracted from {@code com.hfxconnect.user.RegistrationValidation} once
 * {@code com.hfxconnect.auth.AuthenticationService} needed the identical
 * rule — the same "shared pure utilities are extracted on second use, not
 * preemptively" pattern {@link SlugGenerator} already established (see
 * {@code docs/architecture/backend-architecture.md}).
 */
public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
