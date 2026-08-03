package com.hfxconnect.savedresource;

import com.hfxconnect.resource.ResourceSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "One saved resource: when it was saved, plus the same card-sized resource summary the public resource list/detail pages use. Never includes another user's data or any inactive-resource detail.")
public record SavedResourceSummaryResponse(
		@Schema(description = "When the current user saved this resource.") Instant savedAt,
		ResourceSummaryResponse resource) {
}
