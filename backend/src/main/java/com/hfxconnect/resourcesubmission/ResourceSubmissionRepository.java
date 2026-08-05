package com.hfxconnect.resourcesubmission;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
