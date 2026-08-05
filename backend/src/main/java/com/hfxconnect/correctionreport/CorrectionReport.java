package com.hfxconnect.correctionreport;

import com.hfxconnect.common.error.InvalidContributionStatusException;
import com.hfxconnect.resource.CommunityResource;
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
 * A user-reported issue with an existing, active community resource
 * (Milestone 8B). Never modifies the target resource and is never
 * surfaced in public search/list/map/nearby results — see
 * {@code V9__create_resource_submissions_and_correction_reports.sql} and
 * ADR-015 for the full schema rationale.
 *
 * <p>{@code reportedByUserId} is a plain {@code UUID} column, the same
 * reasoning {@code ResourceSubmission.submittedByUserId} and
 * {@code SavedResource.userId} already established.
 *
 * <p>{@code resource} is a lazy, unidirectional, <em>nullable</em>
 * {@code @ManyToOne} (no back-collection on {@link CommunityResource}) —
 * nullable because the foreign key is {@code ON DELETE SET NULL}: an
 * unresolved report outlives its target resource being deleted.
 * {@code resourceNameSnapshot}/{@code resourceSlugSnapshot} are captured
 * once at creation specifically so the report stays meaningful to its
 * owner even after {@code resource} becomes {@code null} — display code
 * should read the snapshot fields, never assume {@code resource} is
 * present.
 */
@Entity
@Table(name = "correction_reports")
public class CorrectionReport {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "reported_by_user_id", nullable = false)
	private UUID reportedByUserId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resource_id")
	private CommunityResource resource;

	@Column(name = "resource_name_snapshot", nullable = false, length = 180)
	private String resourceNameSnapshot;

	@Column(name = "resource_slug_snapshot", nullable = false, length = 220)
	private String resourceSlugSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(name = "issue_type", nullable = false, length = 30)
	private IssueType issueType;

	@Column(nullable = false, length = 2000)
	private String explanation;

	@Column(name = "proposed_name", length = 180)
	private String proposedName;

	@Column(name = "proposed_description", length = 4000)
	private String proposedDescription;

	@Column(name = "proposed_address_line_1", length = 200)
	private String proposedAddressLine1;

	@Column(name = "proposed_address_line_2", length = 200)
	private String proposedAddressLine2;

	@Column(name = "proposed_city", length = 100)
	private String proposedCity;

	@Column(name = "proposed_province", length = 2)
	private String proposedProvince;

	@Column(name = "proposed_postal_code", length = 7)
	private String proposedPostalCode;

	@Column(name = "proposed_phone", length = 40)
	private String proposedPhone;

	@Column(name = "proposed_email", length = 180)
	private String proposedEmail;

	@Column(name = "proposed_website_url", length = 500)
	private String proposedWebsiteUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "proposed_cost_type", length = 20)
	private CostType proposedCostType;

	@Column(name = "proposed_cost_details", length = 500)
	private String proposedCostDetails;

	@Column(name = "proposed_eligibility", length = 1000)
	private String proposedEligibility;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CorrectionReportStatus status;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	protected CorrectionReport() {
		// required by JPA
	}

	public CorrectionReport(UUID reportedByUserId, CommunityResource resource, IssueType issueType,
			String explanation, String proposedName, String proposedDescription, String proposedAddressLine1,
			String proposedAddressLine2, String proposedCity, String proposedProvince, String proposedPostalCode,
			String proposedPhone, String proposedEmail, String proposedWebsiteUrl, CostType proposedCostType,
			String proposedCostDetails, String proposedEligibility) {
		this.reportedByUserId = reportedByUserId;
		this.resource = resource;
		this.resourceNameSnapshot = resource.getName();
		this.resourceSlugSnapshot = resource.getSlug();
		this.issueType = issueType;
		this.explanation = explanation;
		this.proposedName = proposedName;
		this.proposedDescription = proposedDescription;
		this.proposedAddressLine1 = proposedAddressLine1;
		this.proposedAddressLine2 = proposedAddressLine2;
		this.proposedCity = proposedCity;
		this.proposedProvince = proposedProvince;
		this.proposedPostalCode = proposedPostalCode;
		this.proposedPhone = proposedPhone;
		this.proposedEmail = proposedEmail;
		this.proposedWebsiteUrl = proposedWebsiteUrl;
		this.proposedCostType = proposedCostType;
		this.proposedCostDetails = proposedCostDetails;
		this.proposedEligibility = proposedEligibility;
		this.status = CorrectionReportStatus.PENDING_REVIEW;
	}

	/**
	 * Withdraws this report — only legal while it is still
	 * {@link CorrectionReportStatus#PENDING_REVIEW}.
	 */
	public void withdraw() {
		if (status != CorrectionReportStatus.PENDING_REVIEW) {
			throw new InvalidContributionStatusException(
					"Only a pending-review correction report can be withdrawn (current status: " + status + ").");
		}
		this.status = CorrectionReportStatus.WITHDRAWN;
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

	public UUID getReportedByUserId() {
		return reportedByUserId;
	}

	public CommunityResource getResource() {
		return resource;
	}

	public String getResourceNameSnapshot() {
		return resourceNameSnapshot;
	}

	public String getResourceSlugSnapshot() {
		return resourceSlugSnapshot;
	}

	public IssueType getIssueType() {
		return issueType;
	}

	public String getExplanation() {
		return explanation;
	}

	public String getProposedName() {
		return proposedName;
	}

	public String getProposedDescription() {
		return proposedDescription;
	}

	public String getProposedAddressLine1() {
		return proposedAddressLine1;
	}

	public String getProposedAddressLine2() {
		return proposedAddressLine2;
	}

	public String getProposedCity() {
		return proposedCity;
	}

	public String getProposedProvince() {
		return proposedProvince;
	}

	public String getProposedPostalCode() {
		return proposedPostalCode;
	}

	public String getProposedPhone() {
		return proposedPhone;
	}

	public String getProposedEmail() {
		return proposedEmail;
	}

	public String getProposedWebsiteUrl() {
		return proposedWebsiteUrl;
	}

	public CostType getProposedCostType() {
		return proposedCostType;
	}

	public String getProposedCostDetails() {
		return proposedCostDetails;
	}

	public String getProposedEligibility() {
		return proposedEligibility;
	}

	public CorrectionReportStatus getStatus() {
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
