package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One admin organization-queue row — deliberately compact, the same posture {@code ResourceSubmissionQueueItemResponse} already established. */
@Schema(description = "A compact organization queue row.")
public record AdminOrganizationQueueItemResponse(
		UUID id, String name, String slug, OrganizationVerificationStatus verificationStatus, Instant createdAt) {

	static AdminOrganizationQueueItemResponse from(Organization organization) {
		return new AdminOrganizationQueueItemResponse(
				organization.getId(), organization.getName(), organization.getSlug(),
				organization.getVerificationStatus(), organization.getCreatedAt());
	}

}
