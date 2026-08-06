package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ApiError;
import com.hfxconnect.correctionreport.CorrectionReportStatus;
import com.hfxconnect.correctionreport.IssueType;
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

/** The correction-report moderation queue and review decisions (Milestone 9A) — MODERATOR/ADMIN only. */
@Tag(name = "Moderation: Correction Reports", description = "Reviewing pending correction reports. MODERATOR or ADMIN only.")
@RestController
@RequestMapping("/api/v1/moderation/correction-reports")
public class ModerationCorrectionReportController {

	private final CorrectionReportReviewService reviewService;
	private final ModerationAuditQueryService auditQueryService;

	public ModerationCorrectionReportController(CorrectionReportReviewService reviewService,
			ModerationAuditQueryService auditQueryService) {
		this.reviewService = reviewService;
		this.auditQueryService = auditQueryService;
	}

	@Operation(summary = "List the correction-report moderation queue", description = "Defaults to status=PENDING_REVIEW, oldest submitted first. Requires a Bearer access token for a current MODERATOR or ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Queue page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public CorrectionReportQueuePageResponse queue(
			@Parameter(description = "Defaults to PENDING_REVIEW.") @RequestParam(required = false) CorrectionReportStatus status,
			@Parameter(description = "Optional issue-type filter.") @RequestParam(required = false) IssueType issueType,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@Parameter(description = "submittedAt (default, oldest first) or updatedAt (newest first).") @RequestParam(required = false) String sort) {
		return reviewService.queue(status, issueType, page, size, sort);
	}

	@Operation(summary = "Get one correction report for review", description = "Not owner-scoped — any MODERATOR/ADMIN may view any report, including the target resource's current live state. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The report"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No report exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{reportId}")
	public CorrectionReportModerationDetailResponse detail(@PathVariable UUID reportId) {
		return reviewService.detail(reportId);
	}

	@Operation(summary = "Approve a correction report", description = "applyProposedChanges/deactivateResource control what gets applied — see CorrectionApprovalRequest. Requires a Bearer access token for a current MODERATOR or ADMIN who is not the report's own reporter.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Approved"),
			@ApiResponse(responseCode = "400", description = "A missing/invalid reason, an invalid deactivation request, an unsupported application for this issue type, or the resulting resource would be invalid", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not a MODERATOR/ADMIN, or attempting to review one's own report", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No report exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already reviewed/withdrawn, or applying changes conflicts with existing data", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{reportId}/approve")
	public CorrectionReportModerationDetailResponse approve(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID reportId,
			@Valid @RequestBody CorrectionApprovalRequest request) {
		return reviewService.approve(principal, reportId, request);
	}

	@Operation(summary = "Reject a correction report", description = "Never modifies the target resource. Requires a Bearer access token for a current MODERATOR or ADMIN who is not the report's own reporter.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rejected"),
			@ApiResponse(responseCode = "400", description = "A missing or invalid reason", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Not a MODERATOR/ADMIN, or attempting to review one's own report", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No report exists with this id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Already reviewed or withdrawn", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{reportId}/reject")
	public CorrectionReportModerationDetailResponse reject(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID reportId,
			@Valid @RequestBody ModerationDecisionRequest request) {
		return reviewService.reject(principal, reportId, request);
	}

	@Operation(summary = "Get one report's moderation audit history", description = "Oldest first. Requires a Bearer access token for a current MODERATOR or ADMIN.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Audit event page, possibly empty"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{reportId}/audit-events")
	public ModerationAuditEventPageResponse auditEvents(
			@PathVariable UUID reportId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return auditQueryService.forContribution(ContributionType.CORRECTION_REPORT, reportId, page, size);
	}

}
