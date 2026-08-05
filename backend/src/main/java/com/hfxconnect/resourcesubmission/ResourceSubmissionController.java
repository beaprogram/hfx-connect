package com.hfxconnect.resourcesubmission;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current authenticated user's proposed resource submissions (Milestone
 * 8B). Every endpoint is scoped to the caller's own account only — the user
 * id always comes from {@link CurrentUserPrincipal}, never from a path,
 * query, or body parameter, matching {@code SavedResourceController}'s
 * established pattern (Milestone 8A).
 *
 * <p>Not registered in {@code SecurityConfig}'s explicit matcher list: the
 * chain's final {@code anyRequest().authenticated()} rule already requires
 * a valid, {@code ACTIVE}-account Bearer token for every route this class
 * maps, and any authenticated role ({@code USER}/{@code ORGANIZATION}/
 * {@code MODERATOR}/{@code ADMIN}) may submit a resource proposal — there is
 * no role restriction to add.
 */
@Tag(name = "Resource Submissions", description = "The current authenticated user's proposed new resources. Submitting does not create a public resource — every submission starts PENDING_REVIEW and is visible only to its owner until Milestone 9's moderation workflow reviews it.")
@RestController
@RequestMapping("/api/v1/users/me/resource-submissions")
public class ResourceSubmissionController {

	private final ResourceSubmissionService submissionService;

	public ResourceSubmissionController(ResourceSubmissionService submissionService) {
		this.submissionService = submissionService;
	}

	@Operation(summary = "Propose a new resource", description = "Creates a PENDING_REVIEW submission owned by the current user. Does not create a public resource and never appears in public search, list, map, or nearby results. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The submission was created and is pending review"),
			@ApiResponse(responseCode = "400", description = "Validation failure, an invalid category, or an inactive category", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No category exists with the given id", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "The current user already has a pending submission for this category and name", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping
	public ResponseEntity<ResourceSubmissionResponse> create(
			@AuthenticationPrincipal CurrentUserPrincipal principal,
			@Valid @RequestBody ResourceSubmissionCreateRequest request) {
		ResourceSubmissionResponse response = submissionService.create(principal.userId(), request);
		return ResponseEntity.created(URI.create("/api/v1/users/me/resource-submissions/" + response.id())).body(response);
	}

	@Operation(summary = "List the current user's resource submissions", description = "Paginated, owner-scoped only. Ordered by submittedAt descending by default. Page size is capped at " + ResourceSubmissionService.MAX_PAGE_SIZE + ". Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Submission page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public ResourceSubmissionPageResponse list(
			@AuthenticationPrincipal CurrentUserPrincipal principal,
			@Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, 1-" + ResourceSubmissionService.MAX_PAGE_SIZE + ".") @RequestParam(defaultValue = "20") int size,
			@Parameter(description = "One of: submittedAt (default, newest first), updatedAt, status.") @RequestParam(required = false) String sort) {
		return submissionService.list(principal.userId(), page, size, sort);
	}

	@Operation(summary = "Get one of the current user's resource submissions", description = "Returns 404 for a submission id that doesn't exist or that belongs to a different account — the two cases are indistinguishable to the caller. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The owned submission"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No submission with this id is owned by the current user", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{submissionId}")
	public ResourceSubmissionResponse get(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID submissionId) {
		return submissionService.get(principal.userId(), submissionId);
	}

	@Operation(summary = "Withdraw a pending resource submission", description = "Only legal while the submission is still PENDING_REVIEW. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The withdrawn submission"),
			@ApiResponse(responseCode = "400", description = "The submission is no longer PENDING_REVIEW", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No submission with this id is owned by the current user", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/{submissionId}/withdraw")
	public ResourceSubmissionResponse withdraw(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID submissionId) {
		return submissionService.withdraw(principal.userId(), submissionId);
	}

}
