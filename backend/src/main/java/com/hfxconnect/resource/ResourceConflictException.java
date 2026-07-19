package com.hfxconnect.resource;

import com.hfxconnect.common.error.ConflictException;

public class ResourceConflictException extends ConflictException {

	private ResourceConflictException(String message) {
		super("RESOURCE_CONFLICT", message);
	}

	static ResourceConflictException duplicateSlug(String slug) {
		return new ResourceConflictException("A resource with slug '" + slug + "' already exists.");
	}

}
