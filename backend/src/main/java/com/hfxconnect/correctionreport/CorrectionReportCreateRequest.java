package com.hfxconnect.correctionreport;

import com.hfxconnect.resource.CostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for reporting an issue with an existing resource (the
 * target resource id comes from the URL path, not this body). Every
 * proposed field is optional — a {@link IssueType#RESOURCE_CLOSED} or
 * {@link IssueType#OTHER} report may be explanation-only. There is
 * deliberately no field for {@code status} or {@code reportedByUserId}:
 * both are server-assigned.
 */
@Schema(description = "An issue report for an existing, active resource. Submitting this does not modify the resource — it creates a pending item visible only to you, awaiting review.")
public record CorrectionReportCreateRequest(

		@NotNull(message = "Issue type is required.")
		IssueType issueType,

		@Schema(example = "The phone number listed no longer works; I called and it's disconnected.")
		@NotBlank(message = "Explanation is required.")
		@Size(max = CorrectionReportValidation.EXPLANATION_MAX_LENGTH, message = "Explanation must be at most " + CorrectionReportValidation.EXPLANATION_MAX_LENGTH + " characters.")
		String explanation,

		@Schema(description = "Optional proposed corrected name.")
		String proposedName,

		@Schema(description = "Optional proposed corrected description.")
		String proposedDescription,

		String proposedAddressLine1,

		String proposedAddressLine2,

		String proposedCity,

		@Schema(description = "Two-letter Canadian province/territory code.")
		String proposedProvince,

		@Schema(description = "Canadian postal code.")
		String proposedPostalCode,

		String proposedPhone,

		String proposedEmail,

		String proposedWebsiteUrl,

		@Schema(description = "One of FREE, LOW_COST, PAID, UNKNOWN.")
		CostType proposedCostType,

		String proposedCostDetails,

		String proposedEligibility) {
}
