package com.hfxconnect.savedresource;

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
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current authenticated user's saved resources (Milestone 8A). Every
 * endpoint here is scoped to the caller's own account only — the user id
 * always comes from {@link CurrentUserPrincipal} (the authenticated Bearer
 * token, see ADR-009), never from a path/query/body parameter. There is no
 * endpoint anywhere that accepts an arbitrary user id.
 *
 * <p>Not registered in {@code SecurityConfig}'s explicit matcher list: the
 * chain's final {@code anyRequest().authenticated()} rule already requires a
 * valid, {@code ACTIVE}-account Bearer token for every route this class
 * maps, and any authenticated role ({@code USER}/{@code ORGANIZATION}/
 * {@code MODERATOR}/{@code ADMIN}) is permitted to save resources — there is
 * no role restriction to add.
 */
@Tag(name = "Saved Resources", description = "The current authenticated user's saved resources. Every endpoint requires a Bearer access token and is scoped to the caller's own account only.")
@RestController
@RequestMapping("/api/v1/users/me/saved-resources")
public class SavedResourceController {

	private final SavedResourceService savedResourceService;

	public SavedResourceController(SavedResourceService savedResourceService) {
		this.savedResourceService = savedResourceService;
	}

	@Operation(summary = "Save a resource", description = "Ensures the given active resource is saved for the current user. Idempotent: calling this again for an already-saved resource succeeds without creating a duplicate. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "The resource is saved (whether this call created the relation or it already existed)"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No active resource exists with the given id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PutMapping("/{resourceId}")
	public ResponseEntity<Void> save(@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID resourceId) {
		savedResourceService.save(principal.userId(), resourceId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Remove a saved resource", description = "Ensures the given resource is no longer saved for the current user. Idempotent: succeeds even if the resource was never saved, or is no longer active. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "The resource is not saved (whether this call removed the relation or it was already absent)"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@DeleteMapping("/{resourceId}")
	public ResponseEntity<Void> remove(@AuthenticationPrincipal CurrentUserPrincipal principal, @PathVariable UUID resourceId) {
		savedResourceService.remove(principal.userId(), resourceId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "List the current user's saved resources", description = "Paginated, active resources only — a saved resource that has since been deactivated is omitted here (and reappears if it becomes active again). Ordered by savedAt descending by default. Page size is capped at " + SavedResourceService.MAX_PAGE_SIZE + ". Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Saved-resource page, possibly empty"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public SavedResourcePageResponse list(
			@AuthenticationPrincipal CurrentUserPrincipal principal,
			@Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, 1-" + SavedResourceService.MAX_PAGE_SIZE + ".") @RequestParam(defaultValue = "20") int size,
			@Parameter(description = "One of: savedAt (default, newest first), name (resource name, ascending).") @RequestParam(required = false) String sort) {
		return savedResourceService.list(principal.userId(), page, size, sort);
	}

	@Operation(summary = "Check saved status for a batch of resource ids", description = "Returns only which of the supplied resourceIds the current user has saved — one request for a whole page of resource cards, rather than one request per card. At most " + SavedResourceValidation.MAX_STATUS_IDS + " ids; duplicates are normalized. Requires a Bearer access token.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The subset of resourceIds the current user has saved"),
			@ApiResponse(responseCode = "400", description = "Missing resourceIds, a null entry, or more than " + SavedResourceValidation.MAX_STATUS_IDS + " distinct ids", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping("/status")
	public SavedResourceStatusResponse status(
			@AuthenticationPrincipal CurrentUserPrincipal principal, @RequestBody SavedResourceStatusRequest request) {
		return savedResourceService.status(principal.userId(), request);
	}

}
