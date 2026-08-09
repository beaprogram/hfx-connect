package com.hfxconnect.organization;

import com.hfxconnect.common.error.NotFoundException;
import java.util.UUID;

/**
 * No claim exists with the given id, or (for an organization-scoped lookup)
 * it belongs to a different organization — the two cases are deliberately
 * indistinguishable to the caller, the same "no ownership leak" reasoning
 * {@code ResourceSubmissionNotFoundException} already established for the
 * current-user-scoped submission lookup.
 */
public class ResourceOwnershipClaimNotFoundException extends NotFoundException {

	private ResourceOwnershipClaimNotFoundException(String message) {
		super("RESOURCE_OWNERSHIP_CLAIM_NOT_FOUND", message);
	}

	static ResourceOwnershipClaimNotFoundException byId(UUID id) {
		return new ResourceOwnershipClaimNotFoundException("No resource ownership claim exists with id " + id + ".");
	}

}
