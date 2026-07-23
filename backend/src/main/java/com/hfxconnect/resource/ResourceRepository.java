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

	@Query(value = "SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.active = :active",
			countQuery = "SELECT count(r) FROM CommunityResource r WHERE r.active = :active")
	Page<CommunityResource> findByActiveWithCategory(boolean active, Pageable pageable);

	@Query(value = "SELECT r FROM CommunityResource r JOIN FETCH r.category WHERE r.category.id = :categoryId AND r.active = :active",
			countQuery = "SELECT count(r) FROM CommunityResource r WHERE r.category.id = :categoryId AND r.active = :active")
	Page<CommunityResource> findByCategoryIdAndActiveWithCategory(Long categoryId, boolean active, Pageable pageable);

}
