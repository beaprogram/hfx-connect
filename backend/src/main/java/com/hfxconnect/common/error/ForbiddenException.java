package com.hfxconnect.common.error;

import org.springframework.http.HttpStatus;

/**
 * Authentication succeeded, but the account is not permitted to do what it
 * asked. Translated to {@code 403 Forbidden}. Distinct from
 * {@link UnauthorizedException}: that one means "we don't know who you are or
 * don't believe you," this one means "we know who you are, and the answer is
 * no."
 */
public class ForbiddenException extends ApiException {

	public ForbiddenException(String code, String message) {
		super(HttpStatus.FORBIDDEN, code, message);
	}

}
