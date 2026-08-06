package com.hfxconnect.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of moderation audit trail entries.")
public record ModerationAuditEventPageResponse(
		List<ModerationAuditEventResponse> content, int page, int size, long totalElements, int totalPages) {
}
