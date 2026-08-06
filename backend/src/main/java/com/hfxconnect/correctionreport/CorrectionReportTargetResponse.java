package com.hfxconnect.correctionreport;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "The resource this report is about. name/slug always come from a snapshot captured when the report was created, not a live read of the resource — they remain meaningful even if the resource is later deleted. resourceId is null if the target resource has since been deleted.")
public record CorrectionReportTargetResponse(
		@Schema(description = "Null if the target resource has since been deleted.") UUID resourceId,
		String name,
		String slug) {

	/** {@code public} as of Milestone 9A — also used by {@code com.hfxconnect.moderation}'s queue/detail responses. */
	public static CorrectionReportTargetResponse from(CorrectionReport report) {
		var resource = report.getResource();
		return new CorrectionReportTargetResponse(
				resource == null ? null : resource.getId(),
				report.getResourceNameSnapshot(),
				report.getResourceSlugSnapshot());
	}

}
