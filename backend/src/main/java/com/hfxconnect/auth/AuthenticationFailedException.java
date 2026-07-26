package com.hfxconnect.auth;

import com.hfxconnect.common.error.UnauthorizedException;

/**
 * Login credentials did not match. Deliberately the exact same exception
 * (same code, same message) whether the email doesn't exist or the password
 * is wrong — see {@code AuthenticationService}, which never distinguishes
 * the two cases in either its response or its own timing.
 */
public class AuthenticationFailedException extends UnauthorizedException {

	public AuthenticationFailedException() {
		super("AUTHENTICATION_FAILED", "Invalid email or password.");
	}

}
