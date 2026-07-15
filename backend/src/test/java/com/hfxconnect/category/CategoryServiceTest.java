package com.hfxconnect.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.ValidationException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Pure unit tests of {@link CategoryService}'s business rules, using a mocked
 * {@link CategoryRepository} so the exception-translation and race-condition
 * handling paths can be exercised deterministically without needing genuine
 * database concurrency. Real database behavior (constraints, persistence) is
 * covered separately by {@link CategoryRepositoryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	private CategoryService categoryService;

	@BeforeEach
	void setUp() {
		categoryService = new CategoryService(categoryRepository);
	}

	@Test
	void createNormalizesWhitespaceAndGeneratesADeterministicSlug() {
		when(categoryRepository.existsByNormalizedName("study spaces")).thenReturn(false);
		when(categoryRepository.existsBySlug("study-spaces")).thenReturn(false);
		when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> asIfPersisted(invocation.getArgument(0), 1L));

		CategoryResponse response = categoryService.create(new CategoryCreateRequest("  Study   Spaces  ", null));

		assertThat(response.name()).isEqualTo("Study Spaces");
		assertThat(response.slug()).isEqualTo("study-spaces");
	}

	@Test
	void createTrimsBlankDescriptionToNull() {
		when(categoryRepository.existsByNormalizedName(any())).thenReturn(false);
		when(categoryRepository.existsBySlug(any())).thenReturn(false);
		when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> asIfPersisted(invocation.getArgument(0), 1L));

		CategoryResponse response = categoryService.create(new CategoryCreateRequest("Recreation", "   "));

		assertThat(response.description()).isNull();
	}

	@Test
	void createRejectsNamesThatNormalizeToAnEmptySlug() {
		assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("&&&", null)))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("invalid information");

		verify(categoryRepository, never()).save(any());
	}

	@Test
	void createRejectsDuplicateNormalizedName() {
		when(categoryRepository.existsByNormalizedName("food assistance")).thenReturn(true);

		assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("Food Assistance", null)))
				.isInstanceOf(CategoryConflictException.class)
				.hasMessageContaining("Food Assistance");

		verify(categoryRepository, never()).save(any());
	}

	@Test
	void createRejectsDuplicateSlugEvenWhenNameDiffers() {
		when(categoryRepository.existsByNormalizedName("food, assistance!")).thenReturn(false);
		when(categoryRepository.existsBySlug("food-assistance")).thenReturn(true);

		assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("Food, Assistance!", null)))
				.isInstanceOf(CategoryConflictException.class)
				.hasMessageContaining("food-assistance");

		verify(categoryRepository, never()).save(any());
	}

	@Test
	void createTranslatesADatabaseRaceConditionIntoAConflict() {
		// Simulates two concurrent requests both passing the application-level
		// exists() pre-checks before either has committed, so the database's
		// unique constraint is what actually rejects the second insert.
		when(categoryRepository.existsByNormalizedName(any())).thenReturn(false);
		when(categoryRepository.existsBySlug(any())).thenReturn(false);
		when(categoryRepository.save(any(Category.class)))
				.thenThrow(new DataIntegrityViolationException(
						"duplicate key value violates unique constraint \"categories_slug_key\""));

		assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("Food Assistance", null)))
				.isInstanceOf(CategoryConflictException.class);
	}

	@Test
	void getByIdThrowsNotFoundWhenMissing() {
		when(categoryRepository.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> categoryService.getById(42L))
				.isInstanceOf(CategoryNotFoundException.class)
				.hasMessageContaining("42");
	}

	@Test
	void getBySlugThrowsNotFoundWhenMissing() {
		when(categoryRepository.findBySlug("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> categoryService.getBySlug("missing"))
				.isInstanceOf(CategoryNotFoundException.class)
				.hasMessageContaining("missing");
	}

	@Test
	void getBySlugReturnsMappedResponseWhenFound() {
		Category category = new Category(5L, "Recreation", "recreation", "recreation", null, true,
				Instant.now(), Instant.now());
		when(categoryRepository.findBySlug("recreation")).thenReturn(Optional.of(category));

		CategoryResponse response = categoryService.getBySlug("recreation");

		assertThat(response.id()).isEqualTo(5L);
		assertThat(response.slug()).isEqualTo("recreation");
	}

	@Test
	void listRejectsNegativePage() {
		assertThatThrownBy(() -> categoryService.list(-1, 20, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listRejectsSizeBelowOne() {
		assertThatThrownBy(() -> categoryService.list(0, 0, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listRejectsSizeAboveMaximum() {
		assertThatThrownBy(() -> categoryService.list(0, CategoryService.MAX_PAGE_SIZE + 1, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listUsesTheActiveFilterWhenProvided() {
		Page<Category> page = new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0);
		when(categoryRepository.findByActive(true, PageRequest.of(0, 20,
				org.springframework.data.domain.Sort.by("name").ascending()))).thenReturn(page);

		categoryService.list(0, 20, true);

		verify(categoryRepository).findByActive(true, PageRequest.of(0, 20,
				org.springframework.data.domain.Sort.by("name").ascending()));
	}

	@Test
	void listUsesFindAllWhenNoActiveFilterIsProvided() {
		Page<Category> page = new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0);
		when(categoryRepository.findAll(PageRequest.of(0, 20,
				org.springframework.data.domain.Sort.by("name").ascending()))).thenReturn(page);

		categoryService.list(0, 20, null);

		verify(categoryRepository).findAll(PageRequest.of(0, 20,
				org.springframework.data.domain.Sort.by("name").ascending()));
	}

	/** Simulates what {@code save()} would return after JPA assigns an ID and {@code @PrePersist} runs. */
	private static Category asIfPersisted(Category category, Long id) {
		return new Category(id, category.getName(), category.getNormalizedName(), category.getSlug(),
				category.getDescription(), category.isActive(), Instant.now(), Instant.now());
	}

}
