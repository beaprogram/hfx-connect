package com.hfxconnect.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A business-rule validation failure that Bean Validation annotations can't
 * express directly (for example: "the name is not blank, but normalizes to
 * an empty slug"). Produces the same {@code VALIDATION_ERROR} shape,
 * including {@code fieldErrors}, as a failed {@code @Valid} request body.
 */
public class ValidationException extends ApiException {

	private final Map<String, String> fieldErrors;

	public ValidationException(String message, Map<String, String> fieldErrors) {
		super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
		this.fieldErrors = fieldErrors;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}

}
