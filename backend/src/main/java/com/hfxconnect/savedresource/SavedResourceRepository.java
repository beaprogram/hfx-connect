package com.hfxconnect.savedresource;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SavedResourceRepository extends JpaRepository<SavedResource, Long> {

	/**
	 * The application-level half of the idempotent-save guard — narrows the
	 * concurrent-request race window, but the database's
	 * {@code saved_resources_user_resource_key} unique constraint (V8) is
	 * authoritative; see {@code SavedResourceService#save}.
	 */
	boolean existsByUserIdAndResource_Id(UUID userId, UUID resourceId);

	/**
	 * Idempotent by construction: deletes zero or one row and never throws
	 * either way — a derived Spring Data delete method, not a
	 * {@code @Modifying}/{@code @Query} one, the same simple style
	 * {@code ResourceOperatingHoursRepository#deleteByResourceId} already
	 * established.
	 */
	void deleteByUserIdAndResource_Id(UUID userId, UUID resourceId);

	/**
	 * The batch saved-status lookup (Milestone 8A's status endpoint) — one
	 * query for every candidate resource id, scoped to the current user only.
	 * Returns just the subset of {@code resourceIds} this user has actually
	 * saved; never any information about another user's saves.
	 */
	@Query("SELECT sr.resource.id FROM SavedResource sr WHERE sr.userId = :userId AND sr.resource.id IN :resourceIds")
	List<UUID> findSavedResourceIds(UUID userId, Collection<UUID> resourceIds);

	/**
	 * The paginated "list this user's active saved resources, newest first"
	 * query (the default/only sort={@code savedAt} option) — the primary read
	 * path {@code saved_resources_user_id_created_at_idx} (V8) exists for.
	 * See {@link #findActiveSavedResourcesByUserIdOrderByResourceName} for
	 * why sorting is two separate query methods rather than one
	 * dynamically-{@link Pageable}-sorted query: both need an explicit
	 * {@code countQuery} to stay correct alongside a {@code JOIN FETCH}
	 * (mirroring {@code ResourceRepository#search}'s established pattern),
	 * and keeping each sort's {@code ORDER BY} — including its {@code sr.id}
	 * tie-breaker for full determinism — as a fixed literal in its own query
	 * is simpler and safer than translating an allowlisted sort parameter
	 * into a dynamic {@code Sort} across a joined association.
	 */
	@Query(value = "SELECT sr FROM SavedResource sr JOIN FETCH sr.resource r JOIN FETCH r.category "
			+ "WHERE sr.userId = :userId AND r.active = true "
			+ "ORDER BY sr.createdAt DESC, sr.id DESC",
			countQuery = "SELECT count(sr) FROM SavedResource sr JOIN sr.resource r "
			+ "WHERE sr.userId = :userId AND r.active = true")
	Page<SavedResource> findActiveSavedResourcesByUserIdOrderBySavedAtDesc(UUID userId, Pageable pageable);

	/** The sort={@code name} option — resource name ascending, {@code sr.id} tie-breaker. See the {@code savedAt} overload's Javadoc. */
	@Query(value = "SELECT sr FROM SavedResource sr JOIN FETCH sr.resource r JOIN FETCH r.category "
			+ "WHERE sr.userId = :userId AND r.active = true "
			+ "ORDER BY r.name ASC, sr.id ASC",
			countQuery = "SELECT count(sr) FROM SavedResource sr JOIN sr.resource r "
			+ "WHERE sr.userId = :userId AND r.active = true")
	Page<SavedResource> findActiveSavedResourcesByUserIdOrderByResourceName(UUID userId, Pageable pageable);

}
