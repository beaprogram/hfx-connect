package com.hfxconnect.organization;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.ResourceNotFoundException;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.security.CurrentUserPrincipal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current {@code ORGANIZATION} account's own resource-ownership claims:
 * create, list, withdraw. {@code resources.organization_id} remains the sole
 * ownership authority throughout — creating a claim here never touches it;
 * only {@link AdminResourceOwnershipClaimService#approve} does (ADR-017).
 */
@Service
public class ResourceOwnershipClaimService {

	static final int MAX_PAGE_SIZE = 100;

	private final ResourceOwnershipClaimRepository claimRepository;
	private final OrganizationRepository organizationRepository;
	private final ResourceRepository resourceRepository;
	private final OrganizationAuditRecorder auditRecorder;

	public ResourceOwnershipClaimService(ResourceOwnershipClaimRepository claimRepository,
			OrganizationRepository organizationRepository, ResourceRepository resourceRepository,
			OrganizationAuditRecorder auditRecorder) {
		this.claimRepository = claimRepository;
		this.organizationRepository = organizationRepository;
		this.resourceRepository = resourceRepository;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * Requires: a profile exists; the organization is currently {@code
	 * VERIFIED}; the resource exists and is active; the resource is
	 * currently unowned; no existing pending claim by this organization for
	 * this resource. Never accepts an organization id from the caller — the
	 * organization is always the current account's own profile.
	 */
	@Transactional
	public OwnershipClaimResponse create(CurrentUserPrincipal principal, UUID resourceId) {
		Organization organization = organizationRepository.findByOwnerUserId(principal.userId())
				.orElseThrow(OrganizationNotFoundException::forCurrentUser);
		requireVerified(organization);

		CommunityResource resource = resourceRepository.findById(resourceId)
				.orElseThrow(() -> ResourceNotFoundException.byId(resourceId));
		if (!resource.isActive()) {
			throw new InactiveResourceException();
		}
		if (resource.getOrganizationId() != null) {
			throw new ResourceAlreadyOwnedException();
		}
		if (claimRepository.existsByOrganizationIdAndResourceIdAndStatus(
				organization.getId(), resourceId, ResourceOwnershipClaimStatus.PENDING_REVIEW)) {
			throw ResourceOwnershipClaimConflictException.duplicatePending();
		}

		ResourceOwnershipClaim claim = new ResourceOwnershipClaim(organization.getId(), resourceId);
		ResourceOwnershipClaim saved;
		try {
			// saveAndFlush — see OrganizationService.create's identical
			// reasoning: id is Hibernate-generated, so a plain save() would not
			// reliably surface the partial-unique-index race here.
			saved = claimRepository.saveAndFlush(claim);
		} catch (DataIntegrityViolationException ex) {
			throw ResourceOwnershipClaimConflictException.duplicatePending();
		}

		auditRecorder.record(OrganizationAuditEventType.OWNERSHIP_CLAIM_SUBMITTED, organization.getId(), resourceId,
				saved.getId(), principal, null, null, null);

		return OwnershipClaimResponse.from(saved, ClaimedResourceSummaryResponse.from(resource));
	}

	@Transactional(readOnly = true)
	public OwnershipClaimPageResponse list(UUID ownerUserId, ResourceOwnershipClaimStatus status, int page, int size) {
		Organization organization = organizationRepository.findByOwnerUserId(ownerUserId)
				.orElseThrow(OrganizationNotFoundException::forCurrentUser);
		Pageable pageable = pageable(page, size);
		Page<ResourceOwnershipClaim> results = claimRepository.findByOrganization(organization.getId(), status, pageable);
		Map<UUID, ClaimedResourceSummaryResponse> resourcesById = loadResourceSummaries(results.getContent());
		List<OwnershipClaimResponse> content = results.getContent().stream()
				.map(claim -> OwnershipClaimResponse.from(claim, resourcesById.get(claim.getResourceId())))
				.toList();
		return new OwnershipClaimPageResponse(content, results.getNumber(), results.getSize(), results.getTotalElements(),
				results.getTotalPages());
	}

	/** Only the claim's own organization may withdraw it, and only while still {@code PENDING_REVIEW}. */
	@Transactional
	public OwnershipClaimResponse withdraw(CurrentUserPrincipal principal, UUID claimId) {
		Organization organization = organizationRepository.findByOwnerUserId(principal.userId())
				.orElseThrow(OrganizationNotFoundException::forCurrentUser);

		ResourceOwnershipClaim claim = claimRepository.findByIdAndOrganizationId(claimId, organization.getId())
				.orElseThrow(() -> ResourceOwnershipClaimNotFoundException.byId(claimId));

		claim.withdraw();

		auditRecorder.record(OrganizationAuditEventType.OWNERSHIP_CLAIM_WITHDRAWN, organization.getId(),
				claim.getResourceId(), claim.getId(), principal, null, null, null);

		CommunityResource resource = resourceRepository.findById(claim.getResourceId()).orElse(null);
		return OwnershipClaimResponse.from(claim, resource == null ? null : ClaimedResourceSummaryResponse.from(resource));
	}

	private static void requireVerified(Organization organization) {
		if (organization.getVerificationStatus() != OrganizationVerificationStatus.VERIFIED) {
			throw new OrganizationNotVerifiedException();
		}
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
		return PageRequest.of(page, size, Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
	}

}
