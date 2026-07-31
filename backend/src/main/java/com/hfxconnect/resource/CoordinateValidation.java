package com.hfxconnect.resource;

/**
 * Shared latitude/longitude range checks used by both the location-write
 * body ({@link ResourceLocationValidation}, which reuses the existing
 * {@code ValidationException}/{@code VALIDATION_ERROR} shape) and the
 * public nearby-search query parameters ({@code ResourceService#nearby},
 * which throws the dedicated {@code InvalidLatitudeException}/
 * {@code InvalidLongitudeException}) — see ADR-012's "Error Codes" section
 * for why the same range rule produces two different error shapes
 * depending on whether it's validating a request body or a query
 * parameter.
 *
 * <p>Coordinates are never restricted to a Halifax bounding box here — a
 * resource (or a search origin) outside Halifax is still a structurally
 * valid coordinate; only the nearby-search radius is Halifax-scoped (see
 * {@link ResourceService}).
 */
final class CoordinateValidation {

	static final double MIN_LATITUDE = -90;
	static final double MAX_LATITUDE = 90;
	static final double MIN_LONGITUDE = -180;
	static final double MAX_LONGITUDE = 180;

	private CoordinateValidation() {
	}

	static boolean isValidLatitude(Double latitude) {
		return latitude != null && Double.isFinite(latitude) && latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
	}

	static boolean isValidLongitude(Double longitude) {
		return longitude != null && Double.isFinite(longitude) && longitude >= MIN_LONGITUDE
				&& longitude <= MAX_LONGITUDE;
	}

}
