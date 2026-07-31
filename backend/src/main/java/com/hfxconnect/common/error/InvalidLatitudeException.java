package com.hfxconnect.common.error;

/** A caller-supplied {@code latitude} query parameter on {@code GET /api/v1/resources/nearby} is missing or out of range. */
public class InvalidLatitudeException extends BadRequestException {

	public InvalidLatitudeException(String message) {
		super("INVALID_LATITUDE", message);
	}

}
