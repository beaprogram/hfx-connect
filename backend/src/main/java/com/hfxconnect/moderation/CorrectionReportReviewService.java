package com.hfxconnect.moderation;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.correctionreport.CorrectionReport;
import com.hfxconnect.correctionreport.CorrectionReportNotFoundException;
import com.hfxconnect.correctionreport.CorrectionReportRepository;
import com.hfxconnect.correctionreport.CorrectionReportStatus;
import com.hfxconnect.correctionreport.IssueType;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.resource.ResourceValidation;
import com.hfxconnect.security.CurrentUserPrincipal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderator-facing correction-report review (Milestone 9A): the queue,
 * detail view, and the approve/reject decisions. See ADR-016's "Correction
 * Application Policy" and "Concurrency Control" sections for the full design
 * — in particular why {@link IssueType#OPERATING_HOURS}/{@link
 * IssueType#DUPLICATE_RESOURCE} have no supported automatic field
 * application, and why the target resource row is locked separately from
 * the report row.
 */
@Service
public class CorrectionReportReviewService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("submittedAt", "updatedAt");

	/** Issue types with no automated field mapping — see ADR-016's "Correction Application Policy". */
	private static final Set<IssueType> UNSUPPORTED_APPLICATION_ISSUE_TYPES =
			Set.of(IssueType.OPERATING_HOURS, IssueType.DUPLICATE_RESOURCE);

	private final CorrectionReportRepository reportRepository;
	private final ResourceRepository resourceRepository;
	private final ModerationAuditRecorder auditRecorder;

	public CorrectionReportReviewService(CorrectionReportRepository reportRepository,
			ResourceRepository resourceRepository, ModerationAuditRecorder auditRecorder) {
		this.reportRepository = reportRepository;
		this.resourceRepository = resourceRepository;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * The moderation queue — defaults to {@code PENDING_REVIEW}, oldest
	 * submitted first. See {@code ResourceSubmissionReviewService.queue}'s
	 * identical reasoning for why a missing {@code status} defaults to
	 * pending rather than matching every status.
	 */
	@Transactional(readOnly = true)
	public CorrectionReportQueuePageResponse queue(CorrectionReportStatus status, IssueType issueType, int page,
			int size, String sort) {
		CorrectionReportStatus effectiveStatus = status != null ? status : CorrectionReportStatus.PENDING_REVIEW;
		Pageable pageable = pageable(page, size, sort);
		Page<CorrectionReport> results = reportRepository.findForModerationQueue(effectiveStatus, issueType, pageable);
		return new CorrectionReportQueuePageResponse(
				results.getContent().stream().map(CorrectionReportQueueItemResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	@Transactional(readOnly = true)
	public CorrectionReportModerationDetailResponse detail(UUID reportId) {
		CorrectionReport report = reportRepository.findByIdForModeration(reportId)
				.orElseThrow(() -> CorrectionReportNotFoundException.byId(reportId));
		return CorrectionReportModerationDetailResponse.from(report);
	}

	/**
	 * Approves a correction report. {@code request.applyProposedChanges()}
	 * and {@code request.deactivateResource()} are independent — either,
	 * both, or neither may be {@code true} (approving with neither is a
	 * legitimate "reviewed, no automatic public-data change" outcome). When
	 * either flag requires touching the target resource, its row is locked
	 * for the rest of this transaction (see {@code
	 * ResourceRepository.findByIdForUpdate}'s Javadoc for why: two different
	 * pending reports can target the same resource).
	 */
	@Transactional
	public CorrectionReportModerationDetailResponse approve(CurrentUserPrincipal moderator, UUID reportId,
			CorrectionApprovalRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		CorrectionReport report = reportRepository.findByIdForReview(reportId)
				.orElseThrow(() -> CorrectionReportNotFoundException.byId(reportId));

		requireNotSelfReview(moderator.userId(), report.getReportedByUserId());
		requirePending(report.getStatus());

		if (request.deactivateResource() && report.getIssueType() != IssueType.RESOURCE_CLOSED) {
			throw new InvalidDeactivationRequestException();
		}
		if (request.applyProposedChanges() && UNSUPPORTED_APPLICATION_ISSUE_TYPES.contains(report.getIssueType())) {
			throw new UnsupportedCorrectionApplicationException(report.getIssueType());
		}

		boolean touchesResource = request.applyProposedChanges() || request.deactivateResource();
		UUID targetResourceId = report.getResource() == null ? null : report.getResource().getId();
		if (touchesResource && targetResourceId == null) {
			throw CorrectionApplicationConflictException.targetUnavailable();
		}

		boolean appliedChanges = false;
		boolean deactivated = false;
		CommunityResource resource = null;
		Map<String, Object> beforeFields = null;
		Map<String, Object> afterFields = null;

		if (touchesResource) {
			// Locks the resource row for the rest of this transaction — see
			// this class's Javadoc and ResourceRepository.findByIdForUpdate.
			resource = resourceRepository.findByIdForUpdate(targetResourceId)
					.orElseThrow(CorrectionApplicationConflictException::targetUnavailable);

			if (request.applyProposedChanges()) {
				beforeFields = changedFieldsBefore(resource, report);
				applyProposedChanges(resource, report);
				resource.markVerified(Instant.now());
				afterFields = changedFieldsAfter(resource, report);
				appliedChanges = true;
			}
			if (request.deactivateResource()) {
				resource.deactivate();
				deactivated = true;
			}

			try {
				resourceRepository.saveAndFlush(resource);
			} catch (DataIntegrityViolationException ex) {
				throw CorrectionApplicationConflictException.applicationConflict();
			}
		}

		report.approve(moderator.userId(), reason, appliedChanges ? Instant.now() : null);

		if (appliedChanges) {
			auditRecorder.record(ContributionType.CORRECTION_REPORT, report.getId(), ModerationAction.RESOURCE_UPDATED,
					ModerationDecision.APPROVED, moderator, reason, resource.getId(), beforeFields, afterFields);
		}
		if (deactivated) {
			auditRecorder.record(ContributionType.CORRECTION_REPORT, report.getId(), ModerationAction.RESOURCE_DEACTIVATED,
					ModerationDecision.APPROVED, moderator, reason, resource.getId(),
					Map.of("active", true), Map.of("active", false));
		}
		if (!appliedChanges && !deactivated) {
			auditRecorder.record(ContributionType.CORRECTION_REPORT, report.getId(), ModerationAction.REVIEW_DECISION,
					ModerationDecision.APPROVED, moderator, reason, null, null, null);
		}

		return CorrectionReportModerationDetailResponse.from(report);
	}

	/** Rejects a correction report: never modifies the target resource. */
	@Transactional
	public CorrectionReportModerationDetailResponse reject(CurrentUserPrincipal moderator, UUID reportId,
			ModerationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		CorrectionReport report = reportRepository.findByIdForReview(reportId)
				.orElseThrow(() -> CorrectionReportNotFoundException.byId(reportId));

		requireNotSelfReview(moderator.userId(), report.getReportedByUserId());
		requirePending(report.getStatus());

		report.reject(moderator.userId(), reason);

		auditRecorder.record(ContributionType.CORRECTION_REPORT, report.getId(), ModerationAction.REVIEW_DECISION,
				ModerationDecision.REJECTED, moderator, reason, null, null, null);

		return CorrectionReportModerationDetailResponse.from(report);
	}

	private static void requireNotSelfReview(UUID moderatorId, UUID contributorId) {
		if (moderatorId.equals(contributorId)) {
			throw new SelfReviewNotAllowedException();
		}
	}

	private static void requirePending(CorrectionReportStatus status) {
		if (status != CorrectionReportStatus.PENDING_REVIEW) {
			throw new ContributionAlreadyReviewedException();
		}
	}

	/**
	 * Merges the report's supported proposed fields onto the resource's
	 * current values (a proposed field present takes the new value; absent
	 * keeps the current one), revalidates the merged result with the exact
	 * same {@link ResourceValidation} rules resource creation/update already
	 * use, then applies it via {@link CommunityResource#updateDetails}. The
	 * category is never changed here — the report schema has no proposed
	 * category (see ADR-016).
	 */
	private static void applyProposedChanges(CommunityResource resource, CorrectionReport report) {
		String name = orElse(report.getProposedName(), resource.getName());
		String description = orElse(report.getProposedDescription(), resource.getDescription());
		String addressLine1 = orElse(report.getProposedAddressLine1(), resource.getAddressLine1());
		String addressLine2 = orElse(report.getProposedAddressLine2(), resource.getAddressLine2());
		String city = orElse(report.getProposedCity(), resource.getCity());
		String province = orElse(report.getProposedProvince(), resource.getProvince());
		String postalCode = orElse(report.getProposedPostalCode(), resource.getPostalCode());
		String phone = orElse(report.getProposedPhone(), resource.getPhone());
		String email = orElse(report.getProposedEmail(), resource.getEmail());
		String websiteUrl = orElse(report.getProposedWebsiteUrl(), resource.getWebsiteUrl());
		CostType costType = report.getProposedCostType() != null ? report.getProposedCostType() : resource.getCostType();
		String costDetails = orElse(report.getProposedCostDetails(), resource.getCostDetails());
		String eligibility = orElse(report.getProposedEligibility(), resource.getEligibility());

		// Field-by-field, via ResourceValidation's public static helpers —
		// not its package-private validate()/Normalized (deliberately not
		// widened in Milestone 8B; see ResourceValidation's own Javadoc: only
		// the individual field checks are meant to be reused across
		// packages). Mirrors ResourceSubmissionValidation/
		// CorrectionReportValidation's identical approach.
		Map<String, String> errors = new LinkedHashMap<>();
		String normalizedName = ResourceValidation.requireBounded(errors, "proposedName", name,
				ResourceValidation.NAME_MAX_LENGTH, "Name");
		String normalizedDescription = ResourceValidation.requireBounded(errors, "proposedDescription", description,
				ResourceValidation.DESCRIPTION_MAX_LENGTH, "Description");
		String normalizedAddressLine1 = ResourceValidation.requireBounded(errors, "proposedAddressLine1", addressLine1,
				ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Address line 1");
		String normalizedAddressLine2 = ResourceValidation.optionalBounded(errors, "proposedAddressLine2", addressLine2,
				ResourceValidation.ADDRESS_LINE_MAX_LENGTH, "Address line 2");
		String normalizedCity = ResourceValidation.requireBounded(errors, "proposedCity", city,
				ResourceValidation.CITY_MAX_LENGTH, "City");
		String normalizedProvince = ResourceValidation.validateProvince(errors, province);
		String normalizedPostalCode = ResourceValidation.validatePostalCode(errors, postalCode);
		String normalizedPhone = ResourceValidation.validatePhone(errors, phone);
		String normalizedEmail = ResourceValidation.validateEmail(errors, email);
		String normalizedWebsiteUrl = ResourceValidation.validateWebsiteUrl(errors, websiteUrl);
		String normalizedCostDetails = ResourceValidation.optionalBounded(errors, "proposedCostDetails", costDetails,
				ResourceValidation.COST_DETAILS_MAX_LENGTH, "Cost details");
		String normalizedEligibility = ResourceValidation.optionalBounded(errors, "proposedEligibility", eligibility,
				ResourceValidation.ELIGIBILITY_MAX_LENGTH, "Eligibility");

		if (!errors.isEmpty()) {
			throw new ValidationException("Applying this report's proposed changes would produce invalid resource data.", errors);
		}

		resource.updateDetails(resource.getCategory(), normalizedName, normalizedDescription, normalizedAddressLine1,
				normalizedAddressLine2, normalizedCity, normalizedProvince, normalizedPostalCode, normalizedPhone,
				normalizedEmail, normalizedWebsiteUrl, costType, normalizedCostDetails, normalizedEligibility);
	}

	private static String orElse(String proposed, String current) {
		return proposed != null ? proposed : current;
	}

	/** Only the fields the report actually proposed a change for — their values before the update. */
	private static Map<String, Object> changedFieldsBefore(CommunityResource resource, CorrectionReport report) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		putIfProposed(snapshot, "name", report.getProposedName(), resource.getName());
		putIfProposed(snapshot, "description", report.getProposedDescription(), resource.getDescription());
		putIfProposed(snapshot, "addressLine1", report.getProposedAddressLine1(), resource.getAddressLine1());
		putIfProposed(snapshot, "addressLine2", report.getProposedAddressLine2(), resource.getAddressLine2());
		putIfProposed(snapshot, "city", report.getProposedCity(), resource.getCity());
		putIfProposed(snapshot, "province", report.getProposedProvince(), resource.getProvince());
		putIfProposed(snapshot, "postalCode", report.getProposedPostalCode(), resource.getPostalCode());
		putIfProposed(snapshot, "phone", report.getProposedPhone(), resource.getPhone());
		putIfProposed(snapshot, "email", report.getProposedEmail(), resource.getEmail());
		putIfProposed(snapshot, "websiteUrl", report.getProposedWebsiteUrl(), resource.getWebsiteUrl());
		if (report.getProposedCostType() != null) {
			snapshot.put("costType", resource.getCostType().name());
		}
		putIfProposed(snapshot, "costDetails", report.getProposedCostDetails(), resource.getCostDetails());
		putIfProposed(snapshot, "eligibility", report.getProposedEligibility(), resource.getEligibility());
		return snapshot;
	}

	/** The same field set as {@link #changedFieldsBefore}, but the resource's values *after* the update already applied. */
	private static Map<String, Object> changedFieldsAfter(CommunityResource resource, CorrectionReport report) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		putIfProposed(snapshot, "name", report.getProposedName(), resource.getName());
		putIfProposed(snapshot, "description", report.getProposedDescription(), resource.getDescription());
		putIfProposed(snapshot, "addressLine1", report.getProposedAddressLine1(), resource.getAddressLine1());
		putIfProposed(snapshot, "addressLine2", report.getProposedAddressLine2(), resource.getAddressLine2());
		putIfProposed(snapshot, "city", report.getProposedCity(), resource.getCity());
		putIfProposed(snapshot, "province", report.getProposedProvince(), resource.getProvince());
		putIfProposed(snapshot, "postalCode", report.getProposedPostalCode(), resource.getPostalCode());
		putIfProposed(snapshot, "phone", report.getProposedPhone(), resource.getPhone());
		putIfProposed(snapshot, "email", report.getProposedEmail(), resource.getEmail());
		putIfProposed(snapshot, "websiteUrl", report.getProposedWebsiteUrl(), resource.getWebsiteUrl());
		if (report.getProposedCostType() != null) {
			snapshot.put("costType", resource.getCostType().name());
		}
		putIfProposed(snapshot, "costDetails", report.getProposedCostDetails(), resource.getCostDetails());
		putIfProposed(snapshot, "eligibility", report.getProposedEligibility(), resource.getEligibility());
		return snapshot;
	}

	private static void putIfProposed(Map<String, Object> snapshot, String key, String proposedValue, String currentValue) {
		if (proposedValue != null) {
			snapshot.put(key, currentValue);
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

	private static Sort resolveSort(String sort) {
		if (sort == null || sort.isBlank() || "submittedAt".equals(sort)) {
			return Sort.by(Sort.Order.asc("submittedAt"), Sort.Order.asc("id"));
		}
		if ("updatedAt".equals(sort)) {
			return Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
		}
		throw new InvalidSortException("sort must be one of " + ALLOWED_SORT_VALUES + " (got '" + sort + "').");
	}

}
