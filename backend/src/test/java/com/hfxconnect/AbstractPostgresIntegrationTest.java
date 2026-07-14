package com.hfxconnect;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real docker-compose database image (not a generic postgres image) so
 * migrations and health checks are verified against the same PostGIS-enabled
 * PostgreSQL the application actually runs against locally and in production.
 *
 * <p>Deliberately uses the Testcontainers "singleton container" pattern (a plain
 * static field started once, with no {@code @Container}/{@code @Testcontainers}
 * annotations) rather than a per-class-managed container, so the same container
 * is reused across every subclass in this test run instead of being stopped and
 * restarted between test classes. Testcontainers' Ryuk resource reaper cleans it
 * up when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractPostgresIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

	static {
		POSTGRES.start();
	}

}
