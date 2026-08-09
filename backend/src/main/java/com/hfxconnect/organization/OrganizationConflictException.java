package com.hfxconnect.organization;

import com.hfxconnect.common.error.ConflictException;

public class OrganizationConflictException extends ConflictException {

	private OrganizationConflictException(String message) {
		super("ORGANIZATION_CONFLICT", message);
	}

	static OrganizationConflictException accountAlreadyHasProfile() {
		return new OrganizationConflictException("This account already has an organization profile.");
	}

	static OrganizationConflictException duplicateSlug(String slug) {
		return new OrganizationConflictException("An organization with slug '" + slug + "' already exists.");
	}

}
