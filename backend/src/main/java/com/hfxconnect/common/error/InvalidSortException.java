package com.hfxconnect.common.error;

/** A caller-supplied {@code sort} value is not one of an endpoint's documented, allowlisted fields. */
public class InvalidSortException extends BadRequestException {

	public InvalidSortException(String message) {
		super("INVALID_SORT", message);
	}

}
