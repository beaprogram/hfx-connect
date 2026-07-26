package com.hfxconnect.auth;

import com.hfxconnect.common.error.UnauthorizedException;

/**
 * The presented refresh token's hash matches no known session at all —
 * distinct from {@link RefreshTokenExpiredException} (a real, known session
 * that has simply expired) and {@link RefreshTokenReusedException} (a real,
 * known session that is already revoked).
 */
public class InvalidRefreshTokenException extends UnauthorizedException {

	public InvalidRefreshTokenException() {
		super("INVALID_REFRESH_TOKEN", "The refresh token is invalid.");
	}

}
