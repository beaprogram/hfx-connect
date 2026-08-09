package com.hfxconnect.organization;

import com.hfxconnect.resource.CategorySummaryResponse;
import com.hfxconnect.resource.CommunityResource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A compact resource summary for an ownership-claim row (Milestone 10A) — deliberately smaller than the full public resource response. */
@Schema(description = "A compact resource summary shown on an ownership claim.")
public record ClaimedResourceSummaryResponse(UUID id, String name, String slug, CategorySummaryResponse category, boolean active) {

	static ClaimedResourceSummaryResponse from(CommunityResource resource) {
		var category = resource.getCategory();
		return new ClaimedResourceSummaryResponse(
				resource.getId(), resource.getName(), resource.getSlug(),
				new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug()), resource.isActive());
	}

}
