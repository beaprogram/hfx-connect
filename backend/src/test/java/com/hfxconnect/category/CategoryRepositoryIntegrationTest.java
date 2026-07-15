package com.hfxconnect.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V2 migration and the database constraints it creates,
 * against the actual {@code postgis/postgis:17-3.5} image (via the shared
 * Testcontainers singleton container) — not H2, not mocks.
 *
 * <p>{@code @Transactional} rolls each test method back automatically, since
 * every test here runs entirely on the test thread (no separate HTTP
 * request thread is involved, unlike {@link CategoryApiIntegrationTest}).
 */
@Transactional
class CategoryRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationCreatedTheCategoriesTableWithExpectedColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> columns = jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'categories'",
				String.class);

		assertThat(columns).containsExactlyInAnyOrder(
				"id", "name", "normalized_name", "slug", "description", "active", "created_at", "updated_at");
	}

	@Test
	void nameIsRequiredAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO categories (normalized_name, slug) VALUES (?, ?)",
				"no name test " + UUID.randomUUID(), "no-name-test-" + UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void slugIsRequiredAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO categories (name, normalized_name) VALUES (?, ?)",
				"No Slug Test", "no slug test " + UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void slugMustBeUnique() {
		save("Recreation A", "recreation a " + UUID.randomUUID(), "shared-slug-" + UUID.randomUUID());
		String duplicateSlug = "already-used-" + UUID.randomUUID();
		save("Recreation B", "recreation b " + UUID.randomUUID(), duplicateSlug);

		assertThatThrownBy(() -> save("Recreation C", "recreation c " + UUID.randomUUID(), duplicateSlug))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void normalizedNameMustBeUnique() {
		String normalizedName = "unique name check " + UUID.randomUUID();
		save("Unique Name Check", normalizedName, "unique-name-check-" + UUID.randomUUID());

		assertThatThrownBy(() -> save("UNIQUE NAME CHECK", normalizedName, "different-slug-" + UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void activeDefaultsToTrueWhenCreatedThroughTheEntityConstructor() {
		Category saved = save("Default Active Check", "default active check " + UUID.randomUUID(),
				"default-active-check-" + UUID.randomUUID());

		assertThat(saved.isActive()).isTrue();
	}

	@Test
	void createdAtAndUpdatedAtAreSetOnInsert() {
		Instant before = Instant.now().minusSeconds(1);

		Category saved = save("Timestamp Check", "timestamp check " + UUID.randomUUID(),
				"timestamp-check-" + UUID.randomUUID());

		assertThat(saved.getCreatedAt()).isAfter(before);
		assertThat(saved.getUpdatedAt()).isAfter(before);
	}

	@Test
	void findBySlugReturnsTheMatchingCategory() {
		String slug = "find-by-slug-check-" + UUID.randomUUID();
		save("Find By Slug Check", "find by slug check " + UUID.randomUUID(), slug);

		Optional<Category> found = categoryRepository.findBySlug(slug);

		assertThat(found).isPresent();
		assertThat(found.get().getSlug()).isEqualTo(slug);
	}

	@Test
	void findBySlugIsEmptyWhenNoCategoryHasThatSlug() {
		assertThat(categoryRepository.findBySlug("does-not-exist-" + UUID.randomUUID())).isEmpty();
	}

	@Test
	void findByActiveOnlyReturnsMatchingRows() {
		String marker = UUID.randomUUID().toString();
		save("Active Filter Check " + marker, "active filter check " + marker, "active-filter-check-" + marker);

		Page<Category> activeOnly = categoryRepository.findByActive(true, PageRequest.of(0, 50, Sort.by("name")));

		assertThat(activeOnly.getContent())
				.extracting(Category::getName)
				.contains("Active Filter Check " + marker);
	}

	private Category save(String name, String normalizedName, String slug) {
		return categoryRepository.save(new Category(name, normalizedName, slug, null));
	}

}
