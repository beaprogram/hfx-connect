package com.hfxconnect.common.error;

/** A page/size query parameter is out of the allowed range. */
public class InvalidPaginationException extends BadRequestException {

	public InvalidPaginationException(String message) {
		super("INVALID_PAGINATION", message);
	}

}
