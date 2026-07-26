package com.hfxconnect.common.error;

import org.springframework.http.HttpStatus;

/** Authentication is missing or invalid. Translated to {@code 401 Unauthorized}. */
public class UnauthorizedException extends ApiException {

	public UnauthorizedException(String code, String message) {
		super(HttpStatus.UNAUTHORIZED, code, message);
	}

}
