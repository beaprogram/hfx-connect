package com.hfxconnect.resourcesubmission;

import com.hfxconnect.category.Category;
import com.hfxconnect.common.error.InvalidContributionStatusException;
import com.hfxconnect.resource.CostType;
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
 * A user-proposed new community resource, awaiting review (Milestone 8B).
 * Never creates a {@code CommunityResource} row and is never surfaced in
 * public search/list/map/nearby results — see
 * {@code V9__create_resource_submissions_and_correction_reports.sql} and
 * ADR-015 for the full schema rationale.
 *
 * <p>{@code submittedByUserId} is a plain {@code UUID} column, not a
 * {@code @ManyToOne} to {@code User} — the same reasoning
 * {@code SavedResource.userId} already established (Milestone 8A): nothing
 * here ever needs to navigate to the owning account's other fields.
 *
 * <p>{@code category} <em>is</em> a lazy, unidirectional {@code @ManyToOne}
 * (no back-collection on {@link Category}) — every read path genuinely
 * needs the category's name/slug to render a response, the same reason
 * {@code CommunityResource.category} and {@code SavedResource.resource} are
 * both lazy {@code @ManyToOne} rather than plain columns.
 */
@Entity
@Table(name = "resource_submissions")
public class ResourceSubmission {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "submitted_by_user_id", nullable = false)
	private UUID submittedByUserId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false, length = 180)
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 180)
	private String normalizedName;

	@Column(name = "short_description", nullable = false, length = 300)
	private String shortDescription;

	@Column(name = "full_description", length = 4000)
	private String fullDescription;

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

	@Column(name = "eligibility_information", length = 1000)
	private String eligibilityInformation;

	@Column(name = "accessibility_information", length = 1000)
	private String accessibilityInformation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubmissionStatus status;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	protected ResourceSubmission() {
		// required by JPA
	}

	public ResourceSubmission(UUID submittedByUserId, Category category, String name, String normalizedName,
			String shortDescription, String fullDescription, String addressLine1, String addressLine2, String city,
			String province, String postalCode, String phone, String email, String websiteUrl, CostType costType,
			String eligibilityInformation, String accessibilityInformation) {
		this.submittedByUserId = submittedByUserId;
		this.category = category;
		this.name = name;
		this.normalizedName = normalizedName;
		this.shortDescription = shortDescription;
		this.fullDescription = fullDescription;
		this.addressLine1 = addressLine1;
		this.addressLine2 = addressLine2;
		this.city = city;
		this.province = province;
		this.postalCode = postalCode;
		this.phone = phone;
		this.email = email;
		this.websiteUrl = websiteUrl;
		this.costType = costType;
		this.eligibilityInformation = eligibilityInformation;
		this.accessibilityInformation = accessibilityInformation;
		this.status = SubmissionStatus.PENDING_REVIEW;
	}

	/**
	 * Withdraws this submission — only legal while it is still
	 * {@link SubmissionStatus#PENDING_REVIEW}; a submission already
	 * withdrawn, approved, or rejected cannot be withdrawn again.
	 */
	public void withdraw() {
		if (status != SubmissionStatus.PENDING_REVIEW) {
			throw new InvalidContributionStatusException(
					"Only a pending-review submission can be withdrawn (current status: " + status + ").");
		}
		this.status = SubmissionStatus.WITHDRAWN;
		this.withdrawnAt = Instant.now();
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.submittedAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getSubmittedByUserId() {
		return submittedByUserId;
	}

	public Category getCategory() {
		return category;
	}

	public String getName() {
		return name;
	}

	public String getNormalizedName() {
		return normalizedName;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public String getFullDescription() {
		return fullDescription;
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

	public String getEligibilityInformation() {
		return eligibilityInformation;
	}

	public String getAccessibilityInformation() {
		return accessibilityInformation;
	}

	public SubmissionStatus getStatus() {
		return status;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getWithdrawnAt() {
		return withdrawnAt;
	}

}
