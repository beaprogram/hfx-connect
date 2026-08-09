package com.hfxconnect.organization;

/** A resource-ownership claim's review state (Milestone 10A). Matches {@code resource_ownership_claims_status_check}. */
public enum ResourceOwnershipClaimStatus {
	PENDING_REVIEW,
	APPROVED,
	REJECTED,
	WITHDRAWN
}
