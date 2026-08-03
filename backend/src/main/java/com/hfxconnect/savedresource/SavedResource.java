package com.hfxconnect.savedresource;

import com.hfxconnect.resource.CommunityResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One user's "saved for later" relation to one active public resource
 * (Milestone 8A). See {@code V8__create_saved_resources_table.sql} for the
 * full schema rationale.
 *
 * <p>{@code userId} is a plain {@code UUID} column, not a {@code @ManyToOne}
 * to {@link com.hfxconnect.user.User} — this entity never needs to navigate
 * to the owning account's other fields (password hash, role, status; every
 * authorization decision already has the authenticated principal's data
 * before this entity is ever touched), and a relationship would only add
 * lazy-loading risk with no benefit. This is the same reasoning
 * {@code ResourceOperatingHours.resourceId} already established for its own
 * plain-column foreign key.
 *
 * <p>{@code resource} <em>is</em> a {@code @ManyToOne} (lazy, unidirectional
 * — no {@code @OneToMany} back-collection on {@link CommunityResource}):
 * unlike {@code userId}, every saved-resource read path genuinely needs the
 * resource's own fields (name, slug, category, ...) to render a response,
 * the same reason {@code CommunityResource.category} is a lazy
 * {@code @ManyToOne} rather than a plain column.
 *
 * <p>No public setters: a saved relation is either created or deleted, never
 * mutated field-by-field. No {@code equals}/{@code hashCode} override,
 * matching every other entity in this codebase ({@code User},
 * {@code CommunityResource}, {@code ResourceOperatingHours} all rely on
 * default identity) — deliberately not adding one here avoids the risk of an
 * override that traverses the lazy {@code resource} relationship.
 */
@Entity
@Table(name = "saved_resources")
public class SavedResource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resource_id", nullable = false)
	private CommunityResource resource;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SavedResource() {
		// required by JPA
	}

	public SavedResource(UUID userId, CommunityResource resource) {
		this.userId = userId;
		this.resource = resource;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public CommunityResource getResource() {
		return resource;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
