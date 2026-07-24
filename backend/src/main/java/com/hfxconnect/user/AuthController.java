package com.hfxconnect.user;

import com.hfxconnect.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration only. No login, token issuance, or session creation happens
 * here — this endpoint creates an account; it does not authenticate the
 * caller. Login is Milestone 5B's responsibility.
 *
 * <p>No {@code Location} response header: there is no
 * {@code GET /api/v1/users/{id}} endpoint yet for one to point to, and this
 * milestone deliberately does not fabricate a route that doesn't exist.
 */
@Tag(name = "Auth", description = "Account registration. Does not authenticate the caller — login is a later milestone.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final RegistrationService registrationService;

	public AuthController(RegistrationService registrationService) {
		this.registrationService = registrationService;
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

}
