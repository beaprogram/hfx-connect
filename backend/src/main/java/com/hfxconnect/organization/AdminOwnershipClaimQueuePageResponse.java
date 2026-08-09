package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of the admin resource-ownership-claim queue.")
public record AdminOwnershipClaimQueuePageResponse(
		List<AdminOwnershipClaimQueueItemResponse> content, int page, int size, long totalElements, int totalPages) {
}
