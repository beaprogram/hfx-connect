package com.hfxconnect.user;

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
 * A registered HFX Connect account. See ADR-007 for why {@code id} is
 * {@link UUID} (like {@code CommunityResource}, not {@code Category}) and why
 * a newly-registered account is {@link AccountStatus#ACTIVE} with
 * {@code emailVerified = false} rather than {@link AccountStatus#PENDING_VERIFICATION}.
 *
 * <p>There is no login, token, or role-management endpoint yet (Milestone
 * 5A), so this entity has no public setters at all — only what
 * {@code RegistrationService} genuinely needs to construct a new account.
 * {@code passwordHash} is never exposed by a getter used outside this
 * package's persistence layer's own needs beyond what {@code UserRepository}
 * requires — see {@code UserResponse}, which never reads it.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 180)
	private String email;

	@Column(name = "normalized_email", nullable = false, length = 180)
	private String normalizedEmail;

	@Column(name = "password_hash", nullable = false, length = 200)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AccountStatus status;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
		// required by JPA
	}

	/**
	 * Always constructs a {@link Role#USER}, {@link AccountStatus#ACTIVE},
	 * unverified account — see ADR-007. There is deliberately no constructor
	 * parameter for role or status: privilege escalation through registration
	 * input is impossible because there is no code path that accepts a
	 * caller-supplied value for either field.
	 */
	public User(String email, String normalizedEmail, String passwordHash) {
		this.email = email;
		this.normalizedEmail = normalizedEmail;
		this.passwordHash = passwordHash;
		this.role = Role.USER;
		this.status = AccountStatus.ACTIVE;
		this.emailVerified = false;
	}

	/** Package-private: lets same-package unit tests build a fully-populated instance without a persistence context. */
	User(UUID id, String email, String normalizedEmail, String passwordHash, Role role, AccountStatus status,
			boolean emailVerified, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.email = email;
		this.normalizedEmail = normalizedEmail;
		this.passwordHash = passwordHash;
		this.role = role;
		this.status = status;
		this.emailVerified = emailVerified;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
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

	public String getEmail() {
		return email;
	}

	public String getNormalizedEmail() {
		return normalizedEmail;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
