package com.hfxconnect.moderation;

import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The target resource's current live field values, shown alongside a
 * correction report's proposed values so a moderator can compare before and
 * proposed without a separate lookup. {@code null} on the containing
 * response when the target resource has since been deleted (see {@code
 * CorrectionReportTargetResponse.resourceId}).
 */
@Schema(description = "The target resource's current values, for comparison against the report's proposed values.")
public record CurrentResourceStateResponse(
		UUID id,
		String name,
		String description,
		String addressLine1,
		String addressLine2,
		String city,
		String province,
		String postalCode,
		String phone,
		String email,
		String websiteUrl,
		CostType costType,
		String costDetails,
		String eligibility,
		boolean active) {

	static CurrentResourceStateResponse from(CommunityResource resource) {
		return new CurrentResourceStateResponse(
				resource.getId(),
				resource.getName(),
				resource.getDescription(),
				resource.getAddressLine1(),
				resource.getAddressLine2(),
				resource.getCity(),
				resource.getProvince(),
				resource.getPostalCode(),
				resource.getPhone(),
				resource.getEmail(),
				resource.getWebsiteUrl(),
				resource.getCostType(),
				resource.getCostDetails(),
				resource.getEligibility(),
				resource.isActive());
	}

}
