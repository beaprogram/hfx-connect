package com.hfxconnect.common.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
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

	/**
	 * Without built-in Flyway auto-configuration, Spring has no reason to
	 * create the {@code flyway} bean before JPA's {@code entityManagerFactory}
	 * bean — the two have no dependency on each other in the bean graph, so
	 * Hibernate's schema validation (ddl-auto=validate) can run against a
	 * database Flyway hasn't migrated yet, failing with a spurious "missing
	 * table" error even though the migration would have succeeded a moment
	 * later. Forcing the ordering explicitly (the same mechanism the removed
	 * {@code FlywayAutoConfiguration} used internally) fixes this.
	 */
	@Bean
	static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnFlywayPostProcessor() {
		return new EntityManagerFactoryDependsOnPostProcessor("flyway");
	}

}
