package com.hfxconnect.auth;

import com.hfxconnect.user.UserResponse;

/**
 * What a successful login or refresh produces: an access token, its
 * lifetime, the raw refresh token (used only long enough to become a cookie
 * — never logged, never returned in JSON), and the safe account summary.
 * Shared between {@link AuthenticationService} and {@link RefreshService}
 * since both produce exactly the same shape of result.
 */
public record AuthSessionResult(String accessToken, long expiresInSeconds, String rawRefreshToken, UserResponse user) {
}
