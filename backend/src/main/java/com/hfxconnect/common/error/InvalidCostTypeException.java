package com.hfxconnect.common.error;

/** A caller-supplied {@code costType} filter value is not one of the documented {@code CostType} enum values. */
public class InvalidCostTypeException extends BadRequestException {

	public InvalidCostTypeException(String message) {
		super("INVALID_COST_TYPE", message);
	}

}
