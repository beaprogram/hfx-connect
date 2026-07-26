package com.hfxconnect.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code hfx_refresh_token} cookie consistently for every
 * endpoint that sets or clears it. See ADR-008's "Cookie Policy" section for
 * why {@code Secure}/{@code SameSite} are environment-configured rather than
 * hardcoded — the correct values genuinely differ between local development
 * (same-site, plain HTTP) and this project's actual cross-site production
 * deployment (Vercel/Render — see ADR-006).
 */
@Component
public class RefreshCookieConfig {

	public static final String COOKIE_NAME = "hfx_refresh_token";

	private final String path;
	private final boolean secure;
	private final String sameSite;
	private final long refreshTokenTtlSeconds;

	public RefreshCookieConfig(
			@Value("${app.auth.cookie.path}") String path,
			@Value("${app.auth.cookie.secure}") boolean secure,
			@Value("${app.auth.cookie.same-site}") String sameSite,
			@Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
		this.path = path;
		this.secure = secure;
		this.sameSite = sameSite;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	public String cookieName() {
		return COOKIE_NAME;
	}

	/** {@code Max-Age} matches the refresh token's own lifetime — the cookie should never outlive the session it carries. */
	public ResponseCookie buildSetCookie(String rawToken) {
		return baseCookieBuilder(rawToken)
				.maxAge(Duration.ofSeconds(refreshTokenTtlSeconds))
				.build();
	}

	/** {@code Max-Age=0} with an empty value — the standard way to instruct the browser to delete a cookie immediately. */
	public ResponseCookie buildClearCookie() {
		return baseCookieBuilder("")
				.maxAge(Duration.ZERO)
				.build();
	}

	private ResponseCookie.ResponseCookieBuilder baseCookieBuilder(String value) {
		return ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite(sameSite)
				.path(path);
	}

}
