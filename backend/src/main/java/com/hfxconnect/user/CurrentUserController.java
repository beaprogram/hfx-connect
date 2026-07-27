package com.hfxconnect.user;

import com.hfxconnect.auth.AuthenticationRequiredException;
import com.hfxconnect.common.error.ApiError;
import com.hfxconnect.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current authenticated account. Requires a valid Bearer access token
 * (see ADR-009); there is no user-ID parameter, and never will be one here —
 * looking up an arbitrary account by ID is an admin/role-management
 * capability this project does not have.
 *
 * <p>Re-loads the account by the authenticated principal's {@code userId}
 * rather than trusting anything else already computed about it, so the
 * response always reflects the account's current row. In ordinary operation
 * this can never actually fail: {@code JwtAuthenticationFilter} only
 * authenticates a request after confirming the same account exists and is
 * {@code ACTIVE}, moments earlier in the same request.
 */
@Tag(name = "Users", description = "The current authenticated account.")
@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {

	private final UserRepository userRepository;

	public CurrentUserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Operation(summary = "Get the current authenticated account", description = "Requires a valid Bearer access token. Returns only the caller's own account — no password hash, refresh sessions, or other internal metadata.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Current account"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal CurrentUserPrincipal principal) {
		User user = userRepository.findById(principal.userId()).orElseThrow(AuthenticationRequiredException::new);
		return UserResponse.from(user);
	}

}
