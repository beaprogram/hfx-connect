package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
 * Exercises the real V3 migration and the database constraints it creates,
 * against the actual {@code postgis/postgis:17-3.5} image — not H2, not
 * mocks. Follows the exact pattern
 * {@code category.CategoryRepositoryIntegrationTest} established.
 */
@Transactional
class ResourceRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private EntityManager entityManager;

	@Test
	void migrationCreatedTheResourcesTableWithExpectedColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> columns = jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'resources'",
				String.class);

		assertThat(columns).containsExactlyInAnyOrder(
				"id", "category_id", "name", "slug", "description", "address_line_1", "address_line_2",
				"city", "province", "postal_code", "phone", "email", "website_url", "cost_type",
				"cost_details", "eligibility", "verification_status", "active", "created_at", "updated_at",
				// "location" added by V7 (Milestone 7A) — see ADR-012.
				"location");
	}

	@Test
	void resourcePersistsWithItsCategory() {
		Category category = category("Persist Check");

		CommunityResource saved = save(category, "Persist Check Resource " + UUID.randomUUID());

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCategory().getId()).isEqualTo(category.getId());
	}

	@Test
	void findBySlugAndActiveTrueReturnsTheMatchingResource() {
		Category category = category("Active Slug Lookup");
		CommunityResource saved = save(category, "Active Slug Lookup Resource " + UUID.randomUUID());

		Optional<CommunityResource> found = resourceRepository.findBySlugAndActiveTrue(saved.getSlug());

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(saved.getId());
	}

	@Test
	void findBySlugAndActiveTrueExcludesDeactivatedResources() {
		Category category = category("Inactive Exclusion");
		CommunityResource saved = save(category, "Inactive Exclusion Resource " + UUID.randomUUID());

		saved.deactivate();
		resourceRepository.saveAndFlush(saved);

		assertThat(resourceRepository.findBySlugAndActiveTrue(saved.getSlug())).isEmpty();
	}

	@Test
	void findByActiveOnlyReturnsActiveResources() {
		Category category = category("Active Pagination");
		String marker = UUID.randomUUID().toString();
		save(category, "Active Pagination Resource " + marker);

		Page<CommunityResource> page = resourceRepository.findByActive(true, PageRequest.of(0, 50, Sort.by("name")));

		assertThat(page.getContent()).extracting(CommunityResource::getName)
				.contains("Active Pagination Resource " + marker);
	}

	@Test
	void findByCategoryIdAndActiveOnlyReturnsResourcesInThatCategory() {
		Category categoryA = category("Category Filter A");
		Category categoryB = category("Category Filter B");
		String marker = UUID.randomUUID().toString();
		save(categoryA, "Category Filter Resource A " + marker);
		save(categoryB, "Category Filter Resource B " + marker);

		Page<CommunityResource> page = resourceRepository.findByCategoryIdAndActive(
				categoryA.getId(), true, PageRequest.of(0, 50, Sort.by("name")));

		assertThat(page.getContent()).extracting(CommunityResource::getName)
				.contains("Category Filter Resource A " + marker)
				.doesNotContain("Category Filter Resource B " + marker);
	}

	@Test
	void timestampsArePopulatedOnInsert() {
		Instant before = Instant.now().minusSeconds(1);
		Category category = category("Timestamp Check");

		CommunityResource saved = save(category, "Timestamp Check Resource " + UUID.randomUUID());

		assertThat(saved.getCreatedAt()).isAfter(before);
		assertThat(saved.getUpdatedAt()).isAfter(before);
	}

	@Test
	void enumFieldsRoundTripCorrectly() {
		Category category = category("Enum Round Trip");
		CommunityResource resource = new CommunityResource(category, "Enum Round Trip Resource " + UUID.randomUUID(),
				"enum-round-trip-" + UUID.randomUUID(), "Description", "123 Main St", null, "Halifax", "NS",
				"B3H 4R2", null, null, null, CostType.LOW_COST, null, null);

		CommunityResource saved = resourceRepository.saveAndFlush(resource);
		CommunityResource reloaded = resourceRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getCostType()).isEqualTo(CostType.LOW_COST);
		assertThat(reloaded.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
	}

	@Test
	void slugMustBeUniqueAtTheDatabaseLevel() {
		Category category = category("Slug Uniqueness");
		String slug = "shared-slug-" + UUID.randomUUID();
		saveWithSlug(category, "Slug A " + UUID.randomUUID(), slug);

		assertThatThrownBy(() -> saveWithSlug(category, "Slug B " + UUID.randomUUID(), slug))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAReferencedCategoryIsRejected() {
		Category category = category("Delete Restriction");
		save(category, "Delete Restriction Resource " + UUID.randomUUID());
		Long categoryId = category.getId();

		// Detach everything first and re-fetch the category fresh: deleting the
		// same in-memory Category instance the still-managed CommunityResource
		// already references confuses Hibernate's own in-memory consistency
		// check (it throws TransientPropertyValueException before any SQL is
		// even sent), which would mask the real thing this test verifies — that
		// PostgreSQL's ON DELETE RESTRICT rejects the DELETE statement itself.
		entityManager.flush();
		entityManager.clear();
		Category detachedCategory = categoryRepository.findById(categoryId).orElseThrow();

		assertThatThrownBy(() -> {
			categoryRepository.delete(detachedCategory);
			categoryRepository.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void invalidCostTypeIsRejectedAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Category category = category("Invalid Cost Type");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resources (id, category_id, name, slug, description, address_line_1, city, "
						+ "province, postal_code, cost_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), category.getId(), "Bad Cost Type", "bad-cost-type-" + UUID.randomUUID(),
				"Description", "123 Main St", "Halifax", "NS", "B3H 4R2", "NOT_A_REAL_COST_TYPE"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void invalidProvinceIsRejectedAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Category category = category("Invalid Province");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resources (id, category_id, name, slug, description, address_line_1, city, "
						+ "province, postal_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), category.getId(), "Bad Province", "bad-province-" + UUID.randomUUID(),
				"Description", "123 Main St", "Halifax", "ZZ", "B3H 4R2"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void invalidSlugFormatIsRejectedAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Category category = category("Invalid Slug Format");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resources (id, category_id, name, slug, description, address_line_1, city, "
						+ "province, postal_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), category.getId(), "Bad Slug", "-leading-hyphen", "Description", "123 Main St",
				"Halifax", "NS", "B3H 4R2"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void requiredFieldsCannotBeNull() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Category category = category("Required Fields");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resources (id, category_id, slug, description, address_line_1, city, province, "
						+ "postal_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), category.getId(), "no-name-" + UUID.randomUUID(), "Description", "123 Main St",
				"Halifax", "NS", "B3H 4R2"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "cat-" + marker, null));
	}

	private CommunityResource save(Category category, String name) {
		return saveWithSlug(category, name, "resource-" + UUID.randomUUID());
	}

	private CommunityResource saveWithSlug(Category category, String name, String slug) {
		CommunityResource resource = new CommunityResource(category, name, slug, "A helpful community resource.",
				"123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

}
