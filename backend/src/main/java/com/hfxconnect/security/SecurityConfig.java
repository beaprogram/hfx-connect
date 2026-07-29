package com.hfxconnect.security;

import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The whole request-authentication/authorization policy for this project.
 * See ADR-009 for the full design rationale: why request matchers rather
 * than {@code @PreAuthorize} for this milestone's policy shape, why
 * {@code STATELESS}, why form login and HTTP Basic are explicitly disabled,
 * why there is no role hierarchy, and why CSRF protection is not enabled
 * (Bearer-header authentication is not vulnerable to the threat CSRF
 * protection defends against — see ADR-009's "CSRF Review").
 *
 * <p>Route matrix (also documented in {@code docs/api/README.md} and
 * {@code docs/architecture/security-architecture.md}):
 *
 * <ul>
 *   <li>Public: {@code POST /api/v1/auth/register,login,refresh,logout};
 *       {@code GET /api/v1/categories/**}; {@code GET /api/v1/resources/**};
 *       {@code GET /actuator/health}; OpenAPI/Swagger (local dev tooling).
 *   <li>{@code GET /api/v1/users/me} — any authenticated, {@code ACTIVE}
 *       account.
 *   <li>{@code POST /api/v1/categories} — {@code ADMIN} only.
 *   <li>{@code POST /api/v1/resources} — {@code ADMIN} or {@code MODERATOR}.
 *       {@code ORGANIZATION} is deliberately excluded — see ADR-009.
 *   <li>{@code PUT /api/v1/resources/{id}/operating-hours} — {@code ADMIN} or
 *       {@code MODERATOR} (Milestone 6B — see ADR-011), the same pairing as
 *       resource creation.
 *   <li>Everything else: {@code authenticated()} — fail closed, not fail
 *       open, for any route this list doesn't already name.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final AccessTokenService accessTokenService;
	private final UserRepository userRepository;
	private final ApiAuthenticationEntryPoint authenticationEntryPoint;
	private final ApiAccessDeniedHandler accessDeniedHandler;
	private final CorsConfigurationSource corsConfigurationSource;

	public SecurityConfig(
			AccessTokenService accessTokenService,
			UserRepository userRepository,
			ApiAuthenticationEntryPoint authenticationEntryPoint,
			ApiAccessDeniedHandler accessDeniedHandler,
			CorsConfigurationSource corsConfigurationSource) {
		this.accessTokenService = accessTokenService;
		this.userRepository = userRepository;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.corsConfigurationSource = corsConfigurationSource;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/resources", "/api/v1/resources/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/resources").hasAnyRole("ADMIN", "MODERATOR")
						.requestMatchers(HttpMethod.PUT, "/api/v1/resources/*/operating-hours")
						.hasAnyRole("ADMIN", "MODERATOR")
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(accessTokenService, userRepository),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

}
