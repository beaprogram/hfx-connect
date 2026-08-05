package com.hfxconnect.correctionreport;

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
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reporting an issue on an existing resource, and the current authenticated
 * user's own correction reports (Milestone 8B). The creation endpoint is
 * nested under {@code /api/v1/resources/{resourceId}} (the target is a URL
 * path segment, not request-body data); listing and detail are current-
 * user-scoped under {@code /api/v1/users/me/correction-reports}, matching
 * {@code ResourceSubmissionController}'s and
 * {@code SavedResourceController}'s established ownership pattern.
 *
 * <p>Not registered in {@code SecurityConfig}'s explicit matcher list — the
 * chain's final {@code anyRequest().authenticated()} rule already requires
 * a valid, {@code ACTIVE}-account Bearer token for every route this class
 * maps, and any authenticated role may report an issue.
 */
@Tag(name = "Correction Reports", description = "Reporting an issue on an existing resource, and the current authenticated user's own reports. Submitting does not modify the target resource — every report starts PENDING_REVIEW and is visible only to its owner until Milestone 9's moderation workflow reviews it.")
@RestController
public class CorrectionReportController {

	private final CorrectionReportService reportService;

	public CorrectionReportController(CorrectionReportService reportService) {
		this.reportService = reportService;
	}

	@Operation(summary = "Report an issue with a resource", description = "Creates a PENDING_REVIEW correction report owned by the current user against an active resource. Never modifies the target resource. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The report was created and is pending review"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No active resource exists with the given id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "The current user already has a pending report for this resource and issue type", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/api/v1/resources/{resourceId}/correction-reports")
	public ResponseEntity<CorrectionReportResponse> create(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID resourceId,
			@Valid @RequestBody CorrectionReportCreateRequest request) {
		CorrectionReportResponse response = reportService.create(principal.userId(), resourceId, request);
		return ResponseEntity.created(URI.create("/api/v1/users/me/correction-reports/" + response.id())).body(response);
	}

	@Operation(summary = "List the current user's correction reports", description = "Paginated, owner-scoped only. Ordered by submittedAt descending by default. Page size is capped at " + CorrectionReportService.MAX_PAGE_SIZE + ". Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Report page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/api/v1/users/me/correction-reports")
	public CorrectionReportPageResponse list(
			@AuthenticationPrincipal CurrentUserPrincipal principal,
			@Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, 1-" + CorrectionReportService.MAX_PAGE_SIZE + ".") @RequestParam(defaultValue = "20") int size,
			@Parameter(description = "One of: submittedAt (default, newest first), updatedAt, status.") @RequestParam(required = false) String sort) {
		return reportService.list(principal.userId(), page, size, sort);
	}

	@Operation(summary = "Get one of the current user's correction reports", description = "Returns 404 for a report id that doesn't exist or that belongs to a different account. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The owned report"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No report with this id is owned by the current user", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/api/v1/users/me/correction-reports/{reportId}")
	public CorrectionReportResponse get(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID reportId) {
		return reportService.get(principal.userId(), reportId);
	}

	@Operation(summary = "Withdraw a pending correction report", description = "Only legal while the report is still PENDING_REVIEW. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The withdrawn report"),
			@ApiResponse(responseCode = "400", description = "The report is no longer PENDING_REVIEW", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No report with this id is owned by the current user", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/api/v1/users/me/correction-reports/{reportId}/withdraw")
	public CorrectionReportResponse withdraw(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID reportId) {
		return reportService.withdraw(principal.userId(), reportId);
	}

}
