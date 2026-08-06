package com.hfxconnect.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the resource-submission moderation queue.")
public record ResourceSubmissionQueuePageResponse(
		List<ResourceSubmissionQueueItemResponse> content, int page, int size, long totalElements, int totalPages) {
}
