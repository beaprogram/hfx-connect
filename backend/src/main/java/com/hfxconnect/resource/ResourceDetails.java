package com.hfxconnect.resource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business-layer read model for a resource. Not an HTTP response DTO — see
 * {@link CreateResourceCommand}.
 *
 * <p>Includes {@code categoryName}/{@code categorySlug} (not just
 * {@code categoryId}) so callers building a response DTO never need a
 * separate category lookup — {@link ResourceService}'s read methods use the
 * {@code *WithCategory} repository queries specifically so this mapping never
 * triggers a lazy-loading N+1 query.
 *
 * <p>{@code hoursStatus}/{@code openNow}/{@code weeklyHours} are computed by
 * {@link OpenNowCalculator} from a separately batch-loaded (or single-lookup)
 * set of {@link OperatingHoursEntry} rows — see ADR-011 — never lazily
 * derived from the entity itself, since {@link CommunityResource} has no
 * relationship to its hours (avoiding N+1 is the entire point).
 */
public record ResourceDetails(
		UUID id,
		Long categoryId,
		String categoryName,
		String categorySlug,
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
		Instant lastVerifiedAt,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		HoursStatus hoursStatus,
		Boolean openNow,
		List<OperatingHoursEntry> weeklyHours) {

	/** A resource with no computed hours yet (freshly created/updated) is UNKNOWN — see ADR-011. */
	static ResourceDetails from(CommunityResource resource) {
		return from(resource, HoursStatus.UNKNOWN, null, List.of());
	}

	static ResourceDetails from(CommunityResource resource, HoursStatus hoursStatus, Boolean openNow,
			List<OperatingHoursEntry> weeklyHours) {
		return new ResourceDetails(
				resource.getId(),
				resource.getCategory().getId(),
				resource.getCategory().getName(),
				resource.getCategory().getSlug(),
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
				resource.getLastVerifiedAt(),
				resource.isActive(),
				resource.getCreatedAt(),
				resource.getUpdatedAt(),
				hoursStatus,
				openNow,
				weeklyHours);
	}

}
