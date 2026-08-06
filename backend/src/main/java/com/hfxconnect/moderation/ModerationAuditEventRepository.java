package com.hfxconnect.moderation;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModerationAuditEventRepository extends JpaRepository<ModerationAuditEvent, UUID> {

	/** One contribution's full audit history, oldest first — the moderation detail page's audit section. */
	@Query("SELECT e FROM ModerationAuditEvent e WHERE e.contributionType = :contributionType "
			+ "AND e.contributionId = :contributionId ORDER BY e.createdAt ASC, e.id ASC")
	Page<ModerationAuditEvent> findByContribution(ContributionType contributionType, UUID contributionId, Pageable pageable);

	/**
	 * The global moderator-only audit list, newest first — independently
	 * optional {@code contributionType}/{@code decision} filters. {@code
	 * actorId} and date-range filtering are deliberately not implemented in
	 * Milestone 9A (see ADR-016's "Audit API Scope" section) — nothing in
	 * this milestone's requirements justifies the added query complexity yet.
	 */
	@Query(value = "SELECT e FROM ModerationAuditEvent e WHERE "
			+ "(:contributionType IS NULL OR e.contributionType = :contributionType) "
			+ "AND (:decision IS NULL OR e.decision = :decision) ORDER BY e.createdAt DESC, e.id DESC",
			countQuery = "SELECT count(e) FROM ModerationAuditEvent e WHERE "
			+ "(:contributionType IS NULL OR e.contributionType = :contributionType) "
			+ "AND (:decision IS NULL OR e.decision = :decision)")
	Page<ModerationAuditEvent> findForGlobalList(ContributionType contributionType, ModerationDecision decision, Pageable pageable);

}
