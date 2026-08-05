package com.hfxconnect.resourcesubmission;

import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.resource.CategoryNotFoundException;
import com.hfxconnect.resource.InactiveCategoryException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All resource-submission business logic (Milestone 8B): creating a
 * pending proposal, owner-scoped listing/detail, and withdrawal. Every
 * method here is scoped by a {@code userId} the caller ({@code
 * ResourceSubmissionController}) derives exclusively from the authenticated
 * {@code CurrentUserPrincipal} — nothing in this class ever accepts a user
 * id as request data, matching {@code SavedResourceService}'s established
 * pattern (Milestone 8A).
 *
 * <p>{@code JwtAuthenticationFilter} already re-loads the account and
 * confirms it is {@code ACTIVE} before any request reaches this service
 * (ADR-009), so this service does not re-check account status itself.
 */
@Service
public class ResourceSubmissionService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("submittedAt", "updatedAt", "status");

	private final ResourceSubmissionRepository submissionRepository;
	private final CategoryRepository categoryRepository;

	public ResourceSubmissionService(ResourceSubmissionRepository submissionRepository, CategoryRepository categoryRepository) {
		this.submissionRepository = submissionRepository;
		this.categoryRepository = categoryRepository;
	}

	/**
	 * Creates a new pending submission for {@code userId}. The referenced
	 * category must exist and be active — the same "reference validation"
	 * {@code ResourceService.create} already performs for real resource
	 * creation. Does not create a {@code CommunityResource} row.
	 */
	@Transactional
	public ResourceSubmissionResponse create(UUID userId, ResourceSubmissionCreateRequest request) {
		Category category = categoryRepository.findById(request.categoryId())
				.orElseThrow(() -> CategoryNotFoundException.forId(request.categoryId()));
		if (!category.isActive()) {
			throw InactiveCategoryException.forId(request.categoryId());
		}

		ResourceSubmissionValidation.Normalized normalized = ResourceSubmissionValidation.validate(
				request.name(), request.shortDescription(), request.fullDescription(), request.addressLine1(),
				request.addressLine2(), request.city(), request.province(), request.postalCode(), request.phone(),
				request.email(), request.websiteUrl(), request.eligibilityInformation(), request.accessibilityInformation());

		ResourceSubmission submission = new ResourceSubmission(
				userId, category, normalized.name(), normalized.normalizedName(), normalized.shortDescription(),
				normalized.fullDescription(), normalized.addressLine1(), normalized.addressLine2(), normalized.city(),
				normalized.province(), normalized.postalCode(), normalized.phone(), normalized.email(),
				normalized.websiteUrl(), ResourceSubmissionValidation.resolveCostType(request.costType()),
				normalized.eligibilityInformation(), normalized.accessibilityInformation());

		try {
			// saveAndFlush: ResourceSubmission.id is a Hibernate-generated UUID
			// (not IDENTITY), so a plain save() may defer the physical INSERT
			// past this method's own try block — the same reasoning
			// ResourceService.create and SavedResourceService.save already
			// document for their own synchronous-conflict-catch needs.
			ResourceSubmission saved = submissionRepository.saveAndFlush(submission);
			return ResourceSubmissionResponse.from(saved);
		} catch (DataIntegrityViolationException ex) {
			// resource_submissions_pending_duplicate_key (V9): the caller
			// already has a PENDING_REVIEW submission for this exact
			// category+normalized-name. Unlike a saved-resource race (Milestone
			// 8A), this is a real conflict, not an idempotent success — a
			// submission is a distinct, reviewable item each time, not a
			// toggleable relation.
			throw ResourceSubmissionConflictException.duplicatePending(normalized.name());
		}
	}

	/** Lists {@code userId}'s own submissions, newest-submitted first by default. */
	@Transactional(readOnly = true)
	public ResourceSubmissionPageResponse list(UUID userId, int page, int size, String sort) {
		Pageable pageable = pageable(page, size);
		Page<ResourceSubmission> results = switch (resolveSort(sort)) {
			case "updatedAt" -> submissionRepository.findByOwnerOrderByUpdatedAtDesc(userId, pageable);
			case "status" -> submissionRepository.findByOwnerOrderByStatus(userId, pageable);
			default -> submissionRepository.findByOwnerOrderBySubmittedAtDesc(userId, pageable);
		};

		return new ResourceSubmissionPageResponse(
				results.getContent().stream().map(ResourceSubmissionResponse::from).toList(),
				results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	/**
	 * Returns one submission {@code userId} owns. Throws the same {@code
	 * 404} whether the id doesn't exist at all or belongs to a different
	 * account — see {@link ResourceSubmissionNotFoundException}.
	 */
	@Transactional(readOnly = true)
	public ResourceSubmissionResponse get(UUID userId, UUID submissionId) {
		return submissionRepository.findByIdAndOwner(submissionId, userId)
				.map(ResourceSubmissionResponse::from)
				.orElseThrow(() -> ResourceSubmissionNotFoundException.byId(submissionId));
	}

	/**
	 * Withdraws {@code userId}'s own submission — only legal while it is
	 * still {@code PENDING_REVIEW} (see {@link ResourceSubmission#withdraw}).
	 * {@code saveAndFlush}, not a bare mutation left to auto-flush: within a
	 * single flush, Hibernate's action queue orders entity insertions before
	 * updates regardless of call order, so a withdrawal immediately followed
	 * by a resubmission of the same name/category (two independent requests
	 * in production, but sharing one flush if ever chained without a
	 * boundary between them) could otherwise have its own UPDATE physically
	 * applied *after* the new row's INSERT already ran — flushing here makes
	 * the withdrawal durable before this method returns, not merely dirty-
	 * tracked in memory.
	 */
	@Transactional
	public ResourceSubmissionResponse withdraw(UUID userId, UUID submissionId) {
		ResourceSubmission submission = submissionRepository.findByIdAndOwner(submissionId, userId)
				.orElseThrow(() -> ResourceSubmissionNotFoundException.byId(submissionId));
		submission.withdraw();
		submissionRepository.saveAndFlush(submission);
		return ResourceSubmissionResponse.from(submission);
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
