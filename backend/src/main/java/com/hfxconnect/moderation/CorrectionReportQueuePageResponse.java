package com.hfxconnect.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the correction-report moderation queue.")
public record CorrectionReportQueuePageResponse(
		List<CorrectionReportQueueItemResponse> content, int page, int size, long totalElements, int totalPages) {
}
