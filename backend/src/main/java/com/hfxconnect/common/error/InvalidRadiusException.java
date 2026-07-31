package com.hfxconnect.common.error;

/** A caller-supplied {@code radiusKm} query parameter on {@code GET /api/v1/resources/nearby} is out of range. */
public class InvalidRadiusException extends BadRequestException {

	public InvalidRadiusException(String message) {
		super("INVALID_RADIUS", message);
	}

}
