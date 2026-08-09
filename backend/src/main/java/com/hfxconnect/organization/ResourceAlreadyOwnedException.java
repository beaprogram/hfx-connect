package com.hfxconnect.organization;

import com.hfxconnect.common.error.ConflictException;

/**
 * The target resource already has an owning organization — either at claim
 * creation time (checked against the current, non-locked state) or at claim
 * approval time (re-checked under the resource row's lock, since another
 * claim could have been approved first — see ADR-017's "Concurrency
 * Strategy" section). The two call sites share this exception because both
 * describe the exact same fact from the caller's point of view.
 */
public class ResourceAlreadyOwnedException extends ConflictException {

	ResourceAlreadyOwnedException() {
		super("RESOURCE_ALREADY_OWNED", "This resource is already owned by an organization.");
	}

}
