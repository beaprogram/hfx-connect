package com.hfxconnect.common.error;

/** A caller-supplied {@code verificationStatus} filter value is not one of the documented {@code VerificationStatus} enum values. */
public class InvalidVerificationStatusException extends BadRequestException {

	public InvalidVerificationStatusException(String message) {
		super("INVALID_VERIFICATION_STATUS", message);
	}

}
