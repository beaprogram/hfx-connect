package com.hfxconnect.resource;

import com.hfxconnect.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A real Halifax community service (food assistance, a study space,
 * newcomer support, ...), classified by a {@link Category}.
 *
 * <p>Named {@code CommunityResource} rather than {@code Resource} to avoid
 * colliding with {@code org.springframework.core.io.Resource} and similar
 * JDK/framework types.
 *
 * <p>The category relationship is {@link FetchType#LAZY} and carries no
 * cascade behavior — deleting a resource never touches its category, and the
 * database's {@code ON DELETE RESTRICT} (see {@code V3__create_resources_table.sql})
 * is what actually prevents deleting a category that still has resources;
 * this entity does not attempt to reimplement that rule in Java.
 *
 * <p>There is no HTTP API yet (Milestone 3C), so this entity has no public
 * setters at all — only the focused {@link #updateDetails},
 * {@link #deactivate()}, and (Milestone 9A) {@link #markVerified} mutation
 * methods {@link ResourceService} and the moderation workflow use. The slug
 * is never mutated after creation.
 */
@Entity
@Table(name = "resources")
public class CommunityResource {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false, length = 180)
	private String name;

	@Column(nullable = false, length = 220)
	private String slug;

	@Column(nullable = false, length = 4000)
	private String description;

	@Column(name = "address_line_1", nullable = false, length = 200)
	private String addressLine1;

	@Column(name = "address_line_2", length = 200)
	private String addressLine2;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(nullable = false, length = 2)
	private String province;

	@Column(name = "postal_code", nullable = false, length = 7)
	private String postalCode;

	@Column(length = 40)
	private String phone;

	@Column(length = 180)
	private String email;

	@Column(name = "website_url", length = 500)
	private String websiteUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "cost_type", nullable = false, length = 20)
	private CostType costType;

	@Column(name = "cost_details", length = 500)
	private String costDetails;

	@Column(length = 1000)
	private String eligibility;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", nullable = false, length = 20)
	private VerificationStatus verificationStatus;

	@Column(name = "last_verified_at")
	private Instant lastVerifiedAt;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CommunityResource() {
		// required by JPA
	}

	public CommunityResource(Category category, String name, String slug, String description,
			String addressLine1, String addressLine2, String city, String province, String postalCode,
			String phone, String email, String websiteUrl, CostType costType, String costDetails,
			String eligibility) {
		this.category = category;
		this.name = name;
		this.slug = slug;
		this.description = description;
		this.addressLine1 = addressLine1;
		this.addressLine2 = addressLine2;
		this.city = city;
		this.province = province;
		this.postalCode = postalCode;
		this.phone = phone;
		this.email = email;
		this.websiteUrl = websiteUrl;
		this.costType = costType;
		this.costDetails = costDetails;
		this.eligibility = eligibility;
		this.verificationStatus = VerificationStatus.UNVERIFIED;
		this.active = true;
	}

	/**
	 * Updates every editable business field. Deliberately excludes the slug
	 * (stable for the resource's lifetime — see ADR-005's slug-stability
	 * reasoning, which applies equally here), {@code id}, and
	 * {@code verificationStatus} (moderator-controlled, Milestone 9).
	 */
	public void updateDetails(Category category, String name, String description, String addressLine1,
			String addressLine2, String city, String province, String postalCode, String phone, String email,
			String websiteUrl, CostType costType, String costDetails, String eligibility) {
		this.category = category;
		this.name = name;
		this.description = description;
		this.addressLine1 = addressLine1;
		this.addressLine2 = addressLine2;
		this.city = city;
		this.province = province;
		this.postalCode = postalCode;
		this.phone = phone;
		this.email = email;
		this.websiteUrl = websiteUrl;
		this.costType = costType;
		this.costDetails = costDetails;
		this.eligibility = eligibility;
	}

	public void deactivate() {
		this.active = false;
	}

	/**
	 * Marks this resource {@link VerificationStatus#VERIFIED} with the given
	 * timestamp — used only by the Milestone 9A moderation workflow, when a
	 * MODERATOR or ADMIN approves a resource submission (at creation) or a
	 * correction report (after applying its changes). Justified because a
	 * moderator reviewed the complete record; no mutator exists for setting
	 * {@code verificationStatus} back to {@link VerificationStatus#UNVERIFIED}
	 * — nothing in this milestone's scope needs that direction.
	 */
	public void markVerified(Instant at) {
		this.verificationStatus = VerificationStatus.VERIFIED;
		this.lastVerifiedAt = at;
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

	public Category getCategory() {
		return category;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getDescription() {
		return description;
	}

	public String getAddressLine1() {
		return addressLine1;
	}

	public String getAddressLine2() {
		return addressLine2;
	}

	public String getCity() {
		return city;
	}

	public String getProvince() {
		return province;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	public String getWebsiteUrl() {
		return websiteUrl;
	}

	public CostType getCostType() {
		return costType;
	}

	public String getCostDetails() {
		return costDetails;
	}

	public String getEligibility() {
		return eligibility;
	}

	public VerificationStatus getVerificationStatus() {
		return verificationStatus;
	}

	public Instant getLastVerifiedAt() {
		return lastVerifiedAt;
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
