package com.hfxconnect.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One day's operating-hours entry for a resource. See ADR-011 and
 * {@code V6__create_resource_operating_hours.sql} for the full design.
 *
 * <p>{@code resourceId} is a plain {@code UUID} column, not a
 * {@code @ManyToOne} relationship — nothing in this domain ever needs to
 * navigate from an hours row back to its owning {@link CommunityResource}
 * entity (the batch-loading read path in {@code ResourceService} groups rows
 * by id in memory instead; see ADR-011's "Batch-Loading" section), so a
 * relationship would only add lazy-loading risk with no benefit.
 *
 * <p>No public setters: a schedule is always replaced wholesale
 * (delete-then-insert, see {@code ResourceService#replaceOperatingHours}),
 * never mutated field-by-field.
 */
@Entity
@Table(name = "resource_operating_hours")
public class ResourceOperatingHours {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "resource_id", nullable = false)
	private UUID resourceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 9)
	private DayOfWeek dayOfWeek;

	@Column(name = "opens_at")
	private LocalTime opensAt;

	@Column(name = "closes_at")
	private LocalTime closesAt;

	@Column(nullable = false)
	private boolean closed;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ResourceOperatingHours() {
		// required by JPA
	}

	public ResourceOperatingHours(UUID resourceId, DayOfWeek dayOfWeek, boolean closed, LocalTime opensAt,
			LocalTime closesAt) {
		this.resourceId = resourceId;
		this.dayOfWeek = dayOfWeek;
		this.closed = closed;
		this.opensAt = opensAt;
		this.closesAt = closesAt;
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

	public UUID getResourceId() {
		return resourceId;
	}

	public DayOfWeek getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalTime getOpensAt() {
		return opensAt;
	}

	public LocalTime getClosesAt() {
		return closesAt;
	}

	public boolean isClosed() {
		return closed;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
