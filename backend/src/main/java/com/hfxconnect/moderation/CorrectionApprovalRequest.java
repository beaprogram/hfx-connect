package com.hfxconnect.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The request body for approving a correction report. Deliberately explicit
 * about what the moderator wants applied — there is no arbitrary field-
 * editing surface, only these two flags:
 *
 * <ul>
 *   <li>{@code applyProposedChanges} — apply whichever supported scalar
 *       fields the report proposed (see ADR-016's "Correction Application
 *       Policy"). Rejected with {@code 400 UNSUPPORTED_CORRECTION_APPLICATION}
 *       when {@code true} for an {@code OPERATING_HOURS} or {@code
 *       DUPLICATE_RESOURCE} report — neither has an automated field mapping.
 *   <li>{@code deactivateResource} — only legal (and only meaningful) when
 *       {@code issueType} is {@code RESOURCE_CLOSED}; rejected with
 *       {@code 400 INVALID_DEACTIVATION_REQUEST} otherwise.
 * </ul>
 *
 * Both may be {@code false} — a moderator may approve a report purely as a
 * reviewed acknowledgement with no automatic public-data change.
 */
@Schema(description = "A correction-report approval decision, explicit about what gets applied.")
public record CorrectionApprovalRequest(
		@NotBlank(message = "A review reason is required.") @Schema(example = "Confirmed the new address is correct.") String reason,
		@Schema(description = "Apply the report's supported proposed fields to the resource.") boolean applyProposedChanges,
		@Schema(description = "Deactivate the resource. Only legal for a RESOURCE_CLOSED report.") boolean deactivateResource) {
}
