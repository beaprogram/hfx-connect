package com.hfxconnect.resourcesubmission;

import com.hfxconnect.resource.CategorySummaryResponse;
import com.hfxconnect.resource.CostType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A resource submission belonging to the current user. Never includes another user's data or any internal review detail.")
public record ResourceSubmissionResponse(
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
		Instant withdrawnAt) {

	static ResourceSubmissionResponse from(ResourceSubmission submission) {
		var category = submission.getCategory();
		return new ResourceSubmissionResponse(
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
				submission.getWithdrawnAt());
	}

}
