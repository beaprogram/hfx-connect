package com.hfxconnect.common.error;

/** A caller-supplied {@code openNow} filter value is not a valid boolean ({@code true}/{@code false}). */
public class InvalidOpenNowFilterException extends BadRequestException {

	public InvalidOpenNowFilterException(String message) {
		super("INVALID_OPEN_NOW_FILTER", message);
	}

}
