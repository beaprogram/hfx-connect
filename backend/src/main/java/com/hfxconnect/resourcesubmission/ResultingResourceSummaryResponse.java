package com.hfxconnect.resourcesubmission;

import com.hfxconnect.resource.ResourceDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The public resource an approved submission created (Milestone 9A) —
 * shown to both the submission's owner and, via
 * {@code com.hfxconnect.moderation}, to reviewing moderators. Lives in this
 * package (not {@code moderation}) so the dependency runs one direction only
 * ({@code moderation} depends on {@code resourcesubmission}, never the
 * reverse), matching every other cross-package dependency in this codebase.
 */
@Schema(description = "The public resource an approved submission created.")
public record ResultingResourceSummaryResponse(UUID id, String name, String slug) {

	public static ResultingResourceSummaryResponse from(ResourceDetails resource) {
		return new ResultingResourceSummaryResponse(resource.id(), resource.name(), resource.slug());
	}

}
