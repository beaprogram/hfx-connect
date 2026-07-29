package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V6 migration and the database constraints it creates —
 * see ADR-011 and {@code V6__create_resource_operating_hours.sql}. Follows
 * the exact pattern {@code ResourceRepositoryIntegrationTest} established.
 */
@Transactional
class ResourceOperatingHoursRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ResourceOperatingHoursRepository operatingHoursRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationCreatedTheTableWithExpectedColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> columns = jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'resource_operating_hours'",
				String.class);

		assertThat(columns).containsExactlyInAnyOrder(
				"id", "resource_id", "day_of_week", "opens_at", "closes_at", "closed", "created_at", "updated_at");
	}

	@Test
	void anEntryPersistsAndReloadsWithItsResource() {
		CommunityResource resource = resource("Persist Check");

		ResourceOperatingHours saved = operatingHoursRepository.saveAndFlush(
				new ResourceOperatingHours(resource.getId(), java.time.DayOfWeek.MONDAY, false,
						LocalTime.of(9, 0), LocalTime.of(17, 0)));

		assertThat(saved.getId()).isNotNull();
		ResourceOperatingHours reloaded = operatingHoursRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getResourceId()).isEqualTo(resource.getId());
		assertThat(reloaded.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
		assertThat(reloaded.getOpensAt()).isEqualTo(LocalTime.of(9, 0));
		assertThat(reloaded.getClosesAt()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	void resourceAndDayOfWeekMustBeUniqueAtTheDatabaseLevel() {
		CommunityResource resource = resource("Unique Day Check");
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resource.getId(), java.time.DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		assertThatThrownBy(() -> operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resource.getId(), java.time.DayOfWeek.MONDAY, false, LocalTime.of(10, 0), LocalTime.of(18, 0))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void invalidDayOfWeekIsRejectedAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		CommunityResource resource = resource("Invalid Day Check");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_operating_hours (resource_id, day_of_week, opens_at, closes_at, closed) "
						+ "VALUES (?, ?, ?, ?, ?)",
				resource.getId(), "FUNDAY", LocalTime.of(9, 0), LocalTime.of(17, 0), false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aClosedDayCannotHaveTimesAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		CommunityResource resource = resource("Closed With Times Check");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_operating_hours (resource_id, day_of_week, opens_at, closes_at, closed) "
						+ "VALUES (?, ?, ?, ?, ?)",
				resource.getId(), "MONDAY", LocalTime.of(9, 0), LocalTime.of(17, 0), true))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anOpenDayRequiresBothTimesAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		CommunityResource resource = resource("Open Missing Times Check");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_operating_hours (resource_id, day_of_week, opens_at, closes_at, closed) "
						+ "VALUES (?, ?, ?, ?, ?)",
				resource.getId(), "MONDAY", LocalTime.of(9, 0), null, false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void equalOpensAtAndClosesAtIsRejectedAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		CommunityResource resource = resource("Equal Times Check");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_operating_hours (resource_id, day_of_week, opens_at, closes_at, closed) "
						+ "VALUES (?, ?, ?, ?, ?)",
				resource.getId(), "MONDAY", LocalTime.of(9, 0), LocalTime.of(9, 0), false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAResourceCascadesToItsOperatingHours() {
		CommunityResource resource = resource("Cascade Delete Check");
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resource.getId(), java.time.DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		resourceRepository.delete(resource);
		resourceRepository.flush();

		assertThat(operatingHoursRepository.findByResourceId(resource.getId())).isEmpty();
	}

	@Test
	void findByResourceIdReturnsOnlyThatResourcesEntries() {
		CommunityResource resourceA = resource("Find By Resource A");
		CommunityResource resourceB = resource("Find By Resource B");
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resourceA.getId(), java.time.DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resourceB.getId(), java.time.DayOfWeek.TUESDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		List<ResourceOperatingHours> found = operatingHoursRepository.findByResourceId(resourceA.getId());

		assertThat(found).hasSize(1);
		assertThat(found.get(0).getResourceId()).isEqualTo(resourceA.getId());
	}

	@Test
	void findByResourceIdInBatchLoadsEntriesForMultipleResources() {
		CommunityResource resourceA = resource("Batch Load A");
		CommunityResource resourceB = resource("Batch Load B");
		CommunityResource resourceC = resource("Batch Load C (no hours)");
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resourceA.getId(), java.time.DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resourceB.getId(), java.time.DayOfWeek.TUESDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		List<ResourceOperatingHours> found = operatingHoursRepository.findByResourceIdIn(
				List.of(resourceA.getId(), resourceB.getId(), resourceC.getId()));

		assertThat(found).extracting(ResourceOperatingHours::getResourceId)
				.containsExactlyInAnyOrder(resourceA.getId(), resourceB.getId());
	}

	@Test
	void deleteByResourceIdRemovesEveryEntryForThatResource() {
		CommunityResource resource = resource("Delete By Resource Check");
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resource.getId(), java.time.DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));
		operatingHoursRepository.saveAndFlush(new ResourceOperatingHours(
				resource.getId(), java.time.DayOfWeek.TUESDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)));

		operatingHoursRepository.deleteByResourceId(resource.getId());
		operatingHoursRepository.flush();

		assertThat(operatingHoursRepository.findByResourceId(resource.getId())).isEmpty();
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "cat-" + marker, null));
	}

	private CommunityResource resource(String namePrefix) {
		Category category = category(namePrefix);
		String marker = UUID.randomUUID().toString();
		CommunityResource resource = new CommunityResource(category, namePrefix + " " + marker,
				"resource-" + marker, "A helpful community resource.", "123 Main St", null, "Halifax", "NS",
				"B3H 4R2", null, null, null, CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

}
