package com.hfxconnect.auth;

import com.hfxconnect.common.error.UnauthorizedException;

/**
 * A generic 401 for "this request needs to authenticate again" — used both
 * when the refresh cookie required by an auth endpoint is entirely absent,
 * and (see {@code CurrentUserController}) as a defensive fallback if an
 * already-authenticated principal's account somehow no longer resolves by
 * the time a protected endpoint runs.
 */
public class AuthenticationRequiredException extends UnauthorizedException {

	public AuthenticationRequiredException() {
		super("AUTHENTICATION_REQUIRED", "Authentication is required.");
	}

}
