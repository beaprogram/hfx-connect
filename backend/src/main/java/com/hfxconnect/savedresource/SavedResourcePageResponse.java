package com.hfxconnect.savedresource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the current user's saved, active resources, ordered by the requested sort.")
public record SavedResourcePageResponse(
		List<SavedResourceSummaryResponse> content,
		@Schema(example = "0") int page,
		@Schema(example = "20") int size,
		@Schema(example = "3") long totalElements,
		@Schema(example = "1") int totalPages) {
}
