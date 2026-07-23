package com.hfxconnect.resource;

import com.hfxconnect.common.error.BadRequestException;

/**
 * The category a resource is being created under or moved to exists, but is
 * inactive. Deliberately a {@code 400}, not a {@code 404}: the category
 * reference is valid, the request is just not allowed to use it — a
 * different failure mode from {@link CategoryNotFoundException}'s "this ID
 * doesn't exist at all."
 */
public class InactiveCategoryException extends BadRequestException {

	private InactiveCategoryException(String message) {
		super("INACTIVE_CATEGORY", message);
	}

	static InactiveCategoryException forId(Long categoryId) {
		return new InactiveCategoryException(
				"Category " + categoryId + " is not active and cannot be assigned to a resource.");
	}

}
