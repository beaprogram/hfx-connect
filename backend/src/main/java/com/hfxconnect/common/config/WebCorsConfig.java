package com.hfxconnect.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the public frontend (a separate origin — Next.js runs on its own
 * port/domain) to call the API directly from the browser.
 *
 * <p>Only {@code /api/v1/**} is covered — {@code /actuator/health} and the
 * OpenAPI/Swagger endpoints are same-origin developer/operator tooling, not
 * something the frontend calls cross-origin. Allowed origins are configured
 * through {@code app.cors.allowed-origins} (see {@code application.properties}
 * and {@code .env.example}), not hardcoded, so each environment (local,
 * eventual production) lists only its own real frontend origin(s) — never a
 * {@code "*"} wildcard. See
 * {@code docs/decisions/ADR-006-frontend-backend-connectivity.md} for why
 * direct browser-to-backend CORS was chosen over a Next.js proxy layer.
 *
 * <p>There is no {@code spring-boot-starter-security} filter chain in this
 * project yet (Milestone 5A added only {@code spring-security-crypto} for
 * password hashing — see ADR-007) — this is a plain Spring MVC CORS mapping,
 * not a security-filter-chain configuration.
 *
 * <p>{@code allowCredentials} is {@code true} as of Milestone 5B: login,
 * refresh, and logout set/read the {@code hfx_refresh_token} cookie, and a
 * browser never sends or exposes a cookie on a cross-origin {@code fetch}
 * unless both the request specifies {@code credentials: 'include'} <em>and</em>
 * the server's CORS response includes {@code Access-Control-Allow-Credentials: true}
 * — without this, refresh/logout would silently never receive the cookie at
 * all from the frontend's own separate origin. This is not a weakening of the
 * policy: {@code allowedOrigins} remains an explicit, environment-configured
 * allowlist with no {@code "*"} wildcard, which is required for
 * {@code allowCredentials(true)} to even be legal — Spring throws at startup
 * if the two are combined with a wildcard origin. See
 * {@code docs/decisions/ADR-008-authentication-session-architecture.md}.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebCorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/v1/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST")
				.allowedHeaders("Content-Type")
				.allowCredentials(true)
				.maxAge(3600);
	}

}
