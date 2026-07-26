package com.hfxconnect.auth;

import com.hfxconnect.common.error.UnauthorizedException;

/** The refresh cookie required by this endpoint is entirely absent from the request. */
public class AuthenticationRequiredException extends UnauthorizedException {

	public AuthenticationRequiredException() {
		super("AUTHENTICATION_REQUIRED", "Authentication is required.");
	}

}
