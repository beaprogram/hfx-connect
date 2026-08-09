package com.hfxconnect.organization;

import com.hfxconnect.common.error.InvalidContributionStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One organization's request to own one existing resource (Milestone 10A)
 * — workflow history, never itself the authority on current ownership. See
 * {@code V11__create_organizations_and_resource_claims.sql} and ADR-017's
 * "Ownership Source of Truth" section: {@code resources.organization_id}
 * (set only by an approved claim, via {@code
 * CommunityResource.assignOrganization}) is what actually grants ownership
 * authority — a claim's own {@code status} never implies current ownership
 * on its own, since a resource could be reassigned or an old claim record
 * kept purely for history.
 *
 * <p>{@code organizationId}/{@code resourceId} are plain {@code UUID}
 * columns, the same "no back-reference needed" reasoning every other
 * contribution-domain relationship in this codebase already established —
 * {@code ResourceOwnershipClaimReviewService} loads the referenced
 * {@code Organization}/{@code CommunityResource} directly by id when it
 * needs them, rather than navigating a mapped association.
 */
@Entity
@Table(name = "resource_ownership_claims")
public class ResourceOwnershipClaim {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "resource_id", nullable = false)
	private UUID resourceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ResourceOwnershipClaimStatus status;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "reviewed_by_user_id")
	private UUID reviewedByUserId;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "review_reason", length = 1000)
	private String reviewReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ResourceOwnershipClaim() {
		// required by JPA
	}

	public ResourceOwnershipClaim(UUID organizationId, UUID resourceId) {
		this.organizationId = organizationId;
		this.resourceId = resourceId;
		this.status = ResourceOwnershipClaimStatus.PENDING_REVIEW;
	}

	/** Only legal while still {@link ResourceOwnershipClaimStatus#PENDING_REVIEW}. */
	public void withdraw() {
		requirePending();
		this.status = ResourceOwnershipClaimStatus.WITHDRAWN;
	}

	/** Only legal while still {@link ResourceOwnershipClaimStatus#PENDING_REVIEW}. */
	public void approve(UUID reviewerId, String reason) {
		requirePending();
		this.status = ResourceOwnershipClaimStatus.APPROVED;
		this.reviewedByUserId = reviewerId;
		this.reviewedAt = Instant.now();
		this.reviewReason = reason;
	}

	/** Only legal while still {@link ResourceOwnershipClaimStatus#PENDING_REVIEW}. */
	public void reject(UUID reviewerId, String reason) {
		requirePending();
		this.status = ResourceOwnershipClaimStatus.REJECTED;
		this.reviewedByUserId = reviewerId;
		this.reviewedAt = Instant.now();
		this.reviewReason = reason;
	}

	private void requirePending() {
		if (status != ResourceOwnershipClaimStatus.PENDING_REVIEW) {
			throw new InvalidContributionStatusException(
					"Only a pending-review claim can be decided (current status: " + status + ").");
		}
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.requestedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getResourceId() {
		return resourceId;
	}

	public ResourceOwnershipClaimStatus getStatus() {
		return status;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public UUID getReviewedByUserId() {
		return reviewedByUserId;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public String getReviewReason() {
		return reviewReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
