package com.hfxconnect.organization;

import com.hfxconnect.common.error.InvalidPaginationException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only organization/ownership audit history — {@code ADMIN}-only (see
 * {@code SecurityConfig}). Mirrors {@code
 * com.hfxconnect.moderation.ModerationAuditQueryService.forContribution}'s
 * exact shape: one organization's full history, oldest first.
 */
@Service
public class OrganizationAuditQueryService {

	static final int MAX_PAGE_SIZE = 100;

	private final OrganizationAuditEventRepository auditEventRepository;

	public OrganizationAuditQueryService(OrganizationAuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public OrganizationAuditEventPageResponse forOrganization(UUID organizationId, int page, int size) {
		Pageable pageable = pageable(page, size);
		Page<OrganizationAuditEvent> results = auditEventRepository.findByOrganization(organizationId, pageable);
		return new OrganizationAuditEventPageResponse(
				results.getContent().stream().map(OrganizationAuditEventResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	private static Pageable pageable(int page, int size) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size);
	}

}
