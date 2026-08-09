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
 * The admin resource-ownership-claim queue, detail view, and approve/reject
 * decisions (Milestone 10A) — {@code ADMIN} only, enforced by {@code
 * SecurityConfig}'s explicit {@code /api/v1/admin/resource-ownership-claims/**}
 * matcher.
 */
@Tag(name = "Admin: Resource Ownership Claims", description = "Reviewing organizations' requests to own existing resource listings. ADMIN only.")
@RestController
@RequestMapping("/api/v1/admin/resource-ownership-claims")
public class AdminResourceOwnershipClaimController {

	private final AdminResourceOwnershipClaimService adminClaimService;

	public AdminResourceOwnershipClaimController(AdminResourceOwnershipClaimService adminClaimService) {
		this.adminClaimService = adminClaimService;
	}

	@Operation(summary = "List the resource-ownership-claim queue", description = "Defaults to status=PENDING_REVIEW, oldest requested first. Requires a Bearer access token for a current ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Queue page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not an ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public AdminOwnershipClaimQueuePageResponse queue(
			@Parameter(description = "Defaults to PENDING_REVIEW.") @RequestParam(required = false) ResourceOwnershipClaimStatus status,
			@Parameter(description = "Optional organization filter.") @RequestParam(required = false) UUID organizationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminClaimService.queue(status, organizationId, page, size);
	}

	@Operation(summary = "Get one resource-ownership claim for review", description = "Requires a Bearer access token for a current ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The claim"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not an ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No claim exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{claimId}")
	public AdminOwnershipClaimDetailResponse detail(@PathVariable UUID claimId) {
		return adminClaimService.detail(claimId);
	}

	@Operation(summary = "Approve a resource-ownership claim", description = "Assigns resources.organizationId in the same atomic transaction, re-checking the organization is still VERIFIED and the resource is still active and unowned. Requires a Bearer access token for a current ADMIN who does not own the claiming organization.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Approved; the resource is now owned by this organization"),
			@ApiResponse(responseCode = "400", description = "A missing/invalid reason, or the resource is no longer active", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not an ADMIN, attempting to review one's own organization's claim, or the organization is no longer VERIFIED", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No claim exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already decided, or the resource became owned by another organization in the meantime", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{claimId}/approve")
	public AdminOwnershipClaimDetailResponse approve(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID claimId,
			@Valid @RequestBody OrganizationDecisionRequest request) {
		return adminClaimService.approve(principal, claimId, request);
	}

	@Operation(summary = "Reject a resource-ownership claim", description = "The resource is never modified. Requires a Bearer access token for a current ADMIN who does not own the claiming organization.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rejected"),
			@ApiResponse(responseCode = "400", description = "A missing or invalid reason", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not an ADMIN, or attempting to review one's own organization's claim", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No claim exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already decided", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{claimId}/reject")
	public AdminOwnershipClaimDetailResponse reject(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID claimId,
			@Valid @RequestBody OrganizationDecisionRequest request) {
		return adminClaimService.reject(principal, claimId, request);
	}

}
