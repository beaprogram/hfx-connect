package com.hfxconnect.category;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	boolean existsByNormalizedName(String normalizedName);

	boolean existsBySlug(String slug);

	Optional<Category> findBySlug(String slug);

	Page<Category> findByActive(boolean active, Pageable pageable);

}
