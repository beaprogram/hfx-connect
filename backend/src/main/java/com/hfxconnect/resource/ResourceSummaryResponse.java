package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Card-sized resource summary used in list results. Omits fields (full description, contact details, eligibility, ...) that only matter once a specific resource has been opened — see GET /api/v1/resources/{id} for the full ResourceResponse.")
public record ResourceSummaryResponse(
		UUID id,
		@Schema(example = "Halifax Central Library") String name,
		@Schema(example = "halifax-central-library") String slug,
		String city,
		String province,
		CostType costType,
		VerificationStatus verificationStatus,
		boolean active,
		CategorySummaryResponse category,
		Instant createdAt,
		@Schema(description = "UNKNOWN when the resource has no operating-hours schedule at all; otherwise always OPEN or CLOSED. See ADR-011.")
		HoursStatus hoursStatus,
		@Schema(description = "True/false when hoursStatus is OPEN/CLOSED; null when hoursStatus is UNKNOWN.")
		Boolean openNow) {

	static ResourceSummaryResponse from(ResourceDetails details) {
		return new ResourceSummaryResponse(
				details.id(),
				details.name(),
				details.slug(),
				details.city(),
				details.province(),
				details.costType(),
				details.verificationStatus(),
				details.active(),
				CategorySummaryResponse.from(details),
				details.createdAt(),
				details.hoursStatus(),
				details.openNow());
	}

}
