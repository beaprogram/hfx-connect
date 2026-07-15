package com.hfxconnect.category;

import com.hfxconnect.common.error.ConflictException;

public class CategoryConflictException extends ConflictException {

	private CategoryConflictException(String message) {
		super("CATEGORY_CONFLICT", message);
	}

	static CategoryConflictException duplicateName(String name) {
		return new CategoryConflictException("A category named '" + name + "' already exists.");
	}

	static CategoryConflictException duplicateSlug(String slug) {
		return new CategoryConflictException("A category with slug '" + slug + "' already exists.");
	}

	static CategoryConflictException duplicate() {
		return new CategoryConflictException("A category with this name already exists.");
	}

}
