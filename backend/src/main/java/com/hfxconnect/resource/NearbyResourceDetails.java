package com.hfxconnect.resource;

import java.time.Instant;
import java.util.UUID;

/**
 * Business-layer read model for one nearby-search result — the
 * {@link NearbyResourceProjection} (raw native-query columns) plus the
 * batch-loaded {@link HoursStatus}/{@code openNow} pair, the same split
 * {@link ResourceDetails} already applies for the non-geospatial listing.
 */
record NearbyResourceDetails(
		UUID id,
		String name,
		String slug,
		String city,
		String province,
		CostType costType,
		VerificationStatus verificationStatus,
		boolean active,
		Long categoryId,
		String categoryName,
		String categorySlug,
		Instant createdAt,
		double latitude,
		double longitude,
		double distanceMeters,
		HoursStatus hoursStatus,
		Boolean openNow,
		ResourceOrganizationSummaryResponse organization) {

	static NearbyResourceDetails from(NearbyResourceProjection projection, HoursStatus hoursStatus, Boolean openNow,
			ResourceOrganizationSummaryResponse organization) {
		return new NearbyResourceDetails(
				projection.getId(),
				projection.getName(),
				projection.getSlug(),
				projection.getCity(),
				projection.getProvince(),
				CostType.valueOf(projection.getCostType()),
				VerificationStatus.valueOf(projection.getVerificationStatus()),
				projection.getActive(),
				projection.getCategoryId(),
				projection.getCategoryName(),
				projection.getCategorySlug(),
				projection.getCreatedAt(),
				projection.getLatitude(),
				projection.getLongitude(),
				projection.getDistanceMeters(),
				hoursStatus,
				openNow,
				organization);
	}

}
