package com.hfxconnect.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Fields required to create a category. The slug is always derived from the name — see ADR-005 — and cannot be supplied directly.")
public record CategoryCreateRequest(

		@Schema(description = "Display name.", example = "Food Assistance")
		@NotBlank(message = "Category name is required.")
		@Size(max = 120, message = "Category name must be at most 120 characters.")
		String name,

		@Schema(description = "Optional longer description.", example = "Food banks, community meals, and grocery assistance programs.")
		@Size(max = 2000, message = "Category description must be at most 2000 characters.")
		String description) {

}
