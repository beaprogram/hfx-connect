package com.hfxconnect.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The request body for a resource-submission approve/reject decision, and a
 * correction-report rejection. {@code reason} is required for every
 * decision — see {@code ModerationValidation}. Status is never client-
 * supplied; it is implied entirely by which endpoint (approve vs. reject)
 * was called.
 */
@Schema(description = "A moderation decision. The review reason is always required and is visible to the contribution's owner.")
public record ModerationDecisionRequest(
		@NotBlank(message = "A review reason is required.") @Schema(example = "Verified against the organization's website; details match.") String reason) {
}
