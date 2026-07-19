package com.hfxconnect.resource;

/**
 * Business-layer input for creating a resource. Deliberately not an HTTP
 * request DTO — there is no controller yet (Milestone 3C). {@code costType}
 * may be {@code null}; the service defaults it to {@link CostType#UNKNOWN}.
 */
public record CreateResourceCommand(
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
