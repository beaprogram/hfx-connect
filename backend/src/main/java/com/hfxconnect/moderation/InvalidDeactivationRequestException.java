package com.hfxconnect.moderation;

import com.hfxconnect.common.error.BadRequestException;

/** {@code deactivateResource=true} was requested for a report whose issue type is not {@code RESOURCE_CLOSED}. */
public class InvalidDeactivationRequestException extends BadRequestException {

	public InvalidDeactivationRequestException() {
		super("INVALID_DEACTIVATION_REQUEST",
				"deactivateResource may only be true for a RESOURCE_CLOSED correction report.");
	}

}
