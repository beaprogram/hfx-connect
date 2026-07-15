package com.hfxconnect.category;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "A page of categories. An explicit shape rather than Spring's raw Page serialization, so the API contract is stable and self-documenting.")
public record CategoryPageResponse(
		List<CategoryResponse> content,
		@Schema(example = "0") int page,
		@Schema(example = "20") int size,
		@Schema(example = "6") long totalElements,
		@Schema(example = "1") int totalPages) {

	static CategoryPageResponse from(Page<Category> page) {
		return new CategoryPageResponse(
				page.getContent().stream().map(CategoryResponse::from).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
