package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V7 migration and {@link ResourceRepository}'s native
 * location/nearby queries against the actual {@code postgis/postgis:17-3.5}
 * image — see ADR-012.
 */
@Transactional
class ResourceLocationRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	// Halifax Central Library and Dalhousie University — real, asymmetric
	// coordinates (different magnitude and sign for lat vs. lon) specifically
	// so a latitude/longitude swap bug would be immediately, obviously wrong.
	private static final double LIBRARY_LAT = 44.6488;
	private static final double LIBRARY_LON = -63.5752;
	private static final double DALHOUSIE_LAT = 44.6366;
	private static final double DALHOUSIE_LON = -63.5934;
	// Far outside any practical Halifax search radius.
	private static final double TORONTO_LAT = 43.6532;
	private static final double TORONTO_LON = -79.3832;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationAddedTheLocationColumnAsGeographyPoint4326() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		String udtName = jdbcTemplate.queryForObject(
				"SELECT udt_name FROM information_schema.columns WHERE table_name = 'resources' AND column_name = 'location'",
				String.class);
		assertThat(udtName).isEqualTo("geography");

		Integer srid = jdbcTemplate.queryForObject(
				"SELECT srid FROM geography_columns WHERE f_table_name = 'resources' AND f_geography_column = 'location'",
				Integer.class);
		assertThat(srid).isEqualTo(4326);

		String geometryType = jdbcTemplate.queryForObject(
				"SELECT type FROM geography_columns WHERE f_table_name = 'resources' AND f_geography_column = 'location'",
				String.class);
		assertThat(geometryType).isEqualTo("Point");
	}

	@Test
	void gistSpatialIndexExists() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		Integer indexCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_indexes WHERE tablename = 'resources' "
						+ "AND indexname = 'idx_resources_location_gist' AND indexdef ILIKE '%USING gist%'",
				Integer.class);
		assertThat(indexCount).isEqualTo(1);
	}

	@Test
	void nullLocationRemainsValid() {
		CommunityResource resource = resource("Null Location Check");
		assertThat(resource.getId()).isNotNull();

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Boolean isNull = jdbcTemplate.queryForObject(
				"SELECT location IS NULL FROM resources WHERE id = ?", Boolean.class, resource.getId());
		assertThat(isNull).isTrue();
	}

	@Test
	void updateLocationPersistsLatitudeAndLongitudeWithoutSwapping() {
		CommunityResource resource = resource("Coordinate Round Trip Check");

		int updated = resourceRepository.updateLocation(resource.getId(), LIBRARY_LAT, LIBRARY_LON, Instant.now());
		assertThat(updated).isEqualTo(1);

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Double storedLatitude = jdbcTemplate.queryForObject(
				"SELECT ST_Y(location::geometry) FROM resources WHERE id = ?", Double.class, resource.getId());
		Double storedLongitude = jdbcTemplate.queryForObject(
				"SELECT ST_X(location::geometry) FROM resources WHERE id = ?", Double.class, resource.getId());

		assertThat(storedLatitude).isEqualTo(LIBRARY_LAT);
		assertThat(storedLongitude).isEqualTo(LIBRARY_LON);
	}

	@Test
	void replacingALocationOverwritesThePreviousOne() {
		CommunityResource resource = resource("Replace Location Check");
		resourceRepository.updateLocation(resource.getId(), TORONTO_LAT, TORONTO_LON, Instant.now());

		resourceRepository.updateLocation(resource.getId(), LIBRARY_LAT, LIBRARY_LON, Instant.now());

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Double storedLatitude = jdbcTemplate.queryForObject(
				"SELECT ST_Y(location::geometry) FROM resources WHERE id = ?", Double.class, resource.getId());
		assertThat(storedLatitude).isEqualTo(LIBRARY_LAT);
	}

	@Test
	void findNearbyReturnsAResourceInsideTheRadiusOrderedByDistance() {
		// Two resources at the same distance from the origin as each other
		// (Dalhousie vs. a point offset from the library by the same amount
		// in both axes) would be ambiguous to order — instead this proves
		// ordering by comparing an inner (near) and an outer (far) ring
		// resource, both scoped to this test's own marker so pre-existing
		// data from other test classes sharing the same database cannot
		// change the outcome.
		String marker = UUID.randomUUID().toString();
		CommunityResource near = resource("Nearby Inside Radius " + marker);
		CommunityResource far = resource("Nearby Outside Radius " + marker);
		resourceRepository.updateLocation(near.getId(), DALHOUSIE_LAT, DALHOUSIE_LON, Instant.now());
		resourceRepository.updateLocation(far.getId(), LIBRARY_LAT + 0.05, LIBRARY_LON + 0.05, Instant.now());

		var page = resourceRepository.findNearby(LIBRARY_LAT, LIBRARY_LON, 10_000, null,
				ResourceSearchQuery.toLikePattern(marker), null, null, false,
				"MONDAY", "SUNDAY", LocalTime.NOON, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(NearbyResourceProjection::getId)
				.containsExactly(near.getId(), far.getId());
		assertThat(page.getContent().get(0).getDistanceMeters()).isGreaterThan(0);
	}

	@Test
	void findNearbyExcludesResourcesWithNoLocation() {
		CommunityResource noLocation = resource("Nearby No Location Check");

		var page = resourceRepository.findNearby(LIBRARY_LAT, LIBRARY_LON, 50_000, null, null, null, null, false,
				"MONDAY", "SUNDAY", LocalTime.NOON, PageRequest.of(0, 100));

		assertThat(page.getContent()).extracting(NearbyResourceProjection::getId).doesNotContain(noLocation.getId());
	}

	@Test
	void findNearbyExcludesInactiveResources() {
		CommunityResource resource = resource("Nearby Inactive Check");
		resourceRepository.updateLocation(resource.getId(), LIBRARY_LAT, LIBRARY_LON, Instant.now());
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		var page = resourceRepository.findNearby(LIBRARY_LAT, LIBRARY_LON, 5_000, null, null, null, null, false,
				"MONDAY", "SUNDAY", LocalTime.NOON, PageRequest.of(0, 100));

		assertThat(page.getContent()).extracting(NearbyResourceProjection::getId).doesNotContain(resource.getId());
	}

	@Test
	void findNearbyCountMatchesContentSize() {
		CommunityResource resource = resource("Nearby Count Check");
		resourceRepository.updateLocation(resource.getId(), LIBRARY_LAT, LIBRARY_LON, Instant.now());

		var page = resourceRepository.findNearby(LIBRARY_LAT, LIBRARY_LON, 5_000, null, null, null, null, false,
				"MONDAY", "SUNDAY", LocalTime.NOON, PageRequest.of(0, 1));

		assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void findNearbyProjectionExposesCategoryAndCoreFields() {
		Category category = category("Nearby Projection Check");
		CommunityResource resource = resourceInCategory(category, "Nearby Projection Resource");
		resourceRepository.updateLocation(resource.getId(), LIBRARY_LAT, LIBRARY_LON, Instant.now());

		var page = resourceRepository.findNearby(LIBRARY_LAT, LIBRARY_LON, 5_000, null, null, null, null, false,
				"MONDAY", "SUNDAY", LocalTime.NOON, PageRequest.of(0, 100));

		NearbyResourceProjection found = page.getContent().stream()
				.filter(p -> p.getId().equals(resource.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(found.getName()).isEqualTo(resource.getName());
		assertThat(found.getCategoryId()).isEqualTo(category.getId());
		assertThat(found.getCategoryName()).isEqualTo(category.getName());
		assertThat(found.getLatitude()).isEqualTo(LIBRARY_LAT);
		assertThat(found.getLongitude()).isEqualTo(LIBRARY_LON);
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "cat-" + marker, null));
	}

	private CommunityResource resource(String namePrefix) {
		return resourceInCategory(category(namePrefix), namePrefix + " Resource");
	}

	private CommunityResource resourceInCategory(Category category, String name) {
		String marker = UUID.randomUUID().toString();
		CommunityResource resource = new CommunityResource(category, name + " " + marker, "resource-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

}
