package com.hfxconnect.organization;

import com.hfxconnect.common.error.ApiError;
import com.hfxconnect.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current {@code ORGANIZATION} account's own resource-ownership claims
 * (Milestone 10A) — every claim is scoped to the caller's own organization
 * profile, resolved from {@link CurrentUserPrincipal}, never from a request
 * parameter.
 */
@Tag(name = "Organizations: Resource Claims", description = "An ORGANIZATION account's own requests to own existing public resource listings.")
@RestController
@RequestMapping("/api/v1/organizations/me/resource-claims")
public class ResourceOwnershipClaimController {

	private final ResourceOwnershipClaimService claimService;

	public ResourceOwnershipClaimController(ResourceOwnershipClaimService claimService) {
		this.claimService = claimService;
	}

	@Operation(summary = "Claim an existing resource listing", description = "ORGANIZATION role only, and only for a currently VERIFIED organization. The resource must exist, be active, and currently be unowned. Starts PENDING_REVIEW — does not grant ownership by itself. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The claim was created and is pending admin review"),
			@ApiResponse(responseCode = "400", description = "The resource is not active", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not an ORGANIZATION account, or the organization is not yet VERIFIED", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "The current account has no organization profile yet, or no resource exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "The resource is already owned, or this organization already has a pending claim for it", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{resourceId}")
	public ResponseEntity<OwnershipClaimResponse> create(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID resourceId) {
		OwnershipClaimResponse response = claimService.create(principal, resourceId);
		return ResponseEntity.created(URI.create("/api/v1/organizations/me/resource-claims/" + response.id())).body(response);
	}

	@Operation(summary = "List the current organization's resource-ownership claims", description = "Owner-scoped only, newest requested first. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Claim page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "The current account has no organization profile yet", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public OwnershipClaimPageResponse list(
			@AuthenticationPrincipal CurrentUserPrincipal principal,
			@Parameter(description = "Optional status filter.") @RequestParam(required = false) ResourceOwnershipClaimStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return claimService.list(principal.userId(), status, page, size);
	}

	@Operation(summary = "Withdraw a pending resource-ownership claim", description = "Only legal while still PENDING_REVIEW, and only for the current organization's own claim. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The withdrawn claim"),
			@ApiResponse(responseCode = "400", description = "The claim is no longer PENDING_REVIEW", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No claim with this id belongs to the current organization", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{claimId}/withdraw")
	public OwnershipClaimResponse withdraw(@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID claimId) {
		return claimService.withdraw(principal, claimId);
	}

}
