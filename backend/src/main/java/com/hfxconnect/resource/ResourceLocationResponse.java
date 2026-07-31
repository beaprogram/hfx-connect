package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "The saved coordinate for a resource, returned by PUT /api/v1/resources/{id}/location.")
public record ResourceLocationResponse(
		UUID resourceId,
		@Schema(example = "44.6488") double latitude,
		@Schema(example = "-63.5752") double longitude,
		Instant updatedAt) {

}
