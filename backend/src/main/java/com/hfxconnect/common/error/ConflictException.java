package com.hfxconnect.common.error;

import org.springframework.http.HttpStatus;

/** The request conflicts with existing data (e.g. a duplicate). Translated to {@code 409 Conflict}. */
public class ConflictException extends ApiException {

	public ConflictException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}

}
