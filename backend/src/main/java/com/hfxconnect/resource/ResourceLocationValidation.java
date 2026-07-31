package com.hfxconnect.resource;

import com.hfxconnect.common.error.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates a {@link ResourceLocationRequest} body, reusing the existing
 * {@code ValidationException}/{@code VALIDATION_ERROR} shape — the same
 * layering {@code ResourceValidation}/{@code OperatingHoursValidation}
 * already established. See {@link CoordinateValidation} for the shared
 * range rules, and ADR-012 for why the nearby-search query parameters use
 * a different (dedicated-exception) error shape than this body does.
 */
final class ResourceLocationValidation {

	private ResourceLocationValidation() {
	}

	record Normalized(double latitude, double longitude) {
	}

	static Normalized validate(ResourceLocationRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();

		Double latitude = request == null ? null : request.latitude();
		Double longitude = request == null ? null : request.longitude();

		if (latitude == null) {
			errors.put("latitude", "latitude is required.");
		} else if (!CoordinateValidation.isValidLatitude(latitude)) {
			errors.put("latitude", "latitude must be a finite number between "
					+ CoordinateValidation.MIN_LATITUDE + " and " + CoordinateValidation.MAX_LATITUDE + ".");
		}

		if (longitude == null) {
			errors.put("longitude", "longitude is required.");
		} else if (!CoordinateValidation.isValidLongitude(longitude)) {
			errors.put("longitude", "longitude must be a finite number between "
					+ CoordinateValidation.MIN_LONGITUDE + " and " + CoordinateValidation.MAX_LONGITUDE + ".");
		}

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted location contains invalid information.", errors);
		}

		return new Normalized(latitude, longitude);
	}

}
