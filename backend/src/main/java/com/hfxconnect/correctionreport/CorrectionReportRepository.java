package com.hfxconnect.correctionreport;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CorrectionReportRepository extends JpaRepository<CorrectionReport, UUID> {

	// No JOIN FETCH on `resource` here: it is nullable (ON DELETE SET NULL —
	// see CorrectionReport's Javadoc) and every response field this domain
	// actually exposes (resourceNameSnapshot/resourceSlugSnapshot) already
	// lives on the report row itself, so there is nothing on the association
	// a response needs to eagerly load.

	@Query(value = "SELECT r FROM CorrectionReport r WHERE r.reportedByUserId = :userId "
			+ "ORDER BY r.submittedAt DESC, r.id DESC",
			countQuery = "SELECT count(r) FROM CorrectionReport r WHERE r.reportedByUserId = :userId")
	Page<CorrectionReport> findByOwnerOrderBySubmittedAtDesc(UUID userId, Pageable pageable);

	@Query(value = "SELECT r FROM CorrectionReport r WHERE r.reportedByUserId = :userId "
			+ "ORDER BY r.updatedAt DESC, r.id DESC",
			countQuery = "SELECT count(r) FROM CorrectionReport r WHERE r.reportedByUserId = :userId")
	Page<CorrectionReport> findByOwnerOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

	@Query(value = "SELECT r FROM CorrectionReport r WHERE r.reportedByUserId = :userId "
			+ "ORDER BY r.status ASC, r.submittedAt DESC, r.id DESC",
			countQuery = "SELECT count(r) FROM CorrectionReport r WHERE r.reportedByUserId = :userId")
	Page<CorrectionReport> findByOwnerOrderByStatus(UUID userId, Pageable pageable);

	/**
	 * A single owned report, scoped by both id and owner in the query
	 * itself — never loaded by id alone and then checked in Java, the same
	 * ownership-safety pattern {@code ResourceSubmissionRepository} uses.
	 */
	@Query("SELECT r FROM CorrectionReport r WHERE r.id = :id AND r.reportedByUserId = :userId")
	Optional<CorrectionReport> findByIdAndOwner(UUID id, UUID userId);

	/**
	 * Not owner-scoped — moderators review any account's report. {@code LEFT
	 * JOIN FETCH} (not a plain {@code JOIN FETCH}): {@code resource} is
	 * nullable (see this entity's own Javadoc), and a report whose target was
	 * deleted after submission must still be viewable by a moderator.
	 */
	@Query("SELECT r FROM CorrectionReport r LEFT JOIN FETCH r.resource WHERE r.id = :id")
	Optional<CorrectionReport> findByIdForModeration(UUID id);

	/**
	 * Row-locking lookup used only by the review (approve/reject) flow — see
	 * {@code ResourceSubmissionRepository.findByIdForReview}'s identical
	 * reasoning (ADR-016's "Concurrency Control" section). This lock is on
	 * the {@code correction_reports} row only; {@code
	 * ResourceRepository.findByIdForUpdate} separately locks the *target*
	 * resource row for the duration of applying changes, since two different
	 * pending reports can target the same resource.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM CorrectionReport r LEFT JOIN FETCH r.resource WHERE r.id = :id")
	Optional<CorrectionReport> findByIdForReview(UUID id);

	/**
	 * The moderation queue (Milestone 9A) — independently optional {@code
	 * status}/{@code issueType} filters, ordering left to the caller's
	 * {@link Pageable}.
	 */
	@Query(value = "SELECT r FROM CorrectionReport r WHERE "
			+ "(:status IS NULL OR r.status = :status) AND (:issueType IS NULL OR r.issueType = :issueType)",
			countQuery = "SELECT count(r) FROM CorrectionReport r WHERE "
			+ "(:status IS NULL OR r.status = :status) AND (:issueType IS NULL OR r.issueType = :issueType)")
	Page<CorrectionReport> findForModerationQueue(CorrectionReportStatus status, IssueType issueType, Pageable pageable);

}
