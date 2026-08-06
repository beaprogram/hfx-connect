package com.hfxconnect.moderation;

import com.hfxconnect.resource.ResourceDetails;
import com.hfxconnect.resourcesubmission.ResourceSubmission;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the small, explicit {@code Map<String, Object>} structures stored
 * in {@link ModerationAuditEvent#getBeforeSnapshot()}/{@code
 * getAfterSnapshot()} — every key is listed by hand here, never derived by
 * reflecting over an entity, so a snapshot can never accidentally pick up a
 * field this workflow does not intend to record (see ADR-016's "Snapshot
 * Policy" section).
 */
final class ModerationSnapshots {

	private ModerationSnapshots() {
	}

	/** The proposed-resource fields of an approved submission — the {@code RESOURCE_CREATED} event's before-snapshot. */
	static Map<String, Object> ofSubmission(ResourceSubmission submission) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", submission.getName());
		snapshot.put("categoryId", submission.getCategory().getId());
		snapshot.put("shortDescription", submission.getShortDescription());
		snapshot.put("fullDescription", submission.getFullDescription());
		snapshot.put("addressLine1", submission.getAddressLine1());
		snapshot.put("addressLine2", submission.getAddressLine2());
		snapshot.put("city", submission.getCity());
		snapshot.put("province", submission.getProvince());
		snapshot.put("postalCode", submission.getPostalCode());
		snapshot.put("phone", submission.getPhone());
		snapshot.put("email", submission.getEmail());
		snapshot.put("websiteUrl", submission.getWebsiteUrl());
		snapshot.put("costType", submission.getCostType().name());
		snapshot.put("eligibilityInformation", submission.getEligibilityInformation());
		return snapshot;
	}

	/** The resulting public resource's relevant fields — the {@code RESOURCE_CREATED} event's after-snapshot. */
	static Map<String, Object> ofResource(ResourceDetails resource) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("id", resource.id().toString());
		snapshot.put("name", resource.name());
		snapshot.put("slug", resource.slug());
		snapshot.put("categoryId", resource.categoryId());
		snapshot.put("description", resource.description());
		snapshot.put("addressLine1", resource.addressLine1());
		snapshot.put("addressLine2", resource.addressLine2());
		snapshot.put("city", resource.city());
		snapshot.put("province", resource.province());
		snapshot.put("postalCode", resource.postalCode());
		snapshot.put("phone", resource.phone());
		snapshot.put("email", resource.email());
		snapshot.put("websiteUrl", resource.websiteUrl());
		snapshot.put("costType", resource.costType().name());
		snapshot.put("costDetails", resource.costDetails());
		snapshot.put("eligibility", resource.eligibility());
		snapshot.put("verificationStatus", resource.verificationStatus().name());
		snapshot.put("active", resource.active());
		return snapshot;
	}

}
