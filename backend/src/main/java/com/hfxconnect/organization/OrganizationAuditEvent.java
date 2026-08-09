package com.hfxconnect.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One append-only organization/ownership event (Milestone 10A) — never
 * updated or deleted by application code. A focused, separate table from
 * {@code com.hfxconnect.moderation.ModerationAuditEvent} (Milestone 9A/
 * ADR-016) — see {@code V11}'s own comment and ADR-017's "Audit Model"
 * section for why reusing that table would corrupt its
 * contribution-review-specific meaning.
 *
 * <p>{@code actorEmail} is a point-in-time snapshot, the same reasoning
 * {@code ModerationAuditEvent.actorEmail} already established.
 * {@code beforeSnapshot}/{@code afterSnapshot} are small, explicitly-built
 * {@code Map<String, Object>} structures — never a serialized entity.
 */
@Entity
@Table(name = "organization_audit_events")
public class OrganizationAuditEvent {

	@Id
	@GeneratedValue
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 30)
	private OrganizationAuditEventType eventType;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "resource_id")
	private UUID resourceId;

	@Column(name = "claim_id")
	private UUID claimId;

	@Column(name = "actor_user_id", nullable = false)
	private UUID actorUserId;

	@Column(name = "actor_email", nullable = false, length = 180)
	private String actorEmail;

	@Column(name = "review_reason", length = 1000)
	private String reviewReason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_snapshot")
	private Map<String, Object> beforeSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_snapshot")
	private Map<String, Object> afterSnapshot;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OrganizationAuditEvent() {
		// required by JPA
	}

	public OrganizationAuditEvent(OrganizationAuditEventType eventType, UUID organizationId, UUID resourceId,
			UUID claimId, UUID actorUserId, String actorEmail, String reviewReason, Map<String, Object> beforeSnapshot,
			Map<String, Object> afterSnapshot) {
		this.eventType = eventType;
		this.organizationId = organizationId;
		this.resourceId = resourceId;
		this.claimId = claimId;
		this.actorUserId = actorUserId;
		this.actorEmail = actorEmail;
		this.reviewReason = reviewReason;
		this.beforeSnapshot = beforeSnapshot;
		this.afterSnapshot = afterSnapshot;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public OrganizationAuditEventType getEventType() {
		return eventType;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getResourceId() {
		return resourceId;
	}

	public UUID getClaimId() {
		return claimId;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public String getActorEmail() {
		return actorEmail;
	}

	public String getReviewReason() {
		return reviewReason;
	}

	public Map<String, Object> getBeforeSnapshot() {
		return beforeSnapshot;
	}

	public Map<String, Object> getAfterSnapshot() {
		return afterSnapshot;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
