package com.hfxconnect.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** An admin verify/reject/suspend decision, or an ownership-claim approve/reject decision (Milestone 10A). */
@Schema(description = "An admin decision. The reason is always required and is visible to the organization owner.")
public record OrganizationDecisionRequest(
		@NotBlank(message = "A reason is required.") @Schema(example = "Confirmed against the organization's public registration.") String reason) {
}
