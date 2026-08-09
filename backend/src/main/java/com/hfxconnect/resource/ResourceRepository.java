package com.hfxconnect.resource;

import jakarta.persistence.LockModeType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ResourceRepository extends JpaRepository<CommunityResource, UUID> {

	boolean existsBySlug(String slug);

	/**
	 * Row-locking lookup used only by the Milestone 9A correction-report
	 * approval flow: two pending correction reports can target the same
	 * resource, and applying both concurrently without locking the resource
	 * row itself (not just the correction_report row each report's own
	 * moderation-queue lock already protects) would be a classic lost-update
	 * race — one moderator's committed field change silently overwritten by
	 * the other's stale in-memory copy of the "current" resource state. See
	 * ADR-016 for the full concurrency-control rationale.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM CommunityResource r WHERE r.id = :id")
	Optional<CommunityResource> findByIdForUpdate(UUID id);

	Optional<CommunityResource> findBySlugAndActiveTrue(String slug);

	Page<CommunityResource> findByActive(boolean active, Pageable pageable);

	Page<CommunityResource> findByCategoryIdAndActive(Long categoryId, boolean active, Pageable pageable);

	/**
	 * {@code JOIN FETCH} variants used by the public read paths that need to
	 * render a category summary in the response — a plain lazy
	 * {@code category} reference would otherwise trigger one extra query per
	 * resource (N+1) once the response mapping calls
	 * {@code category.getName()}/{@code getSlug()}. Explicit {@code countQuery}
	 * on the paginated variants is required for {@code @Query} +
	 * {@link Pageable}; the {@code category} join is a to-one relationship,
	 * so it never multiplies result rows the way a to-many fetch join would.
	 */
	@Query("SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.id = :id")
	Optional<CommunityResource> findByIdWithCategory(UUID id);

	/**
	 * Batch resource-summary lookup for an ownership-claim list/queue
	 * (Milestone 10A) — one query for a whole page of claims, never one per
	 * row, the same posture {@code findByOrganizationIdWithCategory} and
	 * {@code ResourceService}'s hours/organization batch loaders already
	 * establish.
	 */
	@Query("SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.id IN :ids")
	List<CommunityResource> findByIdInWithCategory(Collection<UUID> ids);

	@Query("SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.slug = :slug AND r.active = true")
	Optional<CommunityResource> findBySlugAndActiveTrueWithCategory(String slug);

	/**
	 * An organization's owned-resource list (Milestone 10A) — both active
	 * and inactive owned resources are returned; the caller decides how to
	 * present each (see {@code OrganizationResourceService}). {@code
	 * organizationId} is a plain column, not a relationship, so this is a
	 * simple equality filter rather than a join condition.
	 */
	@Query(value = "SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.organizationId = :organizationId",
			countQuery = "SELECT count(r) FROM CommunityResource r WHERE r.organizationId = :organizationId")
	Page<CommunityResource> findByOrganizationIdWithCategory(UUID organizationId, Pageable pageable);

	/**
	 * The single query behind the public resource listing (Milestone 6A,
	 * extended in 6B) — active-only, with independently optional filters:
	 * {@code categoryId}, a keyword {@code likePattern}, {@code costType},
	 * {@code verificationStatus}, and {@code openNowOnly}. Expressed as one
	 * parameterized query with {@code (:param IS NULL OR ...)} predicates
	 * rather than as separate methods per filter combination — see ADR-010's
	 * "One Unified Query" section, extended by ADR-011's "Open-Now
	 * Filtering" section for why {@code openNowOnly} is a correlated
	 * {@code EXISTS} subquery evaluated in the database rather than filtered
	 * in the service layer after pagination (which would corrupt
	 * {@code totalElements}/{@code totalPages}).
	 *
	 * <p>{@code likePattern} is always either {@code null} (no keyword filter)
	 * or an already-lowercased, already-escaped, {@code "%"}-wrapped literal
	 * built by {@link ResourceSearchQuery#toLikePattern} — never raw user
	 * input concatenated into this query string. {@code ESCAPE '\'} is a
	 * fixed literal in the query text itself (not user-controlled),
	 * declaring backslash as the pattern's escape character so a literal
	 * {@code %} or {@code _} in a search phrase matches literally instead of
	 * acting as an unintended wildcard. Searched fields: {@code name},
	 * {@code description}, {@code addressLine1}, {@code city} — see ADR-010
	 * for why province/postal code/category name/contact fields are
	 * deliberately excluded.
	 *
	 * <p>{@code today}/{@code yesterday}/{@code now} are the caller's Halifax
	 * "now" (computed once per request/page by {@code ResourceService} via
	 * {@code OpenNowCalculator#nowInHalifax}), never the database session's
	 * own idea of "now" — bind parameters only, never string-concatenated.
	 * The {@code EXISTS} subquery mirrors {@code OpenNowCalculator}'s exact
	 * same-day/overnight/overnight-continuation logic against
	 * {@code ResourceOperatingHours} rows, so the count query and the page
	 * query agree with the single-resource calculation exactly.
	 */
	@Query(value = "SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.active = true "
			+ "AND (:categoryId IS NULL OR r.category.id = :categoryId) "
			+ "AND (:likePattern IS NULL OR "
			+ "LOWER(r.name) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.description) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.addressLine1) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.city) LIKE :likePattern ESCAPE '\\') "
			+ "AND (:costType IS NULL OR r.costType = :costType) "
			+ "AND (:verificationStatus IS NULL OR r.verificationStatus = :verificationStatus) "
			+ "AND (:openNowOnly = false OR EXISTS (SELECT 1 FROM ResourceOperatingHours h WHERE h.resourceId = r.id "
			+ "AND h.closed = false AND ("
			+ "(h.dayOfWeek = :today AND h.opensAt < h.closesAt AND :now >= h.opensAt AND :now < h.closesAt) OR "
			+ "(h.dayOfWeek = :today AND h.opensAt > h.closesAt AND :now >= h.opensAt) OR "
			+ "(h.dayOfWeek = :yesterday AND h.opensAt > h.closesAt AND :now < h.closesAt))))",
			countQuery = "SELECT count(r) FROM CommunityResource r WHERE r.active = true "
			+ "AND (:categoryId IS NULL OR r.category.id = :categoryId) "
			+ "AND (:likePattern IS NULL OR "
			+ "LOWER(r.name) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.description) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.addressLine1) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.city) LIKE :likePattern ESCAPE '\\') "
			+ "AND (:costType IS NULL OR r.costType = :costType) "
			+ "AND (:verificationStatus IS NULL OR r.verificationStatus = :verificationStatus) "
			+ "AND (:openNowOnly = false OR EXISTS (SELECT 1 FROM ResourceOperatingHours h WHERE h.resourceId = r.id "
			+ "AND h.closed = false AND ("
			+ "(h.dayOfWeek = :today AND h.opensAt < h.closesAt AND :now >= h.opensAt AND :now < h.closesAt) OR "
			+ "(h.dayOfWeek = :today AND h.opensAt > h.closesAt AND :now >= h.opensAt) OR "
			+ "(h.dayOfWeek = :yesterday AND h.opensAt > h.closesAt AND :now < h.closesAt))))")
	Page<CommunityResource> search(Long categoryId, String likePattern, CostType costType,
			VerificationStatus verificationStatus, boolean openNowOnly, DayOfWeek today, DayOfWeek yesterday,
			LocalTime now, Pageable pageable);

	/**
	 * Replaces a resource's coordinate directly via native SQL — see
	 * ADR-012 for why {@code location} is never mapped as a Hibernate
	 * entity field. {@code longitude} is bound first, matching
	 * {@code ST_MakePoint(x, y)}'s own X-then-Y (longitude-then-latitude)
	 * parameter order; never swapped. {@code clearAutomatically}: nothing
	 * else in this transaction re-reads the entity, but clearing the
	 * persistence context is the standard, defensive precaution for a bulk/
	 * native update that bypasses the entity's own in-memory state.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = "UPDATE resources SET location = ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, "
			+ "updated_at = :updatedAt WHERE id = :id", nativeQuery = true)
	int updateLocation(UUID id, double latitude, double longitude, Instant updatedAt);

	/**
	 * The nearby-search query (Milestone 7B — see ADR-012). A native SQL
	 * query, not JPQL: {@code ST_DWithin}/{@code ST_Distance} have no JPQL
	 * equivalent. Otherwise follows the exact same shape ADR-010/ADR-011
	 * established for {@link #search} — every filter independently
	 * optional via {@code (:param IS NULL OR ...)} predicates (SQL column
	 * names here instead of JPQL property names), the identical
	 * same-day/overnight/overnight-continuation {@code EXISTS} subquery for
	 * {@code openNowOnly}, and an explicit {@code countQuery} sharing the
	 * identical {@code WHERE} clause so pagination totals stay exact.
	 *
	 * <p>{@code longitude} is always bound before {@code latitude} to
	 * {@code ST_MakePoint} — never swapped (see ADR-012's "Coordinate
	 * Order" section). {@code radiusMetres} is the caller's {@code radiusKm}
	 * already converted to metres by {@code ResourceService}.
	 *
	 * <p>{@code ORDER BY} is a fixed literal — distance ascending, then
	 * {@code name}, then {@code id} as deterministic tie-breakers — never
	 * influenced by a caller-supplied sort parameter; nearby search has
	 * exactly one meaningful order.
	 */
	@Query(value = "SELECT r.id AS id, r.name AS name, r.slug AS slug, r.city AS city, r.province AS province, "
			+ "r.cost_type AS costType, r.verification_status AS verificationStatus, r.active AS active, "
			+ "r.created_at AS createdAt, c.id AS categoryId, c.name AS categoryName, c.slug AS categorySlug, "
			+ "r.organization_id AS organizationId, "
			+ "ST_Y(r.location::geometry) AS latitude, ST_X(r.location::geometry) AS longitude, "
			+ "ST_Distance(r.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography) AS distanceMeters "
			+ "FROM resources r JOIN categories c ON c.id = r.category_id "
			+ "WHERE r.active = true AND r.location IS NOT NULL "
			+ "AND ST_DWithin(r.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMetres) "
			+ "AND (:categoryId IS NULL OR r.category_id = :categoryId) "
			+ "AND (:likePattern IS NULL OR "
			+ "LOWER(r.name) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.description) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.address_line_1) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.city) LIKE :likePattern ESCAPE '\\') "
			+ "AND (:costType IS NULL OR r.cost_type = :costType) "
			+ "AND (:verificationStatus IS NULL OR r.verification_status = :verificationStatus) "
			+ "AND (:openNowOnly = false OR EXISTS (SELECT 1 FROM resource_operating_hours h WHERE h.resource_id = r.id "
			+ "AND h.closed = false AND ("
			+ "(h.day_of_week = :today AND h.opens_at < h.closes_at AND CAST(:now AS time) >= h.opens_at AND CAST(:now AS time) < h.closes_at) OR "
			+ "(h.day_of_week = :today AND h.opens_at > h.closes_at AND CAST(:now AS time) >= h.opens_at) OR "
			+ "(h.day_of_week = :yesterday AND h.opens_at > h.closes_at AND CAST(:now AS time) < h.closes_at)))) "
			+ "ORDER BY distanceMeters ASC, r.name ASC, r.id ASC",
			countQuery = "SELECT count(*) "
			+ "FROM resources r "
			+ "WHERE r.active = true AND r.location IS NOT NULL "
			+ "AND ST_DWithin(r.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMetres) "
			+ "AND (:categoryId IS NULL OR r.category_id = :categoryId) "
			+ "AND (:likePattern IS NULL OR "
			+ "LOWER(r.name) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.description) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.address_line_1) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.city) LIKE :likePattern ESCAPE '\\') "
			+ "AND (:costType IS NULL OR r.cost_type = :costType) "
			+ "AND (:verificationStatus IS NULL OR r.verification_status = :verificationStatus) "
			+ "AND (:openNowOnly = false OR EXISTS (SELECT 1 FROM resource_operating_hours h WHERE h.resource_id = r.id "
			+ "AND h.closed = false AND ("
			+ "(h.day_of_week = :today AND h.opens_at < h.closes_at AND CAST(:now AS time) >= h.opens_at AND CAST(:now AS time) < h.closes_at) OR "
			+ "(h.day_of_week = :today AND h.opens_at > h.closes_at AND CAST(:now AS time) >= h.opens_at) OR "
			+ "(h.day_of_week = :yesterday AND h.opens_at > h.closes_at AND CAST(:now AS time) < h.closes_at))))",
			nativeQuery = true)
	Page<NearbyResourceProjection> findNearby(double latitude, double longitude, double radiusMetres,
			Long categoryId, String likePattern, String costType, String verificationStatus, boolean openNowOnly,
			String today, String yesterday, LocalTime now, Pageable pageable);

}
