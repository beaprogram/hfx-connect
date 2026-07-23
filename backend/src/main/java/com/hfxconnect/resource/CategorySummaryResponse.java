package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The category a resource belongs to, embedded as a small summary rather than the full category representation.")
public record CategorySummaryResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "Food Assistance") String name,
		@Schema(example = "food-assistance") String slug) {

	static CategorySummaryResponse from(ResourceDetails details) {
		return new CategorySummaryResponse(details.categoryId(), details.categoryName(), details.categorySlug());
	}

}
