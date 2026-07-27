package com.hfxconnect.category;

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
 * Category management.
 *
 * <p>{@code POST} requires a Bearer access token belonging to an
 * {@code ADMIN} account (Milestone 5C — see ADR-009 and
 * {@code SecurityConfig}). All {@code GET} endpoints remain fully public.
 */
@Tag(name = "Categories", description = "Category management. POST requires an ADMIN access token; GET is public.")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@Operation(summary = "Create a category", description = "The slug is always derived from the name and cannot be supplied directly. Requires a Bearer access token for an ADMIN account.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Category created"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "403", description = "Authenticated, but not an ADMIN account", content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "A category with this name or slug already exists", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@PostMapping
	public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
		CategoryResponse response = categoryService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/v1/categories/{id}")
				.buildAndExpand(response.id())
				.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@Operation(summary = "Get a category by ID")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Category found"),
			@ApiResponse(responseCode = "404", description = "No category exists with that ID", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/{id}")
	public CategoryResponse getById(@PathVariable Long id) {
		return categoryService.getById(id);
	}

	@Operation(summary = "Get a category by slug")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Category found"),
			@ApiResponse(responseCode = "404", description = "No category exists with that slug", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping("/slug/{slug}")
	public CategoryResponse getBySlug(@PathVariable String slug) {
		return categoryService.getBySlug(slug);
	}

	@Operation(summary = "List categories", description = "Paginated, sorted by name ascending. Page size is capped at " + CategoryService.MAX_PAGE_SIZE + ".")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Category page"),
			@ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	@GetMapping
	public CategoryPageResponse list(
			@Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size, 1-" + CategoryService.MAX_PAGE_SIZE + ".") @RequestParam(defaultValue = "" + CategoryService.DEFAULT_PAGE_SIZE) int size,
			@Parameter(description = "Optional active-state filter; omit to return categories in any state.") @RequestParam(required = false) Boolean active) {
		return categoryService.list(page, size, active);
	}

}
