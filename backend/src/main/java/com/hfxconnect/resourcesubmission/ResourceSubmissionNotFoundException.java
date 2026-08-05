package com.hfxconnect.resourcesubmission;

import com.hfxconnect.common.error.NotFoundException;
import java.util.UUID;

/**
 * No submission exists with the given id that also belongs to the caller.
 * Deliberately the same exception (and the same {@code 404}) whether the id
 * genuinely doesn't exist or belongs to a different account — see the
 * milestone's "return 404 for inaccessible contribution IDs rather than
 * revealing ownership" requirement. The message never distinguishes the two
 * cases either.
 */
public class ResourceSubmissionNotFoundException extends NotFoundException {

	private ResourceSubmissionNotFoundException(String message) {
		super("RESOURCE_SUBMISSION_NOT_FOUND", message);
	}

	static ResourceSubmissionNotFoundException byId(UUID id) {
		return new ResourceSubmissionNotFoundException("No resource submission exists with id " + id + ".");
	}

}
