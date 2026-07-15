package com.hfxconnect.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A category classifying community resources (Food Assistance, Study Spaces,
 * ...). See ADR-005 for why categories use a numeric identity key rather than
 * a UUID, and for the exact name-normalization and slug rules.
 *
 * <p>There is deliberately no update endpoint in Milestone 3A, so this entity
 * has no setters — it is created once via the constructor and otherwise only
 * read. Setters will be added if and when a real update requirement exists.
 */
@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 120)
	private String normalizedName;

	@Column(nullable = false, length = 160)
	private String slug;

	@Column(length = 2000)
	private String description;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Category() {
		// required by JPA
	}

	public Category(String name, String normalizedName, String slug, String description) {
		this.name = name;
		this.normalizedName = normalizedName;
		this.slug = slug;
		this.description = description;
		this.active = true;
	}

	/** Package-private: lets same-package unit tests build a fully-populated instance without a persistence context. */
	Category(Long id, String name, String normalizedName, String slug, String description, boolean active,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.normalizedName = normalizedName;
		this.slug = slug;
		this.description = description;
		this.active = active;
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

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getNormalizedName() {
		return normalizedName;
	}

	public String getSlug() {
		return slug;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
