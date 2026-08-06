package com.hfxconnect.moderation;

import com.hfxconnect.resource.CategorySummaryResponse;
import com.hfxconnect.resourcesubmission.ResourceSubmission;
import com.hfxconnect.resourcesubmission.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One moderation-queue row — deliberately compact (see ADR-016's
 * "Performance" section: a queue page never returns full submission
 * payloads). Submitter identity is deliberately not included — a moderator
 * reviews content on its merits; see ADR-016's "Submitter Identity" section
 * for why this differs from an eventual reviewer-assignment feature.
 */
@Schema(description = "A compact resource-submission queue row.")
public record ResourceSubmissionQueueItemResponse(
		UUID id,
		CategorySummaryResponse category,
		String name,
		String shortDescription,
		SubmissionStatus status,
		Instant submittedAt) {

	static ResourceSubmissionQueueItemResponse from(ResourceSubmission submission) {
		var category = submission.getCategory();
		return new ResourceSubmissionQueueItemResponse(
				submission.getId(),
				new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug()),
				submission.getName(),
				submission.getShortDescription(),
				submission.getStatus(),
				submission.getSubmittedAt());
	}

}
