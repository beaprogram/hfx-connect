package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The owning organization's safe public summary, embedded in a resource
 * response (Milestone 10A) — present only when the resource is owned
 * <em>and</em> that organization is currently verified. {@code verified}
 * is always {@code true} when this object is present at all (an
 * unverified/rejected/suspended owner's attribution is omitted entirely,
 * not shown with {@code verified: false}) — included anyway so the field
 * is self-documenting for a frontend consumer that only has this one
 * object, not the full resource response, in hand. See ADR-017's
 * "Public Organization Exposure" section.
 */
@Schema(description = "The owning organization's safe public summary — present only when the resource is owned by a currently-verified organization.")
public record ResourceOrganizationSummaryResponse(UUID id, String name, String slug, boolean verified) {
}
