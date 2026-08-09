package com.hfxconnect.organization;

import com.hfxconnect.common.error.NotFoundException;
import java.util.UUID;

/** No organization exists with the given id/slug, or (for the current-user route) the account has no profile yet. */
public class OrganizationNotFoundException extends NotFoundException {

	private OrganizationNotFoundException(String message) {
		super("ORGANIZATION_NOT_FOUND", message);
	}

	public static OrganizationNotFoundException byId(UUID id) {
		return new OrganizationNotFoundException("No organization exists with id " + id + ".");
	}

	public static OrganizationNotFoundException bySlug(String slug) {
		return new OrganizationNotFoundException("No organization exists with slug '" + slug + "'.");
	}

	public static OrganizationNotFoundException forCurrentUser() {
		return new OrganizationNotFoundException("The current account has no organization profile yet.");
	}

}
