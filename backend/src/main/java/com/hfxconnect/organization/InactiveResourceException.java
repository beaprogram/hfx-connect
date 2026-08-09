package com.hfxconnect.organization;

import com.hfxconnect.common.error.BadRequestException;

/** The target resource exists but is not active, the same "reference is valid, just not usable here" shape as {@code InactiveCategoryException}. */
public class InactiveResourceException extends BadRequestException {

	InactiveResourceException() {
		super("INACTIVE_RESOURCE", "This resource is not active and cannot be claimed.");
	}

}
