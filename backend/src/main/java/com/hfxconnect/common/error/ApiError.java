package com.hfxconnect.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single consistent error response shape used by every HFX Connect API
 * endpoint. Never includes stack traces, SQL, table names, or other internal
 * implementation details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		Instant timestamp,
		int status,
		String code,
		String message,
		Map<String, String> fieldErrors) {

	public static ApiError of(int status, String code, String message) {
		return new ApiError(Instant.now(), status, code, message, null);
	}

	public static ApiError ofValidation(int status, String code, String message, Map<String, String> fieldErrors) {
		return new ApiError(Instant.now(), status, code, message, fieldErrors);
	}

}
