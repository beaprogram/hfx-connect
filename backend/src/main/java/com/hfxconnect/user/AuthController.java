package com.hfxconnect.user;

import com.hfxconnect.auth.AuthSessionResult;
import com.hfxconnect.auth.AuthenticationService;
import com.hfxconnect.auth.LoginRequest;
import com.hfxconnect.auth.LoginResponse;
import com.hfxconnect.auth.LogoutService;
import com.hfxconnect.auth.RefreshCookieConfig;
import com.hfxconnect.auth.RefreshService;
import com.hfxconnect.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login, refresh, and logout. See ADR-008 for the full
 * authentication-session design (access-token/refresh-token split, rotation,
 * reuse detection, cookie policy).
 *
 * <p>Depends directly on {@code com.hfxconnect.auth} services — a
 * cross-domain dependency in the same style
 * {@code docs/architecture/backend-architecture.md} already documents for
 * {@code ResourceService}'s direct dependency on {@code CategoryRepository}:
 * this controller genuinely needs both the {@code user} domain
 * ({@link RegistrationService}) and the {@code auth} domain (everything
 * else) it exposes under the same {@code /api/v1/auth} path, and there is no
 * repeated pattern yet that would justify an abstraction between them.
 *
 * <p><strong>No endpoint in this milestone requires or checks an access
 * token</strong> — request-level authorization is Milestone 5C's
 * responsibility. Logout in particular deliberately does not require one
 * (see {@code docs/milestones/milestone-05b-authentication-sessions.md}) —
 * the refresh cookie alone is sufficient to identify which session to
 * revoke.
 */
@Tag(name = "Auth", description = "Registration, login, refresh, and logout. No endpoint here checks an access token — protected-resource authorization is Milestone 5C.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final RegistrationService registrationService;
	private final AuthenticationService authenticationService;
	private final RefreshService refreshService;
	private final LogoutService logoutService;
	private final RefreshCookieConfig refreshCookieConfig;

	public AuthController(
			RegistrationService registrationService,
			AuthenticationService authenticationService,
			RefreshService refreshService,
			LogoutService logoutService,
			RefreshCookieConfig refreshCookieConfig) {
		this.registrationService = registrationService;
		this.authenticationService = authenticationService;
		this.refreshService = refreshService;
		this.logoutService = logoutService;
		this.refreshCookieConfig = refreshCookieConfig;
	}

	@Operation(summary = "Register a new account", description = "Always creates a USER-role, ACTIVE account with emailVerified=false — see ADR-007. Submitting role, status, emailVerified, passwordHash, or any other unrecognized field has no effect — it is ignored, not honored. Does not log the caller in.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Account created"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "An account with this email already exists", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
		UserResponse response = registrationService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Log in", description = "Verifies email and password, returns a short-lived access token, and sets a rotating refresh token as an HttpOnly cookie (see ADR-008). Unknown email and wrong password are indistinguishable: both return 401 AUTHENTICATION_FAILED with the same generic message. A correct-credentials account that is not ACTIVE returns 403 ACCOUNT_UNAVAILABLE.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Login succeeded"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Account is not available", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthSessionResult result = authenticationService.login(request.email(), request.password());
		return okWithRefreshCookie(result);
	}

	@Operation(summary = "Refresh an access token", description = "Reads the refresh token from the hfx_refresh_token cookie only — never from the request body, query string, or path. Rotates the refresh token on every successful call: the previous cookie value becomes permanently unusable. Presenting an already-used (rotated or revoked) token revokes every session descended from the same original login.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Refresh succeeded; a new access token and rotated refresh cookie are returned"),
			@ApiResponse(responseCode = "401", description = "Missing, invalid, expired, or reused refresh token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Account is not available", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/refresh")
	public ResponseEntity<LoginResponse> refresh(
			@CookieValue(name = RefreshCookieConfig.COOKIE_NAME, required = false) String refreshToken) {
		AuthSessionResult result = refreshService.refresh(refreshToken);
		return okWithRefreshCookie(result);
	}

	@Operation(summary = "Log out", description = "Revokes the refresh session matching the presented cookie, if any, and clears the cookie. Always returns 204, whether or not a valid session existed — safe to call repeatedly, and never reveals whether a particular session was found. Does not require an access token: the refresh cookie alone is sufficient to identify the session to revoke.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Always returned — logout is safe and idempotent regardless of whether a valid session existed")
	})
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = RefreshCookieConfig.COOKIE_NAME, required = false) String refreshToken) {
		logoutService.logout(refreshToken);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, refreshCookieConfig.buildClearCookie().toString())
				.build();
	}

	private ResponseEntity<LoginResponse> okWithRefreshCookie(AuthSessionResult result) {
		LoginResponse body = LoginResponse.of(result.accessToken(), result.expiresInSeconds(), result.user());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookieConfig.buildSetCookie(result.rawRefreshToken()).toString())
				.body(body);
	}

}
