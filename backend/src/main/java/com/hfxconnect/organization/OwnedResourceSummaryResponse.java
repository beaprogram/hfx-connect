package com.hfxconnect.organization;

import com.hfxconnect.resource.CategorySummaryResponse;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One resource owned by the current organization (Milestone 10A) — the
 * foundation for a future listing-management feature (see the milestone
 * doc's "Known Limitations"); no edit action exists on this response yet.
 * Both active and inactive owned resources are included.
 */
@Schema(description = "A resource owned by the current organization.")
public record OwnedResourceSummaryResponse(
		UUID id, String name, String slug, CategorySummaryResponse category, boolean active,
		VerificationStatus verificationStatus, Instant lastVerifiedAt) {

	static OwnedResourceSummaryResponse from(CommunityResource resource) {
		var category = resource.getCategory();
		return new OwnedResourceSummaryResponse(
				resource.getId(), resource.getName(), resource.getSlug(),
				new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug()),
				resource.isActive(), resource.getVerificationStatus(), resource.getLastVerifiedAt());
	}

}
