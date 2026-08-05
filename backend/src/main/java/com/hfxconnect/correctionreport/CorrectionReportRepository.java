package com.hfxconnect.correctionreport;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
