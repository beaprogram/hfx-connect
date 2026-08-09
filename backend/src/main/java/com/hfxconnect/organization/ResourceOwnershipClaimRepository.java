package com.hfxconnect.organization;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ResourceOwnershipClaimRepository extends JpaRepository<ResourceOwnershipClaim, UUID> {

	/** A single owned claim, scoped by both id and organization in the query itself — never checked in Java after an unscoped load. */
	@Query("SELECT c FROM ResourceOwnershipClaim c WHERE c.id = :id AND c.organizationId = :organizationId")
	Optional<ResourceOwnershipClaim> findByIdAndOrganizationId(UUID id, UUID organizationId);

	/** Pre-check for the "one pending claim per org/resource" rule — {@code resource_ownership_claims_pending_key} (V11) is the actual authority; this only narrows the race window. */
	boolean existsByOrganizationIdAndResourceIdAndStatus(UUID organizationId, UUID resourceId, ResourceOwnershipClaimStatus status);

	/** The current organization's own claim list — independently optional {@code status} filter, ordering left to the caller's {@link Pageable}. */
	@Query(value = "SELECT c FROM ResourceOwnershipClaim c WHERE c.organizationId = :organizationId "
			+ "AND (:status IS NULL OR c.status = :status)",
			countQuery = "SELECT count(c) FROM ResourceOwnershipClaim c WHERE c.organizationId = :organizationId "
			+ "AND (:status IS NULL OR c.status = :status)")
	Page<ResourceOwnershipClaim> findByOrganization(UUID organizationId, ResourceOwnershipClaimStatus status, Pageable pageable);

	/**
	 * Row-locking lookup for the admin approve/reject flow — see
	 * {@code OrganizationRepository.findByIdForReview}'s identical reasoning.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM ResourceOwnershipClaim c WHERE c.id = :id")
	Optional<ResourceOwnershipClaim> findByIdForReview(UUID id);

	/** The admin claim queue — independently optional {@code status}/{@code organizationId} filters. */
	@Query(value = "SELECT c FROM ResourceOwnershipClaim c WHERE "
			+ "(:status IS NULL OR c.status = :status) AND (:organizationId IS NULL OR c.organizationId = :organizationId)",
			countQuery = "SELECT count(c) FROM ResourceOwnershipClaim c WHERE "
			+ "(:status IS NULL OR c.status = :status) AND (:organizationId IS NULL OR c.organizationId = :organizationId)")
	Page<ResourceOwnershipClaim> findForAdminQueue(ResourceOwnershipClaimStatus status, UUID organizationId, Pageable pageable);

}
