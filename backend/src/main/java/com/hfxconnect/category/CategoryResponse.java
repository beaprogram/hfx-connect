package com.hfxconnect.category;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A category, as returned by the API. Note there is no normalizedName field — it is an internal uniqueness-checking detail, not public data.")
public record CategoryResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "Food Assistance") String name,
		@Schema(example = "food-assistance") String slug,
		@Schema(example = "Food banks, community meals, and grocery assistance programs.") String description,
		@Schema(example = "true") boolean active,
		Instant createdAt,
		Instant updatedAt) {

	static CategoryResponse from(Category category) {
		return new CategoryResponse(
				category.getId(),
				category.getName(),
				category.getSlug(),
				category.getDescription(),
				category.isActive(),
				category.getCreatedAt(),
				category.getUpdatedAt());
	}

}
