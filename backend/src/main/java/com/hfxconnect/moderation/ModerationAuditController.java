package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The global moderation audit trail (Milestone 9A) — MODERATOR/ADMIN only.
 * Never exposed publicly and never surfaced through any owner-facing route;
 * see ADR-016's "Audit API Scope" section.
 */
@Tag(name = "Moderation: Audit", description = "The global moderation audit trail. MODERATOR or ADMIN only.")
@RestController
@RequestMapping("/api/v1/moderation/audit-events")
public class ModerationAuditController {

	private final ModerationAuditQueryService auditQueryService;

	public ModerationAuditController(ModerationAuditQueryService auditQueryService) {
		this.auditQueryService = auditQueryService;
	}

	@Operation(summary = "List the global moderation audit trail", description = "Newest first. contributionType/decision are independently optional filters. Requires a Bearer access token for a current MODERATOR or ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Audit event page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page or size value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "The current account is not a MODERATOR or ADMIN", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public ModerationAuditEventPageResponse list(
			@Parameter(description = "Optional contribution-type filter.") @RequestParam(required = false) ContributionType contributionType,
			@Parameter(description = "Optional decision filter.") @RequestParam(required = false) ModerationDecision decision,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return auditQueryService.global(contributionType, decision, page, size);
	}

}
