package com.hfxconnect.organization;

import com.hfxconnect.common.error.ConflictException;

public class ResourceOwnershipClaimConflictException extends ConflictException {

	private ResourceOwnershipClaimConflictException(String message) {
		super("RESOURCE_OWNERSHIP_CLAIM_CONFLICT", message);
	}

	static ResourceOwnershipClaimConflictException duplicatePending() {
		return new ResourceOwnershipClaimConflictException(
				"This organization already has a pending claim for this resource.");
	}

}
