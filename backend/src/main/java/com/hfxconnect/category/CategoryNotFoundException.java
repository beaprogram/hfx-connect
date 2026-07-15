package com.hfxconnect.category;

import com.hfxconnect.common.error.NotFoundException;

public class CategoryNotFoundException extends NotFoundException {

	private CategoryNotFoundException(String message) {
		super("CATEGORY_NOT_FOUND", message);
	}

	static CategoryNotFoundException byId(Long id) {
		return new CategoryNotFoundException("No category exists with id " + id + ".");
	}

	static CategoryNotFoundException bySlug(String slug) {
		return new CategoryNotFoundException("No category exists with slug '" + slug + "'.");
	}

}
