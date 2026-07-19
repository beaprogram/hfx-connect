package com.hfxconnect.resource;

import java.time.Instant;
import java.util.UUID;

/** Business-layer read model for a resource. Not an HTTP response DTO — see {@link CreateResourceCommand}. */
public record ResourceDetails(
		UUID id,
		Long categoryId,
		String name,
		String slug,
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
		VerificationStatus verificationStatus,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {

	static ResourceDetails from(CommunityResource resource) {
		return new ResourceDetails(
				resource.getId(),
				resource.getCategory().getId(),
				resource.getName(),
				resource.getSlug(),
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
				resource.getVerificationStatus(),
				resource.isActive(),
				resource.getCreatedAt(),
				resource.getUpdatedAt());
	}

}
