package com.hfxconnect.resource;

import com.hfxconnect.common.error.BadRequestException;

/** The category a resource is being created under or moved to doesn't exist, or exists but is inactive. */
public class CategoryUnavailableException extends BadRequestException {

	private CategoryUnavailableException(String message) {
		super("CATEGORY_UNAVAILABLE", message);
	}

	static CategoryUnavailableException missing(Long categoryId) {
		return new CategoryUnavailableException("No category exists with id " + categoryId + ".");
	}

	static CategoryUnavailableException inactive(Long categoryId) {
		return new CategoryUnavailableException(
				"Category " + categoryId + " is not active and cannot be assigned to a resource.");
	}

}
