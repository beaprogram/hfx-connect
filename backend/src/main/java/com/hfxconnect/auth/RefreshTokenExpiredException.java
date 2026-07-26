package com.hfxconnect.auth;

import com.hfxconnect.common.error.UnauthorizedException;

/** A known refresh session was found, but its {@code expires_at} has passed. */
public class RefreshTokenExpiredException extends UnauthorizedException {

	public RefreshTokenExpiredException() {
		super("REFRESH_TOKEN_EXPIRED", "The refresh token has expired.");
	}

}
