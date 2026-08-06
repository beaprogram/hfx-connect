package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ApiError;
import com.hfxconnect.resourcesubmission.SubmissionStatus;
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
 * The resource-submission moderation queue and review decisions (Milestone
 * 9A) — MODERATOR/ADMIN only, enforced by {@code SecurityConfig}'s explicit
 * {@code /api/v1/moderation/**} matcher (never trusted from a client-supplied
 * role claim; see ADR-016).
 */
@Tag(name = "Moderation: Resource Submissions", description = "Reviewing pending resource submissions. MODERATOR or ADMIN only.")
@RestController
@RequestMapping("/api/v1/moderation/resource-submissions")
public class ModerationResourceSubmissionController {

	private final ResourceSubmissionReviewService reviewService;
	private final ModerationAuditQueryService auditQueryService;

	public ModerationResourceSubmissionController(ResourceSubmissionReviewService reviewService,
			ModerationAuditQueryService auditQueryService) {
		this.reviewService = reviewService;
		this.auditQueryService = auditQueryService;
	}

	@Operation(summary = "List the resource-submission moderation queue", description = "Defaults to status=PENDING_REVIEW, oldest submitted first. Requires a Bearer access token for a current MODERATOR or ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Queue page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public ResourceSubmissionQueuePageResponse queue(
			@Parameter(description = "Defaults to PENDING_REVIEW.") @RequestParam(required = false) SubmissionStatus status,
			@Parameter(description = "Optional category filter.") @RequestParam(required = false) Long categoryId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@Parameter(description = "submittedAt (default, oldest first) or updatedAt (newest first).") @RequestParam(required = false) String sort) {
		return reviewService.queue(status, categoryId, page, size, sort);
	}

	@Operation(summary = "Get one resource submission for review", description = "Not owner-scoped — any MODERATOR/ADMIN may view any submission. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The submission"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No submission exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{submissionId}")
	public ResourceSubmissionModerationDetailResponse detail(@PathVariable UUID submissionId) {
		return reviewService.detail(submissionId);
	}

	@Operation(summary = "Approve a resource submission", description = "Publishes it as a real, VERIFIED public resource in the same transaction. Requires a Bearer access token for a current MODERATOR or ADMIN who is not the submission's own submitter.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Approved; the submission's resultingResource is now populated"),
			@ApiResponse(responseCode = "400", description = "A missing/invalid reason, or the resulting resource would be invalid", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not a MODERATOR/ADMIN, or attempting to review one's own submission", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No submission exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already reviewed/withdrawn, or publishing would collide with an existing resource", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{submissionId}/approve")
	public ResourceSubmissionModerationDetailResponse approve(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID submissionId,
			@Valid @RequestBody ModerationDecisionRequest request) {
		return reviewService.approve(principal, submissionId, request);
	}

	@Operation(summary = "Reject a resource submission", description = "Never creates a resource. Requires a Bearer access token for a current MODERATOR or ADMIN who is not the submission's own submitter.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rejected"),
			@ApiResponse(responseCode = "400", description = "A missing or invalid reason", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not a MODERATOR/ADMIN, or attempting to review one's own submission", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No submission exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already reviewed or withdrawn", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{submissionId}/reject")
	public ResourceSubmissionModerationDetailResponse reject(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID submissionId,
			@Valid @RequestBody ModerationDecisionRequest request) {
		return reviewService.reject(principal, submissionId, request);
	}

	@Operation(summary = "Get one submission's moderation audit history", description = "Oldest first. Requires a Bearer access token for a current MODERATOR or ADMIN.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Audit event page, possibly empty"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{submissionId}/audit-events")
	public ModerationAuditEventPageResponse auditEvents(
			@PathVariable UUID submissionId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return auditQueryService.forContribution(ContributionType.RESOURCE_SUBMISSION, submissionId, page, size);
	}

}
