package com.hfxconnect.resource;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<CommunityResource, UUID> {

	boolean existsBySlug(String slug);

	Optional<CommunityResource> findBySlugAndActiveTrue(String slug);

	Page<CommunityResource> findByActive(boolean active, Pageable pageable);

	Page<CommunityResource> findByCategoryIdAndActive(Long categoryId, boolean active, Pageable pageable);

}
