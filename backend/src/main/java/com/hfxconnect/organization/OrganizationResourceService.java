package com.hfxconnect.organization;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.ResourceRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current {@code ORGANIZATION} account's list of owned resources (Milestone 10A) — read-only; no editing action exists yet. */
@Service
public class OrganizationResourceService {

	static final int MAX_PAGE_SIZE = 100;

	private final OrganizationRepository organizationRepository;
	private final ResourceRepository resourceRepository;

	public OrganizationResourceService(OrganizationRepository organizationRepository, ResourceRepository resourceRepository) {
		this.organizationRepository = organizationRepository;
		this.resourceRepository = resourceRepository;
	}

	@Transactional(readOnly = true)
	public OwnedResourcePageResponse list(UUID ownerUserId, int page, int size) {
		Organization organization = organizationRepository.findByOwnerUserId(ownerUserId)
				.orElseThrow(OrganizationNotFoundException::forCurrentUser);
		Pageable pageable = pageable(page, size);
		Page<CommunityResource> results = resourceRepository.findByOrganizationIdWithCategory(organization.getId(), pageable);
		return new OwnedResourcePageResponse(
				results.getContent().stream().map(OwnedResourceSummaryResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	private static Pageable pageable(int page, int size) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
	}

}
