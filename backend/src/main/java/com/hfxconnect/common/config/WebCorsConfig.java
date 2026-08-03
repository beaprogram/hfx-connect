package com.hfxconnect.common.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
 * <p><strong>Exposed as a {@link CorsConfigurationSource} bean, not a
 * {@code WebMvcConfigurer.addCorsMappings} implementation</strong>, as of
 * Milestone 5C: {@code SecurityConfig}'s {@code SecurityFilterChain} needs a
 * {@code CorsConfigurationSource} to delegate to (Spring Security's
 * {@code HttpSecurity.cors()} does not read {@code WebMvcConfigurer}
 * registrations), and the security filter chain now covers every request
 * (including plain public {@code GET}s), so this one bean is the single CORS
 * policy definition for the whole application — see ADR-009.
 *
 * <p>{@code allowedHeaders} includes {@code Authorization} as of Milestone
 * 5C — required for the browser to send the Bearer access token
 * cross-origin at all; without it, the CORS preflight itself rejects the
 * real request before it reaches the backend. {@code allowCredentials}
 * remains {@code true} (Milestone 5B — refresh/logout's cookie), which
 * remains legal only because {@code allowedOrigins} stays an explicit,
 * environment-configured allowlist with no {@code "*"} wildcard — Spring
 * throws at startup if the two are combined with a wildcard origin.
 *
 * <p>{@code allowedMethods} includes {@code PUT} and {@code DELETE} as of
 * Milestone 8A — the saved-resources save/remove endpoints are the first
 * browser-called routes to use these methods (the existing admin
 * {@code PUT} routes on {@code ResourceController} were previously only
 * exercised by backend integration tests, never from the browser, so this
 * gap went unnoticed until a real cross-origin preflight hit it).
 */
@Configuration
public class WebCorsConfig {

	private final String[] allowedOrigins;

	public WebCorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(allowedOrigins));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
		configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", configuration);
		return source;
	}

}
