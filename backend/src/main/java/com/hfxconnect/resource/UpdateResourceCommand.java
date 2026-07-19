package com.hfxconnect.resource;

/**
 * Business-layer input for updating a resource's editable fields. The
 * resource being updated is identified separately (see
 * {@link ResourceService#update}), not carried inside this command. Kept as
 * a distinct type from {@link CreateResourceCommand}, even though the field
 * set is identical today, because update semantics (partial fields, for
 * example) are a reasonable way this could diverge later — see
 * {@code docs/milestones/milestone-03b-resource-domain.md}.
 */
public record UpdateResourceCommand(
		Long categoryId,
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
		String eligibility) {

}
