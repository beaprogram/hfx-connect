package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Full public detail for a resource.")
public record ResourceResponse(
		UUID id,
		@Schema(example = "Halifax Central Library") String name,
		@Schema(example = "halifax-central-library") String slug,
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
		CategorySummaryResponse category,
		Instant createdAt,
		Instant updatedAt) {

	static ResourceResponse from(ResourceDetails details) {
		return new ResourceResponse(
				details.id(),
				details.name(),
				details.slug(),
				details.description(),
				details.addressLine1(),
				details.addressLine2(),
				details.city(),
				details.province(),
				details.postalCode(),
				details.phone(),
				details.email(),
				details.websiteUrl(),
				details.costType(),
				details.costDetails(),
				details.eligibility(),
				details.verificationStatus(),
				details.active(),
				CategorySummaryResponse.from(details),
				details.createdAt(),
				details.updatedAt());
	}

}
