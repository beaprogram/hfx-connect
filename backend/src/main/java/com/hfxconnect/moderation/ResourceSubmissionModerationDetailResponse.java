package com.hfxconnect.moderation;

import com.hfxconnect.resource.CategorySummaryResponse;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resourcesubmission.ResourceSubmission;
import com.hfxconnect.resourcesubmission.ResultingResourceSummaryResponse;
import com.hfxconnect.resourcesubmission.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The full moderator-facing view of one resource submission. {@code
 * reviewedByUserId} is a raw account id (not resolved to an email) — see
 * ADR-016's "Reviewer Identity" section: a moderator wanting a colleague's
 * human-readable identity uses the linked audit-events endpoint, whose
 * entries already carry a point-in-time {@code actorEmail} snapshot; this
 * detail response does not duplicate that lookup. Submitter identity is
 * deliberately absent, the same reasoning as the queue row.
 */
@Schema(description = "The full moderator-facing view of one resource submission.")
public record ResourceSubmissionModerationDetailResponse(
		UUID id,
		CategorySummaryResponse category,
		String name,
		String shortDescription,
		String fullDescription,
		String addressLine1,
		String addressLine2,
		String city,
		String province,
		String postalCode,
		String phone,
		String email,
		String websiteUrl,
		CostType costType,
		String eligibilityInformation,
		String accessibilityInformation,
		SubmissionStatus status,
		Instant submittedAt,
		Instant updatedAt,
		Instant withdrawnAt,
		UUID reviewedByUserId,
		Instant reviewedAt,
		String reviewReason,
		ResultingResourceSummaryResponse resultingResource) {

	static ResourceSubmissionModerationDetailResponse from(ResourceSubmission submission) {
		var category = submission.getCategory();
		ResultingResourceSummaryResponse resultingResource = submission.getResultingResourceId() == null ? null
				: new ResultingResourceSummaryResponse(submission.getResultingResourceId(),
						submission.getResultingResourceName(), submission.getResultingResourceSlug());
		return new ResourceSubmissionModerationDetailResponse(
				submission.getId(),
				new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug()),
				submission.getName(),
				submission.getShortDescription(),
				submission.getFullDescription(),
				submission.getAddressLine1(),
				submission.getAddressLine2(),
				submission.getCity(),
				submission.getProvince(),
				submission.getPostalCode(),
				submission.getPhone(),
				submission.getEmail(),
				submission.getWebsiteUrl(),
				submission.getCostType(),
				submission.getEligibilityInformation(),
				submission.getAccessibilityInformation(),
				submission.getStatus(),
				submission.getSubmittedAt(),
				submission.getUpdatedAt(),
				submission.getWithdrawnAt(),
				submission.getReviewedByUserId(),
				submission.getReviewedAt(),
				submission.getReviewReason(),
				resultingResource);
	}

}
