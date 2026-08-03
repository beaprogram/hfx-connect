package com.hfxconnect.resource;

import com.hfxconnect.common.error.NotFoundException;
import java.util.UUID;

public class ResourceNotFoundException extends NotFoundException {

	private ResourceNotFoundException(String message) {
		super("RESOURCE_NOT_FOUND", message);
	}

	/**
	 * {@code public}: also thrown by {@code com.hfxconnect.savedresource}
	 * (Milestone 8A) when saving a resource id that doesn't correspond to any
	 * active resource — the same "not found" semantics this method already
	 * gives every in-package caller.
	 */
	public static ResourceNotFoundException byId(UUID id) {
		return new ResourceNotFoundException("No resource exists with id " + id + ".");
	}

	static ResourceNotFoundException bySlug(String slug) {
		return new ResourceNotFoundException("No active resource exists with slug '" + slug + "'.");
	}

}
