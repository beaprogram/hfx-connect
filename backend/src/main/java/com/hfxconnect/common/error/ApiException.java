package com.hfxconnect.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that should be translated directly into a
 * consistent {@link ApiError} response by {@link GlobalExceptionHandler}.
 * Domain-specific exceptions (for example {@code CategoryNotFoundException})
 * extend one of the concrete subclasses below rather than this class
 * directly.
 */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	protected ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

}
