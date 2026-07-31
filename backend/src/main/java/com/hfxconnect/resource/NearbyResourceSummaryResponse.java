package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A resource-summary card plus its geographic distance from the search origin. distanceMeters is straight-line (\"as the crow flies\") geographic distance in metres, never route distance or travel time — see ADR-012.")
public record NearbyResourceSummaryResponse(
		UUID id,
		@Schema(example = "Halifax Central Library") String name,
		@Schema(example = "halifax-central-library") String slug,
		String city,
		String province,
		CostType costType,
		VerificationStatus verificationStatus,
		boolean active,
		CategorySummaryResponse category,
		Instant createdAt,
		HoursStatus hoursStatus,
		Boolean openNow,
		@Schema(example = "44.6488") double latitude,
		@Schema(example = "-63.5752") double longitude,
		@Schema(description = "Straight-line geographic distance from the search origin, in metres.", example = "1204.7")
		double distanceMeters) {

	static NearbyResourceSummaryResponse from(NearbyResourceDetails details) {
		return new NearbyResourceSummaryResponse(
				details.id(),
				details.name(),
				details.slug(),
				details.city(),
				details.province(),
				details.costType(),
				details.verificationStatus(),
				details.active(),
				new CategorySummaryResponse(details.categoryId(), details.categoryName(), details.categorySlug()),
				details.createdAt(),
				details.hoursStatus(),
				details.openNow(),
				details.latitude(),
				details.longitude(),
				details.distanceMeters());
	}

}
