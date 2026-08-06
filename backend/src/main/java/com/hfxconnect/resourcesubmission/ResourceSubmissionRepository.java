package com.hfxconnect.resourcesubmission;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ResourceSubmissionRepository extends JpaRepository<ResourceSubmission, UUID> {

	@Query(value = "SELECT s FROM ResourceSubmission s JOIN FETCH s.category "
			+ "WHERE s.submittedByUserId = :userId ORDER BY s.submittedAt DESC, s.id DESC",
			countQuery = "SELECT count(s) FROM ResourceSubmission s WHERE s.submittedByUserId = :userId")
	Page<ResourceSubmission> findByOwnerOrderBySubmittedAtDesc(UUID userId, Pageable pageable);

	@Query(value = "SELECT s FROM ResourceSubmission s JOIN FETCH s.category "
			+ "WHERE s.submittedByUserId = :userId ORDER BY s.updatedAt DESC, s.id DESC",
			countQuery = "SELECT count(s) FROM ResourceSubmission s WHERE s.submittedByUserId = :userId")
	Page<ResourceSubmission> findByOwnerOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

	@Query(value = "SELECT s FROM ResourceSubmission s JOIN FETCH s.category "
			+ "WHERE s.submittedByUserId = :userId ORDER BY s.status ASC, s.submittedAt DESC, s.id DESC",
			countQuery = "SELECT count(s) FROM ResourceSubmission s WHERE s.submittedByUserId = :userId")
	Page<ResourceSubmission> findByOwnerOrderByStatus(UUID userId, Pageable pageable);

	/**
	 * A single owned submission, scoped by both id and owner in the query
	 * itself — never loaded by id alone and then checked in Java, so there is
	 * no code path that could accidentally skip the ownership check.
	 */
	@Query("SELECT s FROM ResourceSubmission s JOIN FETCH s.category "
			+ "WHERE s.id = :id AND s.submittedByUserId = :userId")
	Optional<ResourceSubmission> findByIdAndOwner(UUID id, UUID userId);

	/**
	 * Not owner-scoped — moderators review any account's submission. {@code
	 * JOIN FETCH} the category since the moderation detail/queue responses
	 * and the approval flow's {@code CreateResourceCommand} both need it.
	 */
	@Query("SELECT s FROM ResourceSubmission s JOIN FETCH s.category WHERE s.id = :id")
	Optional<ResourceSubmission> findByIdForModeration(UUID id);

	/**
	 * Row-locking lookup used only by the review (approve/reject) flow — see
	 * ADR-016's "Concurrency Control" section. Holds the lock for the whole
	 * review transaction: a second moderator's concurrent
	 * {@code findByIdForReview} call on the same id blocks until the first
	 * transaction commits or rolls back, then re-reads the now-current status
	 * and fails fast with {@link ContributionAlreadyReviewedException} rather
	 * than relying on a stale pre-check.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM ResourceSubmission s JOIN FETCH s.category WHERE s.id = :id")
	Optional<ResourceSubmission> findByIdForReview(UUID id);

	/**
	 * The moderation queue (Milestone 9A) — independently optional {@code
	 * status}/{@code categoryId} filters, ordering left to the caller's
	 * {@link Pageable} (matching {@code ResourceRepository.search}'s
	 * established "no ORDER BY in the query itself" convention).
	 */
	@Query(value = "SELECT s FROM ResourceSubmission s JOIN FETCH s.category WHERE "
			+ "(:status IS NULL OR s.status = :status) AND (:categoryId IS NULL OR s.category.id = :categoryId)",
			countQuery = "SELECT count(s) FROM ResourceSubmission s WHERE "
			+ "(:status IS NULL OR s.status = :status) AND (:categoryId IS NULL OR s.category.id = :categoryId)")
	Page<ResourceSubmission> findForModerationQueue(SubmissionStatus status, Long categoryId, Pageable pageable);

}
