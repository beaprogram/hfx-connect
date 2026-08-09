package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of organization/ownership audit trail entries.")
public record OrganizationAuditEventPageResponse(
		List<OrganizationAuditEventResponse> content, int page, int size, long totalElements, int totalPages) {
}
