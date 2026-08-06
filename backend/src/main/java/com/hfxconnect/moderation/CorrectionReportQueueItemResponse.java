package com.hfxconnect.moderation;

import com.hfxconnect.correctionreport.CorrectionReport;
import com.hfxconnect.correctionreport.CorrectionReportStatus;
import com.hfxconnect.correctionreport.CorrectionReportTargetResponse;
import com.hfxconnect.correctionreport.IssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One moderation-queue row for correction reports — deliberately compact, reporter identity deliberately absent. */
@Schema(description = "A compact correction-report queue row.")
public record CorrectionReportQueueItemResponse(
		UUID id,
		CorrectionReportTargetResponse resource,
		IssueType issueType,
		String explanationPreview,
		CorrectionReportStatus status,
		Instant submittedAt) {

	private static final int EXPLANATION_PREVIEW_LENGTH = 140;

	static CorrectionReportQueueItemResponse from(CorrectionReport report) {
		return new CorrectionReportQueueItemResponse(
				report.getId(),
				CorrectionReportTargetResponse.from(report),
				report.getIssueType(),
				preview(report.getExplanation()),
				report.getStatus(),
				report.getSubmittedAt());
	}

	private static String preview(String explanation) {
		if (explanation.length() <= EXPLANATION_PREVIEW_LENGTH) {
			return explanation;
		}
		return explanation.substring(0, EXPLANATION_PREVIEW_LENGTH).stripTrailing() + "…";
	}

}
