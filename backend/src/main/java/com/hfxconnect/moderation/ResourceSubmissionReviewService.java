package com.hfxconnect.moderation;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.resource.CreateResourceCommand;
import com.hfxconnect.resource.ResourceConflictException;
import com.hfxconnect.resource.ResourceDetails;
import com.hfxconnect.resource.ResourceService;
import com.hfxconnect.resourcesubmission.ResourceSubmission;
import com.hfxconnect.resourcesubmission.ResourceSubmissionNotFoundException;
import com.hfxconnect.resourcesubmission.ResourceSubmissionRepository;
import com.hfxconnect.resourcesubmission.SubmissionStatus;
import com.hfxconnect.security.CurrentUserPrincipal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderator-facing resource-submission review (Milestone 9A): the queue,
 * detail view, and the approve/reject decisions themselves. See ADR-016 for
 * the full design — in particular, why a row-level lock (not {@code
 * @Version}) is this workflow's concurrency-control mechanism, and why
 * publication reuses {@code ResourceService.createVerified} instead of a
 * second, parallel resource-creation code path.
 */
@Service
public class ResourceSubmissionReviewService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("submittedAt", "updatedAt");

	private final ResourceSubmissionRepository submissionRepository;
	private final ResourceService resourceService;
	private final ModerationAuditRecorder auditRecorder;

	public ResourceSubmissionReviewService(ResourceSubmissionRepository submissionRepository,
			ResourceService resourceService, ModerationAuditRecorder auditRecorder) {
		this.submissionRepository = submissionRepository;
		this.resourceService = resourceService;
		this.auditRecorder = auditRecorder;
	}

	/**
	 * The moderation queue — defaults to {@code PENDING_REVIEW}, oldest
	 * submitted first. A missing {@code status} means "the default view of
	 * the queue" (pending items), not "every status" — a moderator wanting a
	 * specific other status (e.g. reviewing recently {@code REJECTED} items)
	 * passes it explicitly.
	 */
	@Transactional(readOnly = true)
	public ResourceSubmissionQueuePageResponse queue(SubmissionStatus status, Long categoryId, int page, int size, String sort) {
		SubmissionStatus effectiveStatus = status != null ? status : SubmissionStatus.PENDING_REVIEW;
		Pageable pageable = pageable(page, size, sort);
		Page<ResourceSubmission> results = submissionRepository.findForModerationQueue(effectiveStatus, categoryId, pageable);
		return new ResourceSubmissionQueuePageResponse(
				results.getContent().stream().map(ResourceSubmissionQueueItemResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	/** Any submission by id, regardless of owner — not owner-scoped, unlike {@code ResourceSubmissionService.get}. */
	@Transactional(readOnly = true)
	public ResourceSubmissionModerationDetailResponse detail(UUID submissionId) {
		ResourceSubmission submission = submissionRepository.findByIdForModeration(submissionId)
				.orElseThrow(() -> ResourceSubmissionNotFoundException.byId(submissionId));
		return ResourceSubmissionModerationDetailResponse.from(submission);
	}

	/**
	 * Approves a submission: revalidates and publishes it as a real public
	 * resource (via {@link ResourceService#createVerified}), marks the
	 * submission {@code APPROVED}, and records a {@code RESOURCE_CREATED}
	 * audit event — all in one transaction. A publication conflict (duplicate
	 * slug) leaves the submission {@code PENDING_REVIEW} and never creates a
	 * resource; the entire transaction rolls back on any exception here.
	 */
	@Transactional
	public ResourceSubmissionModerationDetailResponse approve(CurrentUserPrincipal moderator, UUID submissionId,
			ModerationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		// Row-level lock for the whole transaction — see ADR-016's
		// "Concurrency Control" section. A second moderator's concurrent call
		// blocks here until this transaction ends, then observes the
		// now-current (no longer PENDING_REVIEW) status below.
		ResourceSubmission submission = submissionRepository.findByIdForReview(submissionId)
				.orElseThrow(() -> ResourceSubmissionNotFoundException.byId(submissionId));

		requireNotSelfReview(moderator.userId(), submission.getSubmittedByUserId());
		requirePending(submission.getStatus());

		CreateResourceCommand command = toCreateResourceCommand(submission);
		ResourceDetails created;
		try {
			created = resourceService.createVerified(command, Instant.now());
		} catch (ResourceConflictException ex) {
			throw new ResourcePublicationConflictException(command.name());
		}

		submission.approve(moderator.userId(), reason, created.id(), created.name(), created.slug());

		auditRecorder.record(ContributionType.RESOURCE_SUBMISSION, submission.getId(), ModerationAction.RESOURCE_CREATED,
				ModerationDecision.APPROVED, moderator, reason, created.id(),
				ModerationSnapshots.ofSubmission(submission), ModerationSnapshots.ofResource(created));

		return ResourceSubmissionModerationDetailResponse.from(submission);
	}

	/** Rejects a submission: never creates a resource, never alters any other data besides this submission's own review fields. */
	@Transactional
	public ResourceSubmissionModerationDetailResponse reject(CurrentUserPrincipal moderator, UUID submissionId,
			ModerationDecisionRequest request) {
		String reason = ModerationValidation.validateReason(request.reason());

		ResourceSubmission submission = submissionRepository.findByIdForReview(submissionId)
				.orElseThrow(() -> ResourceSubmissionNotFoundException.byId(submissionId));

		requireNotSelfReview(moderator.userId(), submission.getSubmittedByUserId());
		requirePending(submission.getStatus());

		submission.reject(moderator.userId(), reason);

		auditRecorder.record(ContributionType.RESOURCE_SUBMISSION, submission.getId(), ModerationAction.REVIEW_DECISION,
				ModerationDecision.REJECTED, moderator, reason, null, null, null);

		return ResourceSubmissionModerationDetailResponse.from(submission);
	}

	private static void requireNotSelfReview(UUID moderatorId, UUID contributorId) {
		if (moderatorId.equals(contributorId)) {
			throw new SelfReviewNotAllowedException();
		}
	}

	private static void requirePending(SubmissionStatus status) {
		if (status != SubmissionStatus.PENDING_REVIEW) {
			throw new ContributionAlreadyReviewedException();
		}
	}

	/**
	 * Maps a submission's proposed fields onto a resource-creation command.
	 * {@code description}: this workflow's one field-mapping decision not
	 * already implied by matching column names — a submission collects both
	 * a required {@code shortDescription} and an optional {@code
	 * fullDescription}, but {@code CommunityResource} has always had exactly
	 * one description field (Milestone 3B). {@code fullDescription}, when
	 * present, is preferred as the richer text; otherwise {@code
	 * shortDescription} is used. {@code accessibilityInformation} is
	 * deliberately not carried forward — {@code CommunityResource} has no
	 * matching field (see ADR-016's "Known Limitations").
	 */
	private static CreateResourceCommand toCreateResourceCommand(ResourceSubmission submission) {
		String description = submission.getFullDescription() != null && !submission.getFullDescription().isBlank()
				? submission.getFullDescription()
				: submission.getShortDescription();
		return new CreateResourceCommand(
				submission.getCategory().getId(),
				submission.getName(),
				description,
				submission.getAddressLine1(),
				submission.getAddressLine2(),
				submission.getCity(),
				submission.getProvince(),
				submission.getPostalCode(),
				submission.getPhone(),
				submission.getEmail(),
				submission.getWebsiteUrl(),
				submission.getCostType(),
				null,
				submission.getEligibilityInformation());
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

	/** Default: {@code submittedAt} ascending (oldest pending first — see ADR-016's "Queue Ordering" section). */
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
