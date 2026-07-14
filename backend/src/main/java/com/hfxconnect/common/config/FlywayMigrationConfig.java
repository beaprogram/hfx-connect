package com.hfxconnect.common.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs Flyway migrations against the primary {@link DataSource} on startup.
 *
 * <p>Spring Boot 4.1 no longer ships built-in Flyway auto-configuration (the
 * {@code FlywayAutoConfiguration} class present in earlier Spring Boot versions
 * is not part of {@code spring-boot-autoconfigure} 4.1.0), so migrations are
 * triggered explicitly here instead. Because this bean's factory method calls
 * {@link Flyway#migrate()} eagerly during singleton bean creation, migration
 * runs as part of application context startup, before the application is
 * considered ready — a failed migration fails application startup, which is
 * the intended fail-fast behavior.
 */
@Configuration
public class FlywayMigrationConfig {

	@Bean
	public Flyway flyway(DataSource dataSource) {
		Flyway flyway = Flyway.configure()
				.dataSource(dataSource)
				.load();
		flyway.migrate();
		return flyway;
	}

}
