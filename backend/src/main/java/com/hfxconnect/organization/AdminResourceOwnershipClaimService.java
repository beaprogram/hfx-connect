package com.hfxconnect.organization;

import com.hfxconnect.common.error.ContributionAlreadyReviewedException;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.SelfReviewNotAllowedException;
import com.hfxconnect.moderation.ModerationValidation;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.ResourceNotFoundException;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.security.CurrentUserPrincipal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ADMIN}-only resource-ownership-claim review: the queue, detail
 * view, and the approve/reject decisions. {@link #approve} is this
 * milestone's most concurrency-sensitive operation — see its own Javadoc and
 * ADR-017's "Concurrency Strategy" section for why it locks <em>both</em>
 * the claim row and the target resource row, not just the claim.
 */
@Service
public class AdminResourceOwnershipClaimService {

	static final int MAX_PAGE_SIZE = 100;

	private final ResourceOwnershipClaimRepository claimRepository;
	private final OrganizationRepository organizationRepository;
	private final ResourceRepository resourceRepository;
	private final OrganizationAuditRecorder auditRecorder;

	public AdminResourceOwnershipClaimService(ResourceOwnershipClaimRepository claimRepository,
			OrganizationRepository organizationRepository, ResourceRepository resourceRepository,
			OrganizationAuditRecorder auditRecorder) {
		this.claimRepository = claimRepository;
		this.organizationRepository = organizationRepository;
		this.resourceRepository = resourceRepository;
		this.auditRecorder = auditRecorder;
	}

	/** Defaults to {@code PENDING_REVIEW}, oldest requested first. Independently optional {@code organizationId} filter. */
	@Transactional(readOnly = true)
	public AdminOwnershipClaimQueuePageResponse queue(
			ResourceOwnershipClaimStatus status, UUID organizationId, int page, int size) {
		ResourceOwnershipClaimStatus effectiveStatus = status != null ? status : ResourceOwnershipClaimStatus.PENDING_REVIEW;
		Pageable pageable = pageable(page, size);
		Page<ResourceOwnershipClaim> results = claimRepository.findForAdminQueue(effectiveStatus, organizationId, pageable);

		Map<UUID, String> organizationNamesById = loadOrganizationNames(results.getContent());
		Map<UUID, ClaimedResourceSummaryResponse> resourcesById = loadResourceSummaries(results.getContent());

		List<AdminOwnershipClaimQueueItemResponse> content = results.getContent().stream()
				.map(claim -> new AdminOwnershipClaimQueueItemResponse(
						claim.getId(), claim.getOrganizationId(), organizationNamesById.get(claim.getOrganizationId()),
						resourcesById.get(claim.getResourceId()), claim.getStatus(), claim.getRequestedAt()))
				.toList();
		return new AdminOwnershipClaimQueuePageResponse(content, results.getNumber(), results.getSize(),
				results.getTotalElements(), results.getTotalPages());
	}

	@Transactional(readOnly = true)
	public AdminOwnershipClaimDetailResponse detail(UUID claimId) {
		ResourceOwnershipClaim claim = claimRepository.findById(claimId)
				.orElseThrow(() -> ResourceOwnershipClaimNotFoundException.byId(claimId));
		Organization organization = organizationRepository.findById(claim.getOrganizationId())
				.orElseThrow(() -> OrganizationNotFoundException.byId(claim.getOrganizationId()));
		CommunityResource resource = resourceRepository.findByIdWithCategory(claim.getResourceId())
				.orElseThrow(() -> ResourceNotFoundException.byId(claim.getResourceId()));
		return toDetailResponse(claim, organization, resource);
	}

	/**
	 * The full atomic approval transaction: lock the claim, block self-
	 * review, re-verify the organization is still {@code VERIFIED}, lock and
	 * re-verify the resource is still active and unowned, assign ownership,
	 * mark the claim {@code APPROVED}, and record the audit event — all or
	 * nothing. Locking the resource row (not just the claim row) is what
	 * makes two different claims racing for the <em>same</em> resource
	 * concurrency-safe: two different claim ids are two different rows, so
	 * locking only the claim would never serialize them against each other;
	 * the resource row is the one thing both transactions actually contend
	 * over, reusing {@code ResourceRepository.findByIdForUpdate} — the exact
	 * lock the Milestone 9A correction-report flow already established for
	 * an identical "two concurrent decisions, one shared resource" shape.
	 */
	@Transactional
	public AdminOwnershipClaimDetailResponse approve(CurrentUserPrincipal admin, UUID claimId, OrganizationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		ResourceOwnershipClaim claim = claimRepository.findByIdForReview(claimId)
				.orElseThrow(() -> ResourceOwnershipClaimNotFoundException.byId(claimId));

		Organization organization = organizationRepository.findById(claim.getOrganizationId())
				.orElseThrow(() -> OrganizationNotFoundException.byId(claim.getOrganizationId()));

		requireNotSelfReview(admin.userId(), organization.getOwnerUserId());
		requirePending(claim);

		if (organization.getVerificationStatus() != OrganizationVerificationStatus.VERIFIED) {
			throw new OrganizationNotVerifiedException();
		}

		CommunityResource resource = resourceRepository.findByIdForUpdate(claim.getResourceId())
				.orElseThrow(() -> ResourceNotFoundException.byId(claim.getResourceId()));
		if (!resource.isActive()) {
			throw new InactiveResourceException();
		}
		if (resource.getOrganizationId() != null) {
			throw new ResourceAlreadyOwnedException();
		}

		resource.assignOrganization(organization.getId());
		claim.approve(admin.userId(), reason);

		auditRecorder.record(OrganizationAuditEventType.OWNERSHIP_CLAIM_APPROVED, organization.getId(),
				resource.getId(), claim.getId(), admin, reason, null, null);

		CommunityResource resourceWithCategory = resourceRepository.findByIdWithCategory(resource.getId()).orElseThrow();
		return toDetailResponse(claim, organization, resourceWithCategory);
	}

	@Transactional
	public AdminOwnershipClaimDetailResponse reject(CurrentUserPrincipal admin, UUID claimId, OrganizationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		ResourceOwnershipClaim claim = claimRepository.findByIdForReview(claimId)
				.orElseThrow(() -> ResourceOwnershipClaimNotFoundException.byId(claimId));

		Organization organization = organizationRepository.findById(claim.getOrganizationId())
				.orElseThrow(() -> OrganizationNotFoundException.byId(claim.getOrganizationId()));

		requireNotSelfReview(admin.userId(), organization.getOwnerUserId());
		requirePending(claim);

		claim.reject(admin.userId(), reason);

		auditRecorder.record(OrganizationAuditEventType.OWNERSHIP_CLAIM_REJECTED, organization.getId(),
				claim.getResourceId(), claim.getId(), admin, reason, null, null);

		CommunityResource resource = resourceRepository.findByIdWithCategory(claim.getResourceId())
				.orElseThrow(() -> ResourceNotFoundException.byId(claim.getResourceId()));
		return toDetailResponse(claim, organization, resource);
	}

	private static AdminOwnershipClaimDetailResponse toDetailResponse(
			ResourceOwnershipClaim claim, Organization organization, CommunityResource resource) {
		return new AdminOwnershipClaimDetailResponse(
				claim.getId(), organization.getId(), organization.getName(), organization.getSlug(),
				organization.getVerificationStatus(), ClaimedResourceSummaryResponse.from(resource), resource.getOrganizationId(),
				claim.getStatus(), claim.getRequestedAt(), claim.getReviewedByUserId(), claim.getReviewedAt(), claim.getReviewReason());
	}

	private static void requireNotSelfReview(UUID adminId, UUID ownerId) {
		if (adminId.equals(ownerId)) {
			throw new SelfReviewNotAllowedException();
		}
	}

	private static void requirePending(ResourceOwnershipClaim claim) {
		if (claim.getStatus() != ResourceOwnershipClaimStatus.PENDING_REVIEW) {
			throw new ContributionAlreadyReviewedException();
		}
	}

	private Map<UUID, String> loadOrganizationNames(List<ResourceOwnershipClaim> claims) {
		if (claims.isEmpty()) {
			return Map.of();
		}
		List<UUID> organizationIds = claims.stream().map(ResourceOwnershipClaim::getOrganizationId).distinct().toList();
		Map<UUID, String> byId = new HashMap<>();
		organizationRepository.findAllById(organizationIds).forEach(org -> byId.put(org.getId(), org.getName()));
		return byId;
	}

	private Map<UUID, ClaimedResourceSummaryResponse> loadResourceSummaries(List<ResourceOwnershipClaim> claims) {
		if (claims.isEmpty()) {
			return Map.of();
		}
		List<UUID> resourceIds = claims.stream().map(ResourceOwnershipClaim::getResourceId).distinct().toList();
		Map<UUID, ClaimedResourceSummaryResponse> byId = new HashMap<>();
		resourceRepository.findByIdInWithCategory(resourceIds)
				.forEach(resource -> byId.put(resource.getId(), ClaimedResourceSummaryResponse.from(resource)));
		return byId;
	}

	private static Pageable pageable(int page, int size) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size, Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id")));
	}

}
