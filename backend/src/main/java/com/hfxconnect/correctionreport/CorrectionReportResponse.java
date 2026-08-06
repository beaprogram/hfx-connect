package com.hfxconnect.correctionreport;

import com.hfxconnect.resource.CostType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A correction report belonging to the current user. Never includes another user's data, the reviewing moderator's identity, or any internal audit detail.")
public record CorrectionReportResponse(
		UUID id,
		CorrectionReportTargetResponse resource,
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
		Instant reviewedAt,
		String reviewReason,
		Boolean changesApplied) {

	static CorrectionReportResponse from(CorrectionReport report) {
		Boolean changesApplied = report.getStatus() == CorrectionReportStatus.APPROVED
				? report.getAppliedToResourceAt() != null
				: null;
		return new CorrectionReportResponse(
				report.getId(),
				CorrectionReportTargetResponse.from(report),
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
				report.getReviewedAt(),
				report.getReviewReason(),
				changesApplied);
	}

}
