package com.hfxconnect.savedresource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "The subset of the requested resourceIds the current user has saved. Never reveals anything about another user's saved resources, and never includes an id for a resource that doesn't exist or isn't active.")
public record SavedResourceStatusResponse(List<UUID> savedResourceIds) {
}
