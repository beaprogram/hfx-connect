package com.hfxconnect;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void baselineMigrationIsRecordedAsSuccessful() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		Integer success = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
				Integer.class);

		assertThat(success).isEqualTo(1);
	}

	@Test
	void postgisExtensionIsEnabledByTheBaselineMigration() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		Integer extensionCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class);

		assertThat(extensionCount).isEqualTo(1);
	}

	@Test
	void repeatedApplicationStartupsAgainstTheSameDatabaseAreIdempotent() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		Integer historyRowCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history", Integer.class);

		// AbstractPostgresIntegrationTest uses one singleton container shared by
		// every test class in this run. HfxConnectApplicationTests already
		// started a Spring application context (and therefore ran Flyway)
		// against this exact database before this test class's context started.
		// If Flyway had reapplied the migration on this second startup, or
		// failed a checksum check against an already-applied migration, either
		// this context would have failed to start or this table would contain
		// more than one row for version 1. Exactly one row proves the second
		// startup correctly recognized the schema as already current.
		assertThat(historyRowCount).isEqualTo(1);
	}

}
