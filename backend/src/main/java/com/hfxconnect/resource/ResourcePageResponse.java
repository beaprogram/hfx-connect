package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of resource summaries. An explicit shape rather than Spring's raw Page serialization — see CategoryPageResponse for the established precedent.")
public record ResourcePageResponse(
		List<ResourceSummaryResponse> content,
		@Schema(example = "0") int page,
		@Schema(example = "20") int size,
		@Schema(example = "6") long totalElements,
		@Schema(example = "1") int totalPages) {

	static ResourcePageResponse from(ResourcePage page) {
		return new ResourcePageResponse(
				page.content().stream().map(ResourceSummaryResponse::from).toList(),
				page.page(),
				page.size(),
				page.totalElements(),
				page.totalPages());
	}

}
