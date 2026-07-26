package com.hfxconnect.auth;

import com.hfxconnect.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned by both {@code POST /api/v1/auth/login} and
 * {@code POST /api/v1/auth/refresh} — a successful refresh looks like a
 * fresh login from the client's perspective (new access token, new refresh
 * cookie, same account summary). Never includes the refresh token itself —
 * see ADR-008 — which is set separately as an {@code HttpOnly} cookie.
 */
@Schema(description = "A successful login or refresh. The refresh token is never included here — it is set as an HttpOnly cookie instead.")
public record LoginResponse(
		String accessToken,
		@Schema(example = "Bearer") String tokenType,
		@Schema(description = "Access token lifetime in seconds.", example = "900") long expiresIn,
		UserResponse user) {

	static final String TOKEN_TYPE = "Bearer";

	public static LoginResponse of(String accessToken, long expiresInSeconds, UserResponse user) {
		return new LoginResponse(accessToken, TOKEN_TYPE, expiresInSeconds, user);
	}

}
