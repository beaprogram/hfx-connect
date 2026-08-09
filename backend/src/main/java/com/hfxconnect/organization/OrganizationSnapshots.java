package com.hfxconnect.organization;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the small, explicit {@code Map<String, Object>} structures stored in
 * {@link OrganizationAuditEvent#getBeforeSnapshot()}/{@code
 * getAfterSnapshot()} — every key listed by hand, mirroring {@code
 * com.hfxconnect.moderation.ModerationSnapshots}'s exact reasoning: a
 * snapshot must never accidentally pick up a field (e.g. {@code
 * ownerUserId}) this workflow does not intend to record.
 */
final class OrganizationSnapshots {

	private OrganizationSnapshots() {
	}

	static Map<String, Object> of(Organization organization) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("id", organization.getId().toString());
		snapshot.put("name", organization.getName());
		snapshot.put("slug", organization.getSlug());
		snapshot.put("description", organization.getDescription());
		snapshot.put("websiteUrl", organization.getWebsiteUrl());
		snapshot.put("publicEmail", organization.getPublicEmail());
		snapshot.put("phone", organization.getPhone());
		snapshot.put("city", organization.getCity());
		snapshot.put("province", organization.getProvince());
		snapshot.put("verificationStatus", organization.getVerificationStatus().name());
		snapshot.put("verificationReason", organization.getVerificationReason());
		return snapshot;
	}

}
