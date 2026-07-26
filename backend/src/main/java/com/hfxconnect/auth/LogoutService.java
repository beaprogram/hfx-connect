package com.hfxconnect.auth;

import org.springframework.stereotype.Service;

/**
 * Logout: revokes the refresh session matching the presented cookie, if
 * any. Always safe and idempotent — see
 * {@link RefreshSessionService#revokeByRawToken}, which this simply
 * delegates to — because a logout request's job is "make sure this session
 * is no longer valid," which is already true for a missing, unknown, or
 * already-revoked token.
 */
@Service
public class LogoutService {

	private final RefreshSessionService refreshSessionService;

	public LogoutService(RefreshSessionService refreshSessionService) {
		this.refreshSessionService = refreshSessionService;
	}

	public void logout(String presentedRawToken) {
		refreshSessionService.revokeByRawToken(presentedRawToken);
	}

}
