package com.hfxconnect.category;

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
 * <p><strong>Temporary security limitation:</strong> write endpoints
 * ({@code POST}) are not protected yet. Authentication and role-based
 * authorization are introduced in Milestone 5C; until then, anyone who can
 * reach this API can create categories. This is a known, deliberate,
 * documented limitation of this milestone — not an oversight — and is
 * restated in {@code docs/milestones/milestone-03a-category-domain.md} and
 * the root README's known limitations.
 */
@Tag(name = "Categories", description = "Category management. POST is temporarily unsecured — see class-level Javadoc and the project's known limitations.")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@Operation(summary = "Create a category", description = "The slug is always derived from the name and cannot be supplied directly. Not protected by authentication yet (Milestone 5C).")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Category created"),
			@ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ApiError.class))),
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
