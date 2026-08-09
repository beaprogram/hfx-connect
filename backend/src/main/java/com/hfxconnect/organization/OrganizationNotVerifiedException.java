package com.hfxconnect.organization;

import com.hfxconnect.common.error.ForbiddenException;

/** Only a {@link OrganizationVerificationStatus#VERIFIED} organization may submit a resource-ownership claim (ADR-017). */
public class OrganizationNotVerifiedException extends ForbiddenException {

	OrganizationNotVerifiedException() {
		super("ORGANIZATION_NOT_VERIFIED", "Only a verified organization may claim a resource listing.");
	}

}
