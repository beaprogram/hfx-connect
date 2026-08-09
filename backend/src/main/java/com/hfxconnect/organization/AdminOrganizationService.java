package com.hfxconnect.organization;

import com.hfxconnect.common.error.ContributionAlreadyReviewedException;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.SelfReviewNotAllowedException;
import com.hfxconnect.moderation.ModerationValidation;
import com.hfxconnect.security.CurrentUserPrincipal;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ADMIN}-only organization verification: the queue, detail view, and
 * the verify/reject/suspend decisions themselves. Mirrors {@code
 * com.hfxconnect.moderation.ResourceSubmissionReviewService}'s exact
 * concurrency posture (a row-level {@code PESSIMISTIC_WRITE} lock for the
 * whole decision transaction, reused per ADR-017) and self-review posture
 * (no {@code ADMIN} bypass — an admin who owns the target organization may
 * not decide on it, matching ADR-016's own reasoning exactly).
 */
@Service
public class AdminOrganizationService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("createdAt", "name");

	private final OrganizationRepository organizationRepository;
	private final OrganizationAuditRecorder auditRecorder;

	public AdminOrganizationService(OrganizationRepository organizationRepository, OrganizationAuditRecorder auditRecorder) {
		this.organizationRepository = organizationRepository;
		this.auditRecorder = auditRecorder;
	}

	/** Defaults to {@code PENDING_VERIFICATION} — a missing {@code status} means "the default review queue," not "every organization." */
	@Transactional(readOnly = true)
	public AdminOrganizationQueuePageResponse queue(OrganizationVerificationStatus status, int page, int size, String sort) {
		OrganizationVerificationStatus effectiveStatus = status != null ? status : OrganizationVerificationStatus.PENDING_VERIFICATION;
		Pageable pageable = pageable(page, size, sort);
		Page<Organization> results = organizationRepository.findForAdminQueue(effectiveStatus, pageable);
		return new AdminOrganizationQueuePageResponse(
				results.getContent().stream().map(AdminOrganizationQueueItemResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	@Transactional(readOnly = true)
	public AdminOrganizationDetailResponse detail(UUID organizationId) {
		Organization organization = organizationRepository.findById(organizationId)
				.orElseThrow(() -> OrganizationNotFoundException.byId(organizationId));
		return AdminOrganizationDetailResponse.from(organization);
	}

	@Transactional
	public AdminOrganizationDetailResponse verify(CurrentUserPrincipal admin, UUID organizationId, OrganizationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		Organization organization = organizationRepository.findByIdForReview(organizationId)
				.orElseThrow(() -> OrganizationNotFoundException.byId(organizationId));

		requireNotSelfReview(admin.userId(), organization.getOwnerUserId());
		requirePending(organization);

		organization.verify(admin.userId(), reason);

		auditRecorder.record(OrganizationAuditEventType.ORGANIZATION_VERIFIED, organization.getId(), null, null, admin,
				reason, null, OrganizationSnapshots.of(organization));

		return AdminOrganizationDetailResponse.from(organization);
	}

	@Transactional
	public AdminOrganizationDetailResponse reject(CurrentUserPrincipal admin, UUID organizationId, OrganizationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		Organization organization = organizationRepository.findByIdForReview(organizationId)
				.orElseThrow(() -> OrganizationNotFoundException.byId(organizationId));

		requireNotSelfReview(admin.userId(), organization.getOwnerUserId());
		requirePending(organization);

		organization.reject(admin.userId(), reason);

		auditRecorder.record(OrganizationAuditEventType.ORGANIZATION_REJECTED, organization.getId(), null, null, admin,
				reason, null, OrganizationSnapshots.of(organization));

		return AdminOrganizationDetailResponse.from(organization);
	}

	/** Only legal from {@code VERIFIED} — see {@link Organization#suspend}. Owned resources are never touched here (ADR-017's "Organization Suspension" section). */
	@Transactional
	public AdminOrganizationDetailResponse suspend(CurrentUserPrincipal admin, UUID organizationId, OrganizationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		Organization organization = organizationRepository.findByIdForReview(organizationId)
				.orElseThrow(() -> OrganizationNotFoundException.byId(organizationId));

		requireNotSelfReview(admin.userId(), organization.getOwnerUserId());

		organization.suspend(admin.userId(), reason);

		auditRecorder.record(OrganizationAuditEventType.ORGANIZATION_SUSPENDED, organization.getId(), null, null, admin,
				reason, null, OrganizationSnapshots.of(organization));

		return AdminOrganizationDetailResponse.from(organization);
	}

	private static void requireNotSelfReview(UUID adminId, UUID ownerId) {
		if (adminId.equals(ownerId)) {
			throw new SelfReviewNotAllowedException();
		}
	}

	private static void requirePending(Organization organization) {
		if (organization.getVerificationStatus() != OrganizationVerificationStatus.PENDING_VERIFICATION) {
			throw new ContributionAlreadyReviewedException();
		}
	}

	private static Pageable pageable(int page, int size, String sort) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size, resolveSort(sort));
	}

	/** Default: {@code createdAt} ascending (oldest submitted first, mirroring the moderation queue's default). */
	private static Sort resolveSort(String sort) {
		if (sort == null || sort.isBlank() || "createdAt".equals(sort)) {
			return Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
		}
		if ("name".equals(sort)) {
			return Sort.by(Sort.Order.asc("normalizedName"), Sort.Order.asc("id"));
		}
		throw new InvalidSortException("sort must be one of " + ALLOWED_SORT_VALUES + " (got '" + sort + "').");
	}

}
