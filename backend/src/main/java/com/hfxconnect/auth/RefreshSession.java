package com.hfxconnect.auth;

import com.hfxconnect.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One issued (or rotated-away) refresh token. See ADR-008 for the full
 * rotation/reuse-detection design this entity supports.
 *
 * <p>No setters beyond the specific mutations {@code RefreshSessionService}
 * genuinely needs ({@link #markUsed()}, {@link #revoke()},
 * {@link #replaceWith(UUID)}) — mirrors every other entity in this project
 * (see {@code docs/architecture/backend-architecture.md}).
 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_session_id")
	private UUID replacedBySessionId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	protected RefreshSession() {
		// required by JPA
	}

	/** A brand-new login: starts a new rotation family (its own {@code id} will become the family's {@code family_id}). */
	public RefreshSession(User user, String tokenHash, UUID familyId, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public boolean isExpired() {
		return Instant.now().isAfter(expiresAt);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void markUsed() {
		this.lastUsedAt = Instant.now();
	}

	/** Marks this session revoked because {@code replacement}'s id continues its rotation family. */
	public void replaceWith(UUID replacementSessionId) {
		this.revokedAt = Instant.now();
		this.replacedBySessionId = replacementSessionId;
	}

	/** Marks this session revoked directly — logout, or reuse-detection's family-wide revocation. */
	public void revoke() {
		this.revokedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public UUID getReplacedBySessionId() {
		return replacedBySessionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

}
