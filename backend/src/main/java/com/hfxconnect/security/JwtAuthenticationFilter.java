package com.hfxconnect.security;

import com.hfxconnect.auth.AccessTokenService;
import com.hfxconnect.auth.AccessTokenService.AccessTokenClaims;
import com.hfxconnect.user.AccountStatus;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request presenting {@code Authorization: Bearer <token>}.
 * See ADR-009 for the full design. A missing or invalid token simply leaves
 * the request unauthenticated — whether that is acceptable is entirely the
 * {@code SecurityFilterChain}'s {@code authorizeHttpRequests} matchers'
 * decision (a public route continues normally; a protected route is rejected
 * downstream by {@link ApiAuthenticationEntryPoint}). This filter never
 * performs authorization (role checks) itself, and never queries refresh
 * sessions — it only validates the self-contained access token and re-loads
 * the current account row by the token's subject, exactly the two things
 * ADR-009's "Current-Request Identity" section describes.
 *
 * <p>Deliberately <strong>not</strong> a Spring bean: if this class were
 * {@code @Component}-annotated, Spring Boot's servlet-filter
 * auto-registration would additionally register it as an ordinary container
 * filter outside the security chain, running it a second time per request.
 * {@link SecurityConfig} constructs it directly and wires it into the chain
 * with {@code addFilterBefore}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private final AccessTokenService accessTokenService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(AccessTokenService accessTokenService, UserRepository userRepository) {
		this.accessTokenService = accessTokenService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			extractBearerToken(request).ifPresent(this::authenticate);
		}
		filterChain.doFilter(request, response);
	}

	private Optional<String> extractBearerToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			return Optional.of(header.substring(BEARER_PREFIX.length()));
		}
		return Optional.empty();
	}

	private void authenticate(String token) {
		AccessTokenClaims claims;
		try {
			claims = accessTokenService.validate(token);
		} catch (JwtException ex) {
			// The raw token and JJWT's own exception detail (which can embed
			// claim values) are never logged — only that validation failed,
			// and why in the broadest terms, for operational visibility.
			log.debug("Rejected an access token that failed validation: {}", ex.getClass().getSimpleName());
			return;
		}

		Optional<User> account = loadActiveAccount(claims.userId());
		account.ifPresent(user -> {
			CurrentUserPrincipal principal = new CurrentUserPrincipal(user.getId(), user.getEmail(), user.getRole());
			List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
			Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		});
	}

	/**
	 * Re-loads the account by the token's {@code sub} and checks its
	 * <em>current</em> status — not a claim baked into the token at issuance
	 * — so a deleted, suspended, or deactivated account loses access on its
	 * very next request rather than staying valid until the token's natural
	 * expiration. See ADR-009.
	 */
	private Optional<User> loadActiveAccount(UUID userId) {
		return userRepository.findById(userId).filter(user -> user.getStatus() == AccountStatus.ACTIVE);
	}

}
