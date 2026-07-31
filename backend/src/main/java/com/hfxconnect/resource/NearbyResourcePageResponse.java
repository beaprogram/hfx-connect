package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of nearby-search results, ordered nearest first.")
public record NearbyResourcePageResponse(
		List<NearbyResourceSummaryResponse> content,
		@Schema(example = "0") int page,
		@Schema(example = "20") int size,
		@Schema(example = "3") long totalElements,
		@Schema(example = "1") int totalPages) {

	static NearbyResourcePageResponse from(NearbyResourcePage page) {
		return new NearbyResourcePageResponse(
				page.content().stream().map(NearbyResourceSummaryResponse::from).toList(),
				page.page(),
				page.size(),
				page.totalElements(),
				page.totalPages());
	}

}
