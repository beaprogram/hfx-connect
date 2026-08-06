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
 *
 * <p>{@link #byId}: {@code public} as of Milestone 9A — also thrown by
 * {@code com.hfxconnect.moderation} when a moderator requests a submission id
 * that does not exist at all (a moderator's queue/detail/review routes are
 * not owner-scoped, but a genuinely missing id is still a 404).
 */
public class ResourceSubmissionNotFoundException extends NotFoundException {

	private ResourceSubmissionNotFoundException(String message) {
		super("RESOURCE_SUBMISSION_NOT_FOUND", message);
	}

	public static ResourceSubmissionNotFoundException byId(UUID id) {
		return new ResourceSubmissionNotFoundException("No resource submission exists with id " + id + ".");
	}

}
