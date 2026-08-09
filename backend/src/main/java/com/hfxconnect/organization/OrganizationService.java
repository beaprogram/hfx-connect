package com.hfxconnect.organization;

import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.common.text.SlugGenerator;
import com.hfxconnect.security.CurrentUserPrincipal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current authenticated {@code ORGANIZATION} account's own organization
 * profile: create, read, update. Every method is scoped by the {@code
 * userId} the caller ({@code OrganizationController}) derives exclusively
 * from the authenticated {@code CurrentUserPrincipal} — nothing here ever
 * accepts an owner id, verification status, or reviewer field as request
 * data, matching {@code ResourceSubmissionService}'s established pattern
 * (Milestone 8B).
 */
@Service
public class OrganizationService {

	private final OrganizationRepository organizationRepository;
	private final OrganizationAuditRecorder auditRecorder;

	public OrganizationService(OrganizationRepository organizationRepository, OrganizationAuditRecorder auditRecorder) {
		this.organizationRepository = organizationRepository;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * Creates the current user's organization profile — always begins
	 * {@link OrganizationVerificationStatus#PENDING_VERIFICATION}; there is
	 * no code path here that could ever start it {@code VERIFIED}. {@code
	 * organizations_owner_user_id_key} (V11) is the actual "one organization
	 * per account" authority — the check below narrows the race window but
	 * does not close it, the same "database constraint is the authority"
	 * posture every duplicate-guard in this project already takes.
	 */
	@Transactional
	public OrganizationResponse create(CurrentUserPrincipal principal, OrganizationProfileRequest request) {
		if (organizationRepository.findByOwnerUserId(principal.userId()).isPresent()) {
			throw OrganizationConflictException.accountAlreadyHasProfile();
		}

		OrganizationValidation.Normalized fields = OrganizationValidation.validate(request.name(), request.description(),
				request.websiteUrl(), request.publicEmail(), request.phone(), request.addressLine1(), request.city(),
				request.province(), request.postalCode());

		String slug = generateUniqueSlug(fields.name());

		Organization organization = new Organization(principal.userId(), fields.name(), fields.normalizedName(), slug,
				fields.description(), fields.websiteUrl(), fields.publicEmail(), fields.phone(), fields.addressLine1(),
				fields.city(), fields.province(), fields.postalCode());

		Organization saved;
		try {
			// saveAndFlush: Organization.id is a Hibernate-generated UUID, not
			// IDENTITY — see ResourceService.createInternal's identical
			// reasoning for why a plain save() would not make the
			// race-condition catch below reliable.
			saved = organizationRepository.saveAndFlush(organization);
		} catch (DataIntegrityViolationException ex) {
			throw OrganizationConflictException.accountAlreadyHasProfile();
		}

		auditRecorder.record(OrganizationAuditEventType.ORGANIZATION_SUBMITTED, saved.getId(), null, null, principal,
				null, null, OrganizationSnapshots.of(saved));

		return OrganizationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public OrganizationResponse getCurrent(UUID ownerUserId) {
		Organization organization = organizationRepository.findByOwnerUserId(ownerUserId)
				.orElseThrow(OrganizationNotFoundException::forCurrentUser);
		return OrganizationResponse.from(organization);
	}

	/**
	 * Updates the current user's profile. Identity-significant fields
	 * ({@code name}, {@code websiteUrl}) reset an already-{@code VERIFIED}
	 * organization back to {@code PENDING_VERIFICATION} — see ADR-017's
	 * "Verification Reset Policy" section. Contact-detail-only changes
	 * (description, email, phone, address) never reset verification.
	 */
	@Transactional
	public OrganizationResponse update(UUID ownerUserId, OrganizationProfileRequest request) {
		Organization organization = organizationRepository.findByOwnerUserId(ownerUserId)
				.orElseThrow(OrganizationNotFoundException::forCurrentUser);

		OrganizationValidation.Normalized fields = OrganizationValidation.validate(request.name(), request.description(),
				request.websiteUrl(), request.publicEmail(), request.phone(), request.addressLine1(), request.city(),
				request.province(), request.postalCode());

		boolean identityChanged = !Objects.equals(organization.getNormalizedName(), fields.normalizedName())
				|| !Objects.equals(organization.getWebsiteUrl(), fields.websiteUrl());

		organization.updateProfile(fields.name(), fields.normalizedName(), fields.description(), fields.websiteUrl(),
				fields.publicEmail(), fields.phone(), fields.addressLine1(), fields.city(), fields.province(),
				fields.postalCode());

		if (identityChanged && organization.getVerificationStatus() == OrganizationVerificationStatus.VERIFIED) {
			organization.resetVerificationToPending();
		}

		return OrganizationResponse.from(organization);
	}

	/** Public — only ever returns a currently-{@code VERIFIED} organization; anything else is indistinguishable from a nonexistent slug. */
	@Transactional(readOnly = true)
	public PublicOrganizationResponse getPublicBySlug(String slug) {
		Organization organization = organizationRepository
				.findBySlugAndVerificationStatus(slug, OrganizationVerificationStatus.VERIFIED)
				.orElseThrow(() -> OrganizationNotFoundException.bySlug(slug));
		return PublicOrganizationResponse.from(organization);
	}

	private String generateUniqueSlug(String name) {
		String baseSlug = SlugGenerator.generate(name)
				.orElseThrow(() -> new ValidationException("The submitted organization profile contains invalid information.",
						Map.of("name", "Organization name must contain at least one letter or number.")));
		if (!organizationRepository.existsBySlug(baseSlug)) {
			return baseSlug;
		}
		throw OrganizationConflictException.duplicateSlug(baseSlug);
	}

}
