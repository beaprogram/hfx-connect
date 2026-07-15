package com.hfxconnect.common.error;

import org.springframework.http.HttpStatus;

/** The request itself is invalid in a way Bean Validation doesn't cover (e.g. pagination parameters). Translated to {@code 400 Bad Request}. */
public class BadRequestException extends ApiException {

	public BadRequestException(String code, String message) {
		super(HttpStatus.BAD_REQUEST, code, message);
	}

}
