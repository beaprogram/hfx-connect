package com.hfxconnect.resource;

import com.hfxconnect.common.error.NotFoundException;

/**
 * The category ID a resource is being created under or moved to does not
 * reference any existing category. A distinct type from
 * {@code com.hfxconnect.category.CategoryNotFoundException} (same code,
 * same meaning, different package — this one is thrown while validating a
 * *reference* to a category from within the resource domain, not while
 * looking up a category directly).
 */
public class CategoryNotFoundException extends NotFoundException {

	private CategoryNotFoundException(String message) {
		super("CATEGORY_NOT_FOUND", message);
	}

	static CategoryNotFoundException forId(Long categoryId) {
		return new CategoryNotFoundException("No category exists with id " + categoryId + ".");
	}

}
