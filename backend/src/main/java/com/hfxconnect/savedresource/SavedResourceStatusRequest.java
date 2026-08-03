package com.hfxconnect.savedresource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "A batch of candidate resource ids to check the current user's saved status for — used so a page of resource cards needs one request, not one per card.")
public record SavedResourceStatusRequest(
		@Schema(description = "At most " + SavedResourceValidation.MAX_STATUS_IDS + " ids. Duplicates are normalized; a null entry is rejected.")
		List<UUID> resourceIds) {
}
