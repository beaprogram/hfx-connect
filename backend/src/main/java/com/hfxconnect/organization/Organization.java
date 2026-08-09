package com.hfxconnect.organization;

import com.hfxconnect.common.error.InvalidContributionStatusException;
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
 * One organization's profile (Milestone 10A) — see
 * {@code V11__create_organizations_and_resource_claims.sql} and ADR-017 for
 * the full schema/design rationale. Exactly one per owning account (a real
 * database unique index, not just an application check); begins
 * {@link OrganizationVerificationStatus#PENDING_VERIFICATION} and can only
 * reach {@link OrganizationVerificationStatus#VERIFIED}/{@link
 * OrganizationVerificationStatus#REJECTED}/{@link
 * OrganizationVerificationStatus#SUSPENDED} through an {@code ADMIN}
 * decision — no mutator here ever sets verification state directly from
 * caller-supplied input.
 *
 * <p>{@code ownerUserId} is a plain {@code UUID} column, the same
 * "no back-reference needed" reasoning every other contribution-domain
 * owner column in this codebase already established.
 */
@Entity
@Table(name = "organizations")
public class Organization {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Column(nullable = false, length = 180)
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 180)
	private String normalizedName;

	@Column(nullable = false, length = 220)
	private String slug;

	@Column(length = 2000)
	private String description;

	@Column(name = "website_url", length = 500)
	private String websiteUrl;

	@Column(name = "public_email", length = 180)
	private String publicEmail;

	@Column(length = 40)
	private String phone;

	@Column(name = "address_line_1", length = 200)
	private String addressLine1;

	@Column(length = 100)
	private String city;

	@Column(length = 2)
	private String province;

	@Column(name = "postal_code", length = 7)
	private String postalCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", nullable = false, length = 20)
	private OrganizationVerificationStatus verificationStatus;

	@Column(name = "verified_by_user_id")
	private UUID verifiedByUserId;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "verification_reason", length = 1000)
	private String verificationReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Organization() {
		// required by JPA
	}

	public Organization(UUID ownerUserId, String name, String normalizedName, String slug, String description,
			String websiteUrl, String publicEmail, String phone, String addressLine1, String city, String province,
			String postalCode) {
		this.ownerUserId = ownerUserId;
		this.name = name;
		this.normalizedName = normalizedName;
		this.slug = slug;
		this.description = description;
		this.websiteUrl = websiteUrl;
		this.publicEmail = publicEmail;
		this.phone = phone;
		this.addressLine1 = addressLine1;
		this.city = city;
		this.province = province;
		this.postalCode = postalCode;
		this.verificationStatus = OrganizationVerificationStatus.PENDING_VERIFICATION;
	}

	/**
	 * Updates every owner-editable field. Never touches {@code slug}
	 * (stable after creation, the same reasoning {@code CommunityResource}
	 * and {@code Category} slugs already establish) or any verification
	 * field. The caller ({@code OrganizationService}) decides separately
	 * whether this particular update resets verification — see
	 * {@link #resetVerificationToPending()}.
	 */
	public void updateProfile(String name, String normalizedName, String description, String websiteUrl,
			String publicEmail, String phone, String addressLine1, String city, String province, String postalCode) {
		this.name = name;
		this.normalizedName = normalizedName;
		this.description = description;
		this.websiteUrl = websiteUrl;
		this.publicEmail = publicEmail;
		this.phone = phone;
		this.addressLine1 = addressLine1;
		this.city = city;
		this.province = province;
		this.postalCode = postalCode;
	}

	/**
	 * Resets an already-{@link OrganizationVerificationStatus#VERIFIED}
	 * organization back to {@link OrganizationVerificationStatus#PENDING_VERIFICATION}
	 * — used only when the owner changes an identity-significant field (name
	 * or website). Clears the prior verification metadata entirely (not just
	 * the status) since {@code organizations_verification_metadata_check}
	 * requires them present/absent together, and the previous decision no
	 * longer describes the organization's current identity. See ADR-017's
	 * "Verification Reset Policy" section.
	 */
	public void resetVerificationToPending() {
		this.verificationStatus = OrganizationVerificationStatus.PENDING_VERIFICATION;
		this.verifiedByUserId = null;
		this.verifiedAt = null;
		this.verificationReason = null;
	}

	/** Only legal from {@link OrganizationVerificationStatus#PENDING_VERIFICATION}. */
	public void verify(UUID reviewerId, String reason) {
		requirePending();
		this.verificationStatus = OrganizationVerificationStatus.VERIFIED;
		this.verifiedByUserId = reviewerId;
		this.verifiedAt = Instant.now();
		this.verificationReason = reason;
	}

	/** Only legal from {@link OrganizationVerificationStatus#PENDING_VERIFICATION}. */
	public void reject(UUID reviewerId, String reason) {
		requirePending();
		this.verificationStatus = OrganizationVerificationStatus.REJECTED;
		this.verifiedByUserId = reviewerId;
		this.verifiedAt = Instant.now();
		this.verificationReason = reason;
	}

	/** Only legal from {@link OrganizationVerificationStatus#VERIFIED} — see ADR-017's "Organization Suspension" section. */
	public void suspend(UUID reviewerId, String reason) {
		if (verificationStatus != OrganizationVerificationStatus.VERIFIED) {
			throw new InvalidContributionStatusException(
					"Only a verified organization can be suspended (current status: " + verificationStatus + ").");
		}
		this.verificationStatus = OrganizationVerificationStatus.SUSPENDED;
		this.verifiedByUserId = reviewerId;
		this.verifiedAt = Instant.now();
		this.verificationReason = reason;
	}

	private void requirePending() {
		if (verificationStatus != OrganizationVerificationStatus.PENDING_VERIFICATION) {
			throw new InvalidContributionStatusException(
					"Only a pending-verification organization can be reviewed (current status: " + verificationStatus + ").");
		}
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

	public UUID getOwnerUserId() {
		return ownerUserId;
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

	public String getWebsiteUrl() {
		return websiteUrl;
	}

	public String getPublicEmail() {
		return publicEmail;
	}

	public String getPhone() {
		return phone;
	}

	public String getAddressLine1() {
		return addressLine1;
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

	public OrganizationVerificationStatus getVerificationStatus() {
		return verificationStatus;
	}

	public UUID getVerifiedByUserId() {
		return verifiedByUserId;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public String getVerificationReason() {
		return verificationReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
