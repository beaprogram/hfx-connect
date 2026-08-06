package com.hfxconnect.moderation;

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
 * One row per concrete moderation effect (Milestone 9A) — append-only, never
 * updated or deleted by application code. See
 * {@code V10__add_moderation_workflow_and_audit.sql} and ADR-016 for the
 * full schema/design rationale, and {@link ModerationAction}'s Javadoc for
 * exactly when one review decision produces one row versus more than one.
 *
 * <p>{@code contributionId} is a plain {@code UUID}, not a JPA relation — it
 * addresses either a {@code resource_submissions} or a
 * {@code correction_reports} row depending on {@code contributionType},
 * which Postgres/JPA has no polymorphic-reference mechanism for. {@code
 * actorUserId}/{@code actorEmail} follow the same "plain id column, no
 * {@code @ManyToOne}" convention every other contribution-domain owner
 * reference in this codebase already uses ({@code
 * ResourceSubmission.submittedByUserId}, and so on) — {@code actorEmail} is
 * a point-in-time snapshot for the same reason {@code CorrectionReport}
 * already snapshots its target resource's name/slug (V9): this row stays
 * independently readable even if the account's email later changes.
 *
 * <p>{@code beforeSnapshot}/{@code afterSnapshot} are small, explicitly
 * built {@code Map<String, Object>} structures — never a serialized entity —
 * constructed field-by-field by the review services. See ADR-016's
 * "Snapshot Policy" section for exactly what each {@link ModerationAction}
 * stores.
 */
@Entity
@Table(name = "moderation_audit_events")
public class ModerationAuditEvent {

	@Id
	@GeneratedValue
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "contribution_type", nullable = false, length = 30)
	private ContributionType contributionType;

	@Column(name = "contribution_id", nullable = false)
	private UUID contributionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ModerationAction action;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ModerationDecision decision;

	@Column(name = "actor_user_id", nullable = false)
	private UUID actorUserId;

	@Column(name = "actor_email", nullable = false, length = 180)
	private String actorEmail;

	@Column(name = "review_reason", length = 1000)
	private String reviewReason;

	@Column(name = "affected_resource_id")
	private UUID affectedResourceId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_snapshot")
	private Map<String, Object> beforeSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_snapshot")
	private Map<String, Object> afterSnapshot;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ModerationAuditEvent() {
		// required by JPA
	}

	public ModerationAuditEvent(ContributionType contributionType, UUID contributionId, ModerationAction action,
			ModerationDecision decision, UUID actorUserId, String actorEmail, String reviewReason,
			UUID affectedResourceId, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
		this.contributionType = contributionType;
		this.contributionId = contributionId;
		this.action = action;
		this.decision = decision;
		this.actorUserId = actorUserId;
		this.actorEmail = actorEmail;
		this.reviewReason = reviewReason;
		this.affectedResourceId = affectedResourceId;
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

	public ContributionType getContributionType() {
		return contributionType;
	}

	public UUID getContributionId() {
		return contributionId;
	}

	public ModerationAction getAction() {
		return action;
	}

	public ModerationDecision getDecision() {
		return decision;
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

	public UUID getAffectedResourceId() {
		return affectedResourceId;
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
