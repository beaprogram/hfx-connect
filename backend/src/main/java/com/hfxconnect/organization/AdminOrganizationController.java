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
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin organization-verification queue, detail view, decisions, and
 * audit history (Milestone 10A) — {@code ADMIN} only, enforced by {@code
 * SecurityConfig}'s explicit {@code /api/v1/admin/organizations/**} matcher.
 * {@code MODERATOR} is deliberately excluded — see ADR-017.
 */
@Tag(name = "Admin: Organizations", description = "Reviewing organization profiles for verification. ADMIN only.")
@RestController
@RequestMapping("/api/v1/admin/organizations")
public class AdminOrganizationController {

	private final AdminOrganizationService adminOrganizationService;
	private final OrganizationAuditQueryService auditQueryService;

	public AdminOrganizationController(AdminOrganizationService adminOrganizationService,
			OrganizationAuditQueryService auditQueryService) {
		this.adminOrganizationService = adminOrganizationService;
		this.auditQueryService = auditQueryService;
	}

	@Operation(summary = "List the organization verification queue", description = "Defaults to status=PENDING_VERIFICATION, oldest submitted first. Requires a Bearer access token for a current ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Queue page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not an ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public AdminOrganizationQueuePageResponse queue(
			@Parameter(description = "Defaults to PENDING_VERIFICATION.") @RequestParam(required = false) OrganizationVerificationStatus verificationStatus,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@Parameter(description = "createdAt (default, oldest first) or name.") @RequestParam(required = false) String sort) {
		return adminOrganizationService.queue(verificationStatus, page, size, sort);
	}

	@Operation(summary = "Get one organization for review", description = "Requires a Bearer access token for a current ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The organization"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not an ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No organization exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{organizationId}")
	public AdminOrganizationDetailResponse detail(@PathVariable UUID organizationId) {
		return adminOrganizationService.detail(organizationId);
	}

	@Operation(summary = "Verify an organization", description = "Only legal while PENDING_VERIFICATION. Requires a Bearer access token for a current ADMIN who does not own the target organization.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Verified"),
			@ApiResponse(responseCode = "400", description = "A missing or invalid reason", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not an ADMIN, or attempting to review one's own organization", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No organization exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already decided", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{organizationId}/verify")
	public AdminOrganizationDetailResponse verify(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID organizationId,
			@Valid @RequestBody OrganizationDecisionRequest request) {
		return adminOrganizationService.verify(principal, organizationId, request);
	}

	@Operation(summary = "Reject an organization", description = "Only legal while PENDING_VERIFICATION. The profile is not deleted; the reason is visible to the owner. Requires a Bearer access token for a current ADMIN who does not own the target organization.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rejected"),
			@ApiResponse(responseCode = "400", description = "A missing or invalid reason", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not an ADMIN, or attempting to review one's own organization", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No organization exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already decided", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{organizationId}/reject")
	public AdminOrganizationDetailResponse reject(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID organizationId,
			@Valid @RequestBody OrganizationDecisionRequest request) {
		return adminOrganizationService.reject(principal, organizationId, request);
	}

	@Operation(summary = "Suspend a verified organization", description = "Only legal while VERIFIED. Owned resources remain owned and public; only the organization's public attribution/profile becomes unavailable. Requires a Bearer access token for a current ADMIN who does not own the target organization.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Suspended"),
			@ApiResponse(responseCode = "400", description = "A missing/invalid reason, or the organization is not currently VERIFIED", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not an ADMIN, or attempting to review one's own organization", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No organization exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{organizationId}/suspend")
	public AdminOrganizationDetailResponse suspend(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID organizationId,
			@Valid @RequestBody OrganizationDecisionRequest request) {
		return adminOrganizationService.suspend(principal, organizationId, request);
	}

	@Operation(summary = "Get one organization's audit history", description = "Oldest first. Requires a Bearer access token for a current ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Audit event page, possibly empty"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not an ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{organizationId}/audit-events")
	public OrganizationAuditEventPageResponse auditEvents(
			@PathVariable UUID organizationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return auditQueryService.forOrganization(organizationId, page, size);
	}

}
