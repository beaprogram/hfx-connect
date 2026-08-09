package com.hfxconnect.organization;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

	boolean existsBySlug(String slug);

	Optional<Organization> findByOwnerUserId(UUID ownerUserId);

	/** Public lookup — only ever used for a currently-{@code VERIFIED} organization; see {@code OrganizationService.getPublicBySlug}. */
	Optional<Organization> findBySlugAndVerificationStatus(String slug, OrganizationVerificationStatus verificationStatus);

	/**
	 * Row-locking lookup for the review (verify/reject/suspend) flow — see
	 * {@code com.hfxconnect.resourcesubmission.ResourceSubmissionRepository
	 * .findByIdForReview}'s identical reasoning (ADR-016, reused here per
	 * ADR-017's "Concurrency Strategy" section).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Organization o WHERE o.id = :id")
	Optional<Organization> findByIdForReview(UUID id);

	/** The admin organization queue — independently optional {@code verificationStatus} filter, ordering left to the caller's {@link Pageable}. */
	@Query(value = "SELECT o FROM Organization o WHERE (:verificationStatus IS NULL OR o.verificationStatus = :verificationStatus)",
			countQuery = "SELECT count(o) FROM Organization o WHERE (:verificationStatus IS NULL OR o.verificationStatus = :verificationStatus)")
	Page<Organization> findForAdminQueue(OrganizationVerificationStatus verificationStatus, Pageable pageable);

	/**
	 * Batch-loaded, verified-only organization summaries for the public
	 * resource response (Milestone 10A) — the same "one query for a whole
	 * page of resources, never one per card" posture
	 * {@code ResourceService.loadHoursByResourceIds} already established.
	 * Deliberately filters to {@code VERIFIED} in the query itself: a
	 * pending/rejected/suspended organization's id simply never comes back,
	 * which is what keeps attribution omitted for all three cases with one
	 * rule instead of three (ADR-017).
	 */
	List<Organization> findByIdInAndVerificationStatus(Collection<UUID> ids, OrganizationVerificationStatus verificationStatus);

}
