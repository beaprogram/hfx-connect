package com.hfxconnect.correctionreport;

import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.resource.ResourceValidation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure normalization and format validation for a correction report,
 * independent of persistence or resource lookup (which
 * {@link CorrectionReportService} handles). Every proposed field is
 * optional by design — a report such as {@link IssueType#RESOURCE_CLOSED}
 * or {@link IssueType#OTHER} may be explanation-only — but a proposed field
 * that <em>is</em> supplied must pass the identical format rules a real
 * resource field would, reusing {@link ResourceValidation}'s helpers
 * directly rather than duplicating them (Milestone 8B — see that class's
 * own Javadoc).
 */
final class CorrectionReportValidation {

	static final int EXPLANATION_MAX_LENGTH = 2000;
	static final int PROPOSED_NAME_MAX_LENGTH = ResourceValidation.NAME_MAX_LENGTH;
	static final int PROPOSED_DESCRIPTION_MAX_LENGTH = ResourceValidation.DESCRIPTION_MAX_LENGTH;

	private CorrectionReportValidation() {
	}

	record Normalized(
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
			String proposedCostDetails,
			String proposedEligibility) {
	}

	static Normalized validate(
			String explanation, String proposedName, String proposedDescription, String proposedAddressLine1,
			String proposedAddressLine2, String proposedCity, String proposedProvince, String proposedPostalCode,
			String proposedPhone, String proposedEmail, String proposedWebsiteUrl, String proposedCostDetails,
			String proposedEligibility) {

		Map<String, String> errors = new LinkedHashMap<>();

		String normalizedExplanation = ResourceValidation.requireBounded(
				errors, "explanation", explanation, EXPLANATION_MAX_LENGTH, "Explanation");

		String normalizedProposedName = ResourceValidation.optionalBounded(
				errors, "proposedName", proposedName, PROPOSED_NAME_MAX_LENGTH, "Proposed name");
		String normalizedProposedDescription = ResourceValidation.optionalBounded(
				errors, "proposedDescription", proposedDescription, PROPOSED_DESCRIPTION_MAX_LENGTH, "Proposed description");
		String normalizedProposedAddressLine1 = ResourceValidation.optionalBounded(
				errors, "proposedAddressLine1", proposedAddressLine1, ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Proposed address line 1");
		String normalizedProposedAddressLine2 = ResourceValidation.optionalBounded(
				errors, "proposedAddressLine2", proposedAddressLine2, ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Proposed address line 2");
		String normalizedProposedCity = ResourceValidation.optionalBounded(
				errors, "proposedCity", proposedCity, ResourceValidation.CITY_MAX_LENGTH, "Proposed city");
		String normalizedProposedProvince = blank(proposedProvince) ? null : ResourceValidation.validateProvince(errors, proposedProvince);
		String normalizedProposedPostalCode = blank(proposedPostalCode) ? null : ResourceValidation.validatePostalCode(errors, proposedPostalCode);
		String normalizedProposedPhone = ResourceValidation.validatePhone(errors, proposedPhone);
		String normalizedProposedEmail = ResourceValidation.validateEmail(errors, proposedEmail);
		String normalizedProposedWebsiteUrl = ResourceValidation.validateWebsiteUrl(errors, proposedWebsiteUrl);
		String normalizedProposedCostDetails = ResourceValidation.optionalBounded(
				errors, "proposedCostDetails", proposedCostDetails, ResourceValidation.COST_DETAILS_MAX_LENGTH, "Proposed cost details");
		String normalizedProposedEligibility = ResourceValidation.optionalBounded(
				errors, "proposedEligibility", proposedEligibility, ResourceValidation.ELIGIBILITY_MAX_LENGTH, "Proposed eligibility");

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted correction report contains invalid information.", errors);
		}

		return new Normalized(normalizedExplanation, normalizedProposedName, normalizedProposedDescription,
				normalizedProposedAddressLine1, normalizedProposedAddressLine2, normalizedProposedCity,
				normalizedProposedProvince, normalizedProposedPostalCode, normalizedProposedPhone,
				normalizedProposedEmail, normalizedProposedWebsiteUrl, normalizedProposedCostDetails,
				normalizedProposedEligibility);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
