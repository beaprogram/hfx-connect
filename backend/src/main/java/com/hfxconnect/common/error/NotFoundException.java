package com.hfxconnect.common.error;

import org.springframework.http.HttpStatus;

/** A requested resource does not exist. Translated to {@code 404 Not Found}. */
public class NotFoundException extends ApiException {

	public NotFoundException(String code, String message) {
		super(HttpStatus.NOT_FOUND, code, message);
	}

}
