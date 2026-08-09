package com.hfxconnect.organization;

import com.hfxconnect.common.error.ApiError;
import com.hfxconnect.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current {@code ORGANIZATION} account's own organization profile, plus
 * the public verified-organization lookup by slug (Milestone 10A). Role
 * enforcement for {@code /api/v1/organizations/me/**} lives in {@code
 * SecurityConfig}, not here — see its Javadoc for why request matchers, not
 * {@code @PreAuthorize}, are this project's authorization mechanism (ADR-009).
 */
@Tag(name = "Organizations", description = "An ORGANIZATION account's own organization profile, and the public profile of any verified organization.")
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

	private final OrganizationService organizationService;

	public OrganizationController(OrganizationService organizationService) {
		this.organizationService = organizationService;
	}

	@Operation(summary = "Create the current account's organization profile", description = "ORGANIZATION role only. One profile per account. Always starts PENDING_VERIFICATION — this endpoint never accepts or sets a verification status. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The organization profile was created, pending verification"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account does not have the ORGANIZATION role", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "The current account already has an organization profile, or the generated slug collides with an existing one", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/me")
	public ResponseEntity<OrganizationResponse> create(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @Valid @RequestBody OrganizationProfileRequest request) {
		OrganizationResponse response = organizationService.create(principal, request);
		return ResponseEntity.created(URI.create("/api/v1/organizations/" + response.slug())).body(response);
	}

	@Operation(summary = "Get the current account's organization profile", description = "ORGANIZATION role only. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The current organization profile"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account does not have the ORGANIZATION role", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "The current account has not yet created an organization profile", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/me")
	public OrganizationResponse getCurrent(@AuthenticationPrincipal CurrentUserPrincipal principal) {
		return organizationService.getCurrent(principal.userId());
	}

	@Operation(summary = "Update the current account's organization profile", description = "ORGANIZATION role only. Never accepts owner, verification status, or reviewer fields. Changing name or websiteUrl on an already-VERIFIED organization resets it to PENDING_VERIFICATION — see ADR-017's Verification Reset Policy. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The updated organization profile"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account does not have the ORGANIZATION role", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "The current account has not yet created an organization profile", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PatchMapping("/me")
	public OrganizationResponse update(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @Valid @RequestBody OrganizationProfileRequest request) {
		return organizationService.update(principal.userId(), request);
	}

	@Operation(summary = "Get a verified organization's public profile", description = "Public — no authentication required. Only ever returns a currently-VERIFIED organization; a pending, rejected, or suspended organization's slug returns 404, indistinguishable from a slug that does not exist.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The verified organization's public profile"),
			@ApiResponse(responseCode = "404", description = "No verified organization has this slug", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{slug}")
	public PublicOrganizationResponse getPublic(@PathVariable String slug) {
		return organizationService.getPublicBySlug(slug);
	}

}
