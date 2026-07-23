package com.hfxconnect.resource;

import com.hfxconnect.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * an administrative capability that needs the authorization Milestone 5
 * introduces, not something this unsecured milestone should expose.
 *
 * <p><strong>Temporary security limitation:</strong> {@code POST} is not
 * protected yet — anyone who can reach this API can create a resource. This
 * mirrors {@code CategoryController}'s exact same documented limitation and
 * is resolved the same way, in Milestone 5.
 *
 * <p>There is deliberately no update or delete endpoint here — see
 * {@code docs/milestones/milestone-03c-public-resource-api.md} for why
 * {@code ResourceService.update}/{@code deactivate} (both already
 * implemented and tested since Milestone 3B) are not exposed over HTTP yet.
 */
@Tag(name = "Resources", description = "Public resource directory. POST is temporarily unsecured; only active resources are ever visible — see class-level Javadoc.")
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

	private final ResourceService resourceService;

	public ResourceController(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	@Operation(summary = "Create a resource", description = "Must reference an existing, active category. The slug is derived from the name. New resources always start UNVERIFIED. Not protected by authentication yet (Milestone 5).")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Resource created"),
			@ApiResponse(responseCode = "400", description = "Validation failure, or the category is inactive", content = @Content(schema = @Schema(implementation = ApiError.class))),
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

	@Operation(summary = "List active resources", description = "Paginated. Always active-only — there is no way to include inactive resources in this public listing yet (that needs Milestone 5 authorization). Page size is capped at " + ResourceService.MAX_PAGE_SIZE + ". sort defaults to name ascending; sort=createdAt sorts newest-first.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Resource page"),
			@ApiResponse(responseCode = "400", description = "Invalid page, size, or sort value", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public ResourcePageResponse list(
			@Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, 1-" + ResourceService.MAX_PAGE_SIZE + ".") @RequestParam(defaultValue = "20") int size,
			@Parameter(description = "Optional category filter.") @RequestParam(required = false) Long categoryId,
			@Parameter(description = "One of: name (default, ascending), createdAt (newest first).") @RequestParam(required = false) String sort) {
		return ResourcePageResponse.from(categoryId == null
				? resourceService.listActive(page, size, sort)
				: resourceService.listActiveByCategory(categoryId, page, size, sort));
	}

}
