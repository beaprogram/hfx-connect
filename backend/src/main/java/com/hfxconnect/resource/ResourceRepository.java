package com.hfxconnect.resource;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResourceRepository extends JpaRepository<CommunityResource, UUID> {

	boolean existsBySlug(String slug);

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

	@Query("SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.slug = :slug AND r.active = true")
	Optional<CommunityResource> findBySlugAndActiveTrueWithCategory(String slug);

	/**
	 * The single query behind the public resource listing (Milestone 6A) —
	 * active-only, with two independently optional filters: {@code categoryId}
	 * and a keyword {@code likePattern}. Expressed as one parameterized query
	 * with {@code (:param IS NULL OR ...)} predicates rather than as separate
	 * methods per filter combination — see ADR-010's "One Unified Query"
	 * section for why this replaced the previous
	 * {@code findByActiveWithCategory}/{@code findByCategoryIdAndActiveWithCategory}
	 * pair.
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
	 */
	@Query(value = "SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.active = true "
			+ "AND (:categoryId IS NULL OR r.category.id = :categoryId) "
			+ "AND (:likePattern IS NULL OR "
			+ "LOWER(r.name) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.description) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.addressLine1) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.city) LIKE :likePattern ESCAPE '\\')",
			countQuery = "SELECT count(r) FROM CommunityResource r WHERE r.active = true "
			+ "AND (:categoryId IS NULL OR r.category.id = :categoryId) "
			+ "AND (:likePattern IS NULL OR "
			+ "LOWER(r.name) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.description) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.addressLine1) LIKE :likePattern ESCAPE '\\' OR "
			+ "LOWER(r.city) LIKE :likePattern ESCAPE '\\')")
	Page<CommunityResource> search(Long categoryId, String likePattern, Pageable pageable);

}
