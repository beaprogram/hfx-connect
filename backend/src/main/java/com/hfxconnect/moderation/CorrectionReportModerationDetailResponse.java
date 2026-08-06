package com.hfxconnect.moderation;

import com.hfxconnect.correctionreport.CorrectionReport;
import com.hfxconnect.correctionreport.CorrectionReportStatus;
import com.hfxconnect.correctionreport.CorrectionReportTargetResponse;
import com.hfxconnect.correctionreport.IssueType;
import com.hfxconnect.resource.CostType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** The full moderator-facing view of one correction report, including the target resource's current live state. */
@Schema(description = "The full moderator-facing view of one correction report.")
public record CorrectionReportModerationDetailResponse(
		UUID id,
		CorrectionReportTargetResponse resource,
		CurrentResourceStateResponse currentResource,
		IssueType issueType,
		String explanation,
		String proposedName,
		String proposedDescription,
		String proposedAddressLine1,
		String proposedAddressLine2,
		String proposedCity,
		String proposedProvince,
		String proposedPostalCode,
		String proposedPhone,
		String proposedEmail,
		String proposedWebsiteUrl,
		CostType proposedCostType,
		String proposedCostDetails,
		String proposedEligibility,
		CorrectionReportStatus status,
		Instant submittedAt,
		Instant updatedAt,
		Instant withdrawnAt,
		UUID reviewedByUserId,
		Instant reviewedAt,
		String reviewReason,
		Instant appliedToResourceAt) {

	static CorrectionReportModerationDetailResponse from(CorrectionReport report) {
		var resource = report.getResource();
		return new CorrectionReportModerationDetailResponse(
				report.getId(),
				CorrectionReportTargetResponse.from(report),
				resource == null ? null : CurrentResourceStateResponse.from(resource),
				report.getIssueType(),
				report.getExplanation(),
				report.getProposedName(),
				report.getProposedDescription(),
				report.getProposedAddressLine1(),
				report.getProposedAddressLine2(),
				report.getProposedCity(),
				report.getProposedProvince(),
				report.getProposedPostalCode(),
				report.getProposedPhone(),
				report.getProposedEmail(),
				report.getProposedWebsiteUrl(),
				report.getProposedCostType(),
				report.getProposedCostDetails(),
				report.getProposedEligibility(),
				report.getStatus(),
				report.getSubmittedAt(),
				report.getUpdatedAt(),
				report.getWithdrawnAt(),
				report.getReviewedByUserId(),
				report.getReviewedAt(),
				report.getReviewReason(),
				report.getAppliedToResourceAt());
	}

}
