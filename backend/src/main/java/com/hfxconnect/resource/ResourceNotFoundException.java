package com.hfxconnect.resource;

import com.hfxconnect.common.error.NotFoundException;
import java.util.UUID;

public class ResourceNotFoundException extends NotFoundException {

	private ResourceNotFoundException(String message) {
		super("RESOURCE_NOT_FOUND", message);
	}

	static ResourceNotFoundException byId(UUID id) {
		return new ResourceNotFoundException("No resource exists with id " + id + ".");
	}

	static ResourceNotFoundException bySlug(String slug) {
		return new ResourceNotFoundException("No active resource exists with slug '" + slug + "'.");
	}

}
