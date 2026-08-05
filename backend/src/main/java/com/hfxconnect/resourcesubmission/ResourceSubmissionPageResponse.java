package com.hfxconnect.resourcesubmission;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the current user's resource submissions.")
public record ResourceSubmissionPageResponse(
		List<ResourceSubmissionResponse> content, int page, int size, long totalElements, int totalPages) {
}
