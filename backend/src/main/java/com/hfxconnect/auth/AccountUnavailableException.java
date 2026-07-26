package com.hfxconnect.auth;

import com.hfxconnect.common.error.ForbiddenException;

/**
 * Credentials (or an otherwise-valid refresh session) checked out, but the
 * owning account's {@code status} is not {@link com.hfxconnect.user.AccountStatus#ACTIVE}.
 * {@code 403}, not {@code 401}: authentication genuinely succeeded here — the
 * account is merely not permitted to hold a session right now. Deliberately
 * does not say *why* (suspended vs. deactivated vs. pending) to avoid
 * revealing internal account state beyond "this account cannot log in."
 */
public class AccountUnavailableException extends ForbiddenException {

	public AccountUnavailableException() {
		super("ACCOUNT_UNAVAILABLE", "This account is not available.");
	}

}
