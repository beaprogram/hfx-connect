package com.hfxconnect.auth;

import com.hfxconnect.common.error.UnauthorizedException;

/**
 * A known refresh session was found, but it is already {@code revoked_at}-set
 * — either because it was already rotated away by an earlier refresh, already
 * revoked by a logout, or (the case this is actually named for) an attacker's
 * copy of an already-consumed token being replayed. See
 * {@code RefreshSessionService#revokeFamily} — presenting one of these
 * revokes every session descended from the same original login, not just
 * this one.
 */
public class RefreshTokenReusedException extends UnauthorizedException {

	public RefreshTokenReusedException() {
		super("REFRESH_TOKEN_REUSED", "The refresh token has already been used.");
	}

}
