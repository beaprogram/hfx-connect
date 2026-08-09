package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the current organization's resource-ownership claims.")
public record OwnershipClaimPageResponse(
		List<OwnershipClaimResponse> content, int page, int size, long totalElements, int totalPages) {
}
