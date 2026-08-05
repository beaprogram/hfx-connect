package com.hfxconnect.correctionreport;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the current user's correction reports.")
public record CorrectionReportPageResponse(
		List<CorrectionReportResponse> content, int page, int size, long totalElements, int totalPages) {
}
