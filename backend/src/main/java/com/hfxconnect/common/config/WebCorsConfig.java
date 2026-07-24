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
 * password hashing — see ADR-007; login/tokens are 5B) — this is a plain
 * Spring MVC CORS mapping, not a security-filter-chain configuration, and
 * {@code allowCredentials} is left {@code false} since no cookie/session-based
 * request is ever made.
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
				.allowCredentials(false)
				.maxAge(3600);
	}

}
