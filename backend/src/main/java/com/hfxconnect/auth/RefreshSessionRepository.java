package com.hfxconnect.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

	Optional<RefreshSession> findByTokenHash(String tokenHash);

	/**
	 * Bulk-revokes every currently-active session in a family in one
	 * statement — used by reuse detection, where "all of them" (an unknown,
	 * unbounded count) must be revoked atomically rather than loaded and
	 * saved one at a time. {@code clearAutomatically = true}: a bulk JPQL
	 * update bypasses the persistence context entirely, so without this,
	 * any of these rows already loaded in the current session would keep
	 * showing their stale, pre-update {@code revokedAt} value in memory
	 * until the context is cleared.
	 */
	@Modifying(clearAutomatically = true)
	@Query("UPDATE RefreshSession s SET s.revokedAt = :now WHERE s.familyId = :familyId AND s.revokedAt IS NULL")
	int revokeAllActiveInFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

}
