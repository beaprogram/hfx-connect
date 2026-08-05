package com.hfxconnect.correctionreport;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.ResourceNotFoundException;
import com.hfxconnect.resource.ResourceRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All correction-report business logic (Milestone 8B): creating a pending
 * report against an active resource, owner-scoped listing/detail, and
 * withdrawal. Every method here is scoped by a {@code userId} the caller
 * ({@code CorrectionReportController}) derives exclusively from the
 * authenticated {@code CurrentUserPrincipal}, matching
 * {@code ResourceSubmissionService}'s and {@code SavedResourceService}'s
 * established pattern.
 */
@Service
public class CorrectionReportService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("submittedAt", "updatedAt", "status");

	private final CorrectionReportRepository reportRepository;
	private final ResourceRepository resourceRepository;

	public CorrectionReportService(CorrectionReportRepository reportRepository, ResourceRepository resourceRepository) {
		this.reportRepository = reportRepository;
		this.resourceRepository = resourceRepository;
	}

	/**
	 * Creates a new pending report for {@code userId} against
	 * {@code resourceId}. The target must currently be active — a missing
	 * or inactive resource is indistinguishable to the caller (both throw
	 * {@link ResourceNotFoundException}), the same visibility rule
	 * {@code SavedResourceService.save} already applies.
	 */
	@Transactional
	public CorrectionReportResponse create(UUID userId, UUID resourceId, CorrectionReportCreateRequest request) {
		CommunityResource resource = resourceRepository.findById(resourceId)
				.filter(CommunityResource::isActive)
				.orElseThrow(() -> ResourceNotFoundException.byId(resourceId));

		CorrectionReportValidation.Normalized normalized = CorrectionReportValidation.validate(
				request.explanation(), request.proposedName(), request.proposedDescription(),
				request.proposedAddressLine1(), request.proposedAddressLine2(), request.proposedCity(),
				request.proposedProvince(), request.proposedPostalCode(), request.proposedPhone(),
				request.proposedEmail(), request.proposedWebsiteUrl(), request.proposedCostDetails(),
				request.proposedEligibility());

		CorrectionReport report = new CorrectionReport(
				userId, resource, request.issueType(), normalized.explanation(), normalized.proposedName(),
				normalized.proposedDescription(), normalized.proposedAddressLine1(), normalized.proposedAddressLine2(),
				normalized.proposedCity(), normalized.proposedProvince(), normalized.proposedPostalCode(),
				normalized.proposedPhone(), normalized.proposedEmail(), normalized.proposedWebsiteUrl(),
				request.proposedCostType(), normalized.proposedCostDetails(), normalized.proposedEligibility());

		try {
			// saveAndFlush: CorrectionReport.id is a Hibernate-generated UUID
			// (not IDENTITY) — see ResourceSubmissionService.create's identical
			// reasoning.
			CorrectionReport saved = reportRepository.saveAndFlush(report);
			return CorrectionReportResponse.from(saved);
		} catch (DataIntegrityViolationException ex) {
			// correction_reports_pending_duplicate_key (V9): the caller
			// already has a PENDING_REVIEW report for this resource+issueType.
			throw CorrectionReportConflictException.duplicatePending();
		}
	}

	/** Lists {@code userId}'s own correction reports, newest-submitted first by default. */
	@Transactional(readOnly = true)
	public CorrectionReportPageResponse list(UUID userId, int page, int size, String sort) {
		Pageable pageable = pageable(page, size);
		Page<CorrectionReport> results = switch (resolveSort(sort)) {
			case "updatedAt" -> reportRepository.findByOwnerOrderByUpdatedAtDesc(userId, pageable);
			case "status" -> reportRepository.findByOwnerOrderByStatus(userId, pageable);
			default -> reportRepository.findByOwnerOrderBySubmittedAtDesc(userId, pageable);
		};

		return new CorrectionReportPageResponse(
				results.getContent().stream().map(CorrectionReportResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	/**
	 * Returns one report {@code userId} owns. Throws the same {@code 404}
	 * whether the id doesn't exist at all or belongs to a different account.
	 */
	@Transactional(readOnly = true)
	public CorrectionReportResponse get(UUID userId, UUID reportId) {
		return reportRepository.findByIdAndOwner(reportId, userId)
				.map(CorrectionReportResponse::from)
				.orElseThrow(() -> CorrectionReportNotFoundException.byId(reportId));
	}

	/**
	 * Withdraws {@code userId}'s own report — only legal while it is still
	 * {@code PENDING_REVIEW} (see {@link CorrectionReport#withdraw}).
	 * {@code saveAndFlush}, not a bare mutation: see
	 * {@code ResourceSubmissionService.withdraw}'s identical reasoning —
	 * flushing here makes the withdrawal durable before this method
	 * returns, immune to Hibernate's insert-before-update flush ordering if
	 * a caller chains this with an immediate resubmission.
	 */
	@Transactional
	public CorrectionReportResponse withdraw(UUID userId, UUID reportId) {
		CorrectionReport report = reportRepository.findByIdAndOwner(reportId, userId)
				.orElseThrow(() -> CorrectionReportNotFoundException.byId(reportId));
		report.withdraw();
		reportRepository.saveAndFlush(report);
		return CorrectionReportResponse.from(report);
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

	private static String resolveSort(String sort) {
		if (sort == null || sort.isBlank() || "submittedAt".equals(sort)) {
			return "submittedAt";
		}
		if (ALLOWED_SORT_VALUES.contains(sort)) {
			return sort;
		}
		throw new InvalidSortException("sort must be one of " + ALLOWED_SORT_VALUES + " (got '" + sort + "').");
	}

}
