package com.hfxconnect.moderation;

import com.hfxconnect.common.error.InvalidPaginationException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only moderation audit history — moderator/admin-only (see
 * {@code SecurityConfig}). One contribution's full history (oldest first,
 * for the moderation detail page) and the global list (newest first,
 * independently optional {@code contributionType}/{@code decision}
 * filters — see {@code ModerationAuditEventRepository}'s Javadoc for why
 * {@code actorId} and date-range filtering are not implemented here).
 */
@Service
public class ModerationAuditQueryService {

	static final int MAX_PAGE_SIZE = 100;

	private final ModerationAuditEventRepository auditEventRepository;

	public ModerationAuditQueryService(ModerationAuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public ModerationAuditEventPageResponse forContribution(ContributionType contributionType, UUID contributionId,
			int page, int size) {
		Pageable pageable = pageable(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
		Page<ModerationAuditEvent> results = auditEventRepository.findByContribution(contributionType, contributionId, pageable);
		return toPageResponse(results);
	}

	@Transactional(readOnly = true)
	public ModerationAuditEventPageResponse global(ContributionType contributionType, ModerationDecision decision,
			int page, int size) {
		Pageable pageable = pageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<ModerationAuditEvent> results = auditEventRepository.findForGlobalList(contributionType, decision, pageable);
		return toPageResponse(results);
	}

	private static ModerationAuditEventPageResponse toPageResponse(Page<ModerationAuditEvent> results) {
		return new ModerationAuditEventPageResponse(
				results.getContent().stream().map(ModerationAuditEventResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	private static Pageable pageable(int page, int size, Sort sort) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size, sort);
	}

}
