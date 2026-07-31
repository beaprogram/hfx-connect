package com.hfxconnect.common.error;

/** A caller-supplied {@code longitude} query parameter on {@code GET /api/v1/resources/nearby} is missing or out of range. */
public class InvalidLongitudeException extends BadRequestException {

	public InvalidLongitudeException(String message) {
		super("INVALID_LONGITUDE", message);
	}

}
