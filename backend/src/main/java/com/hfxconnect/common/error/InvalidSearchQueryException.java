package com.hfxconnect.common.error;

/** A caller-supplied {@code q} (keyword search) value exceeds the documented maximum length. */
public class InvalidSearchQueryException extends BadRequestException {

	public InvalidSearchQueryException(String message) {
		super("INVALID_SEARCH_QUERY", message);
	}

}
