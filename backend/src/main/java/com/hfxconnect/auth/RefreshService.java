package com.hfxconnect.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh: validates and rotates the presented refresh token (see
 * {@link RefreshSessionService#rotate}) and, only on success, issues a new
 * access token. The refresh token itself must come from the {@code hfx_refresh_token}
 * cookie — {@link AuthController} never accepts one from anywhere else.
 */
@Service
public class RefreshService {

	private final RefreshSessionService refreshSessionService;
	private final AccessTokenService accessTokenService;

	public RefreshService(RefreshSessionService refreshSessionService, AccessTokenService accessTokenService) {
		this.refreshSessionService = refreshSessionService;
		this.accessTokenService = accessTokenService;
	}

	@Transactional
	public AuthSessionResult refresh(String presentedRawToken) {
		if (presentedRawToken == null || presentedRawToken.isBlank()) {
			throw new AuthenticationRequiredException();
		}

		RefreshSessionService.RotatedRefreshToken rotated = refreshSessionService.rotate(presentedRawToken);
		AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(
				rotated.user().getId(), rotated.user().getRole());

		return new AuthSessionResult(
				accessToken.token(),
				accessToken.expiresInSeconds(),
				rotated.rawToken(),
				AuthenticationService.toUserResponse(rotated.user()));
	}

}
