package com.hfxconnect.resource;

import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.ValidationException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All resource business logic: category validation, field normalization,
 * slug generation, duplicate detection, transaction boundaries, and mapping
 * to/from the business-layer read models. See
 * {@code docs/architecture/backend-architecture.md} for the layering pattern
 * this follows (the same one {@code CategoryService} established).
 *
 * <p>Public read methods ({@link #getActiveById}, {@link #getActiveBySlug},
 * {@link #listActive}, {@link #listActiveByCategory}) only ever see active
 * resources — deactivated resources are treated as not found, the same
 * "public-style" visibility rule {@code ResourceController} relies on.
 */
@Service
public class ResourceService {

	static final int MAX_PAGE_SIZE = 100;

	/**
	 * Allowlisted values for the public {@code sort} query parameter — see
	 * {@code docs/api/README.md}. Arbitrary entity-field sorting is
	 * deliberately not permitted (it would leak internal field names into the
	 * public contract and let callers request expensive sorts with no index
	 * support).
	 */
	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("name", "createdAt");

	private final ResourceRepository resourceRepository;
	private final CategoryRepository categoryRepository;

	public ResourceService(ResourceRepository resourceRepository, CategoryRepository categoryRepository) {
		this.resourceRepository = resourceRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional
	public ResourceDetails create(CreateResourceCommand command) {
		Category category = requireActiveCategory(command.categoryId());

		ResourceValidation.Normalized fields = ResourceValidation.validate(
				command.name(), command.description(), command.addressLine1(), command.addressLine2(),
				command.city(), command.province(), command.postalCode(), command.phone(), command.email(),
				command.websiteUrl(), command.costDetails(), command.eligibility());

		if (resourceRepository.existsBySlug(fields.slug())) {
			throw ResourceConflictException.duplicateSlug(fields.slug());
		}

		CostType costType = command.costType() != null ? command.costType() : CostType.UNKNOWN;

		CommunityResource resource = new CommunityResource(category, fields.name(), fields.slug(),
				fields.description(), fields.addressLine1(), fields.addressLine2(), fields.city(),
				fields.province(), fields.postalCode(), fields.phone(), fields.email(), fields.websiteUrl(),
				costType, fields.costDetails(), fields.eligibility());

		try {
			// saveAndFlush (not save): CommunityResource's id is a Hibernate-
			// generated UUID assigned in memory, unlike Category's IDENTITY
			// column, so a plain save() is not guaranteed to hit the database
			// synchronously — the INSERT could otherwise be deferred to the
			// next flush, which would happen after this try/catch has already
			// exited. Flushing explicitly is what makes the race-condition
			// catch below reliable.
			resource = resourceRepository.saveAndFlush(resource);
		} catch (DataIntegrityViolationException ex) {
			// Same reasoning as CategoryService.create(): the exists() check
			// above narrows the race window but does not close it.
			throw ResourceConflictException.duplicateSlug(fields.slug());
		}

		return ResourceDetails.from(resource);
	}

	@Transactional
	public ResourceDetails update(UUID id, UpdateResourceCommand command) {
		CommunityResource resource = findRequiredById(id);
		Category category = requireActiveCategory(command.categoryId());

		ResourceValidation.Normalized fields = ResourceValidation.validate(
				command.name(), command.description(), command.addressLine1(), command.addressLine2(),
				command.city(), command.province(), command.postalCode(), command.phone(), command.email(),
				command.websiteUrl(), command.costDetails(), command.eligibility());

		CostType costType = command.costType() != null ? command.costType() : CostType.UNKNOWN;

		// fields.slug() is intentionally discarded here — the stored slug
		// never changes after creation (see CommunityResource#updateDetails
		// and ADR-005's slug-stability reasoning). Running it through
		// ResourceValidation still matters: it proves the (possibly renamed)
		// resource name is not something that would produce an empty slug.
		resource.updateDetails(category, fields.name(), fields.description(), fields.addressLine1(),
				fields.addressLine2(), fields.city(), fields.province(), fields.postalCode(), fields.phone(),
				fields.email(), fields.websiteUrl(), costType, fields.costDetails(), fields.eligibility());

		return ResourceDetails.from(resource);
	}

	@Transactional
	public void deactivate(UUID id) {
		findRequiredById(id).deactivate();
	}

	@Transactional(readOnly = true)
	public ResourceDetails getActiveById(UUID id) {
		CommunityResource resource = resourceRepository.findByIdWithCategory(id)
				.filter(CommunityResource::isActive)
				.orElseThrow(() -> ResourceNotFoundException.byId(id));
		return ResourceDetails.from(resource);
	}

	@Transactional(readOnly = true)
	public ResourceDetails getActiveBySlug(String slug) {
		CommunityResource resource = resourceRepository.findBySlugAndActiveTrueWithCategory(slug)
				.orElseThrow(() -> ResourceNotFoundException.bySlug(slug));
		return ResourceDetails.from(resource);
	}

	@Transactional(readOnly = true)
	public ResourcePage listActive(int page, int size, String sort) {
		return ResourcePage.from(resourceRepository.findByActiveWithCategory(true, pageable(page, size, sort)));
	}

	@Transactional(readOnly = true)
	public ResourcePage listActiveByCategory(Long categoryId, int page, int size, String sort) {
		return ResourcePage.from(
				resourceRepository.findByCategoryIdAndActiveWithCategory(categoryId, true, pageable(page, size, sort)));
	}

	private CommunityResource findRequiredById(UUID id) {
		return resourceRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.byId(id));
	}

	private Category requireActiveCategory(Long categoryId) {
		if (categoryId == null) {
			throw new ValidationException("The submitted resource contains invalid information.",
					Map.of("categoryId", "Category is required."));
		}
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> CategoryNotFoundException.forId(categoryId));
		if (!category.isActive()) {
			throw InactiveCategoryException.forId(categoryId);
		}
		return category;
	}

	private static Pageable pageable(int page, int size, String sort) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size, resolveSort(sort));
	}

	/**
	 * {@code sort=name} (the default) sorts ascending; {@code sort=createdAt}
	 * sorts newest-first, since a chronological listing is far more useful
	 * read newest-to-oldest than the reverse. No separate direction
	 * parameter is exposed — two fixed, documented behaviors are simpler to
	 * use and to test than a direction/field combinatorial surface, and
	 * nothing in the product requirements justifies more yet.
	 */
	private static Sort resolveSort(String sort) {
		if (sort == null || sort.isBlank() || "name".equals(sort)) {
			return Sort.by("name").ascending();
		}
		if ("createdAt".equals(sort)) {
			return Sort.by("createdAt").descending();
		}
		throw new InvalidSortException("sort must be one of " + ALLOWED_SORT_VALUES + " (got '" + sort + "').");
	}

}
