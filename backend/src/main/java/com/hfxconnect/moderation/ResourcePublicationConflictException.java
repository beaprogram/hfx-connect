package com.hfxconnect.moderation;

import com.hfxconnect.common.error.ConflictException;

/**
 * Approving a resource submission would collide with an existing public
 * resource (same generated slug — which, since slugs are name-derived, also
 * catches a same-name collision regardless of category; see
 * {@code ResourceService}'s single slug-uniqueness rule, deliberately not
 * duplicated here as a second, independent name+category check). The
 * submission is left {@code PENDING_REVIEW} — never marked {@code APPROVED}
 * when publication fails.
 */
public class ResourcePublicationConflictException extends ConflictException {

	public ResourcePublicationConflictException(String submissionName) {
		super("RESOURCE_PUBLICATION_CONFLICT",
				"A resource derived from the name '" + submissionName
						+ "' already exists; this submission cannot be published as-is.");
	}

}
