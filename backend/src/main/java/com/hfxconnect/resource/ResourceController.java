package com.hfxconnect.resource;

import com.hfxconnect.common.error.ApiError;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The public Resource API.
 *
 * <p>Only active resources are ever visible through this controller — a
 * deactivated resource returns {@code 404} from every read endpoint here,
 * the same way a genuinely nonexistent one would (see
 * {@code ResourceService}'s {@code getActive*}/{@code listActive*} methods).
 * There is no way to list or fetch inactive resources publicly yet; that is
 * an administrative capability that needs the authorization Milestone 5C
 * introduces, not something this unsecured milestone should expose.
 *
 * <p>{@code POST} requires a Bearer access token belonging to an
 * {@code ADMIN} or {@code MODERATOR} account (Milestone 5C — see ADR-009 and
 * {@code SecurityConfig}). {@code ORGANIZATION} accounts cannot create
 * resources yet — organization ownership/verification doesn't exist (see
 * ADR-009); that is Milestone 10's concern.
 *
 * <p>There is deliberately no general update or delete endpoint here — see
 * {@code docs/milestones/milestone-03c-public-resource-api.md} for why
 * {@code ResourceService.update}/{@code deactivate} (both already
 * implemented and tested since Milestone 3B) are not exposed over HTTP yet.
 * The one exception is {@code PUT /{id}/operating-hours} (Milestone 6B — see
 * ADR-011), a narrow, focused endpoint for replacing a resource's weekly
 * schedule, gated by the same {@code ADMIN}/{@code MODERATOR} pairing as
 * {@code POST}.
 */
@Tag(name = "Resources", description = "Public resource directory (list/filter/search/read). POST and PUT /{id}/operating-hours require an ADMIN or MODERATOR access token; only active resources are ever visible — see class-level Javadoc.")
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

	private final ResourceService resourceService;

	public ResourceController(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	@Operation(summary = "Create a resource", description = "Must reference an existing, active category. The slug is derived from the name. New resources always start UNVERIFIED. Requires a Bearer access token for an ADMIN or MODERATOR account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Resource created"),
			@ApiResponse(responseCode = "400", description = "Validation failure, or the category is inactive", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Authenticated, but not an ADMIN or MODERATOR account", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No category exists with the given categoryId", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "A resource with this (derived) slug already exists", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping
	public ResponseEntity<ResourceResponse> create(@Valid @RequestBody ResourceCreateRequest request) {
		ResourceDetails details = resourceService.create(request.toCommand());
		ResourceResponse response = ResourceResponse.from(details);
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/v1/resources/{id}")
				.buildAndExpand(response.id())
				.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@Operation(summary = "Get an active resource by ID", description = "Returns 404 for a deactivated resource, the same as for an unknown ID.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Resource found"),
			@ApiResponse(responseCode = "404", description = "No active resource exists with that ID", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{id}")
	public ResourceResponse getById(@PathVariable UUID id) {
		return ResourceResponse.from(resourceService.getActiveById(id));
	}

	@Operation(summary = "Get an active resource by slug", description = "Returns 404 for a deactivated resource, the same as for an unknown slug.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Resource found"),
			@ApiResponse(responseCode = "404", description = "No active resource exists with that slug", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/slug/{slug}")
	public ResourceResponse getBySlug(@PathVariable String slug) {
		return ResourceResponse.from(resourceService.getActiveBySlug(slug));
	}

	@Operation(summary = "List (and optionally search/filter) active resources", description = "Paginated. Always active-only — there is no way to include inactive resources in this public listing yet (that needs Milestone 5C authorization). Page size is capped at " + ResourceService.MAX_PAGE_SIZE + ". sort defaults to name ascending; sort=createdAt sorts newest-first. q performs a case-insensitive substring match across name, description, addressLine1, and city (Milestone 6A) — see ADR-010. A blank q is treated as no keyword filter; results are not relevance-ranked. costType/verificationStatus/openNow (Milestone 6B, see ADR-011) are all independently optional and combine with q/categoryId. openNow is evaluated in the America/Halifax timezone; resources with no operating-hours schedule (UNKNOWN status) are never returned when openNow=true.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Resource page"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, sort, q, costType, verificationStatus, or openNow value", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public ResourcePageResponse list(
			@Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, 1-" + ResourceService.MAX_PAGE_SIZE + ".") @RequestParam(defaultValue = "20") int size,
			@Parameter(description = "Optional category filter.") @RequestParam(required = false) Long categoryId,
			@Parameter(description = "One of: name (default, ascending), createdAt (newest first).") @RequestParam(required = false) String sort,
			@Parameter(description = "Optional keyword search. Case-insensitive substring match across name/description/addressLine1/city; at most " + ResourceSearchQuery.MAX_LENGTH + " characters after normalization; blank is treated as no filter.") @RequestParam(required = false) String q,
			@Parameter(description = "Optional exact cost-type filter: FREE, LOW_COST, PAID, or UNKNOWN.") @RequestParam(required = false) String costType,
			@Parameter(description = "Optional exact verification-status filter: UNVERIFIED or VERIFIED.") @RequestParam(required = false) String verificationStatus,
			@Parameter(description = "Optional 'true'/'false'. true returns only resources currently OPEN (America/Halifax). Missing, blank, or false apply no filter.") @RequestParam(required = false) String openNow) {
		return ResourcePageResponse.from(
				resourceService.search(q, categoryId, costType, verificationStatus, openNow, page, size, sort));
	}

	@Operation(summary = "Replace a resource's weekly operating hours", description = "Fully replaces the resource's entire weekly schedule (delete-then-insert, one transaction) — no partial update. At most one entry per day, no duplicate days; a day with no entry has no schedule for that day. Requires a Bearer access token for an ADMIN or MODERATOR account. See ADR-011.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated schedule"),
			@ApiResponse(responseCode = "400", description = "Invalid schedule (duplicate day, closed day with times, open day missing a time, equal opensAt/closesAt, more than 7 entries)", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Authenticated, but not an ADMIN or MODERATOR account", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "No resource exists with the given id", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PutMapping("/{id}/operating-hours")
	public OperatingHoursResponse replaceOperatingHours(
			@PathVariable UUID id, @RequestBody ReplaceOperatingHoursRequest request) {
		return resourceService.replaceOperatingHours(id, request);
	}

}
