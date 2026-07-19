package com.hfxconnect.category;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.common.text.SlugGenerator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

	static final int DEFAULT_PAGE_SIZE = 20;
	static final int MAX_PAGE_SIZE = 100;

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Transactional
	public CategoryResponse create(CategoryCreateRequest request) {
		String name = normalizeWhitespace(request.name());
		String normalizedName = name.toLowerCase(Locale.ROOT);
		String slug = SlugGenerator.generate(name)
				.orElseThrow(() -> new ValidationException(
						"The submitted category contains invalid information.",
						Map.of("name", "Category name must contain at least one letter or number.")));

		if (categoryRepository.existsByNormalizedName(normalizedName)) {
			throw CategoryConflictException.duplicateName(name);
		}
		if (categoryRepository.existsBySlug(slug)) {
			throw CategoryConflictException.duplicateSlug(slug);
		}

		String description = request.description() != null ? request.description().trim() : null;
		if (description != null && description.isEmpty()) {
			description = null;
		}

		Category category = new Category(name, normalizedName, slug, description);
		try {
			category = categoryRepository.save(category);
		} catch (DataIntegrityViolationException ex) {
			// The application-level checks above narrow the race window but do not
			// close it; the database's unique constraints are authoritative. A
			// concurrent request that created the same category between the checks
			// above and this save lands here instead of succeeding twice.
			throw translateDuplicateConstraint(ex, slug);
		}

		return CategoryResponse.from(category);
	}

	@Transactional(readOnly = true)
	public CategoryResponse getById(Long id) {
		Category category = categoryRepository.findById(id)
				.orElseThrow(() -> CategoryNotFoundException.byId(id));
		return CategoryResponse.from(category);
	}

	@Transactional(readOnly = true)
	public CategoryResponse getBySlug(String slug) {
		Category category = categoryRepository.findBySlug(slug)
				.orElseThrow(() -> CategoryNotFoundException.bySlug(slug));
		return CategoryResponse.from(category);
	}

	@Transactional(readOnly = true)
	public CategoryPageResponse list(int page, int size, Boolean active) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
		Page<Category> result = active == null
				? categoryRepository.findAll(pageable)
				: categoryRepository.findByActive(active, pageable);
		return CategoryPageResponse.from(result);
	}

	private static String normalizeWhitespace(String input) {
		return input.trim().replaceAll("\\s+", " ");
	}

	private static CategoryConflictException translateDuplicateConstraint(DataIntegrityViolationException ex, String slug) {
		String detail = Optional.ofNullable(ex.getRootCause())
				.map(Throwable::getMessage)
				.orElse("");
		if (detail.contains("categories_slug_key") || detail.contains(slug)) {
			return CategoryConflictException.duplicateSlug(slug);
		}
		return CategoryConflictException.duplicate();
	}

}
