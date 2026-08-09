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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The current {@code ORGANIZATION} account's owned resources (Milestone 10A) — read-only. */
@Tag(name = "Organizations: Resources", description = "An ORGANIZATION account's own list of resources it currently owns.")
@RestController
@RequestMapping("/api/v1/organizations/me/resources")
public class OrganizationResourceController {

	private final OrganizationResourceService organizationResourceService;

	public OrganizationResourceController(OrganizationResourceService organizationResourceService) {
		this.organizationResourceService = organizationResourceService;
	}

	@Operation(summary = "List the current organization's owned resources", description = "Owner-scoped only, name ascending. Both active and inactive owned resources are included. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Resource page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "The current account has no organization profile yet", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public OwnedResourcePageResponse list(
			@AuthenticationPrincipal CurrentUserPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return organizationResourceService.list(principal.userId(), page, size);
	}

}
