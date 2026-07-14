# ADR-004: Manually Configuring Flyway Instead of Relying on Auto-Configuration

## Status

Accepted — 2026-07-13

## Context

Spring Boot has historically auto-configured a `Flyway` bean and run migrations on
startup automatically whenever `flyway-core` and a `DataSource` were present on the
classpath (`org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration`).
[ADR-002](ADR-002-postgresql-and-postgis.md) already commits this project to Flyway as
the sole schema authority.

While integrating Flyway in Milestone 2B, migrations silently never ran: no Flyway log
output appeared at startup, and `flyway_schema_history` was never created, even though
`flyway-core`, `flyway-database-postgresql`, and a working `DataSource` were all
present and connected successfully. Investigation (searching every class in every
Spring Boot 4.1.0 artifact) confirmed that `FlywayAutoConfiguration` does not exist
anywhere in Spring Boot 4.1.0's `spring-boot-autoconfigure` jar, and no replacement
artifact under `org.springframework.boot` or `org.flywaydb` provides it either.
Spring Boot 4.1 does not ship built-in Flyway auto-configuration.

## Decision

Configure Flyway explicitly with a small `@Configuration` class
(`com.hfxconnect.common.config.FlywayMigrationConfig`) that defines a `Flyway` bean,
built from the primary `DataSource`, and calls `.migrate()` inside the bean's factory
method. Because Spring eagerly instantiates singleton beans during context refresh,
migration runs as part of application startup, before the application is considered
ready — a failed migration fails application startup, preserving the fail-fast
behavior the project requires.

## Alternatives Considered

- **Wait for/depend on a future Spring Boot patch that restores auto-configuration.**
  Rejected: there is no indication this was an oversight rather than a deliberate
  removal, and blocking Milestone 2B on an assumption about a future release is not a
  reasonable engineering plan.
- **Run migrations out-of-band with the Flyway Maven plugin or CLI instead of at
  application startup.** Rejected: this would decouple "the application is running"
  from "the schema is current," reintroducing exactly the kind of manual, easy-to-forget
  step Flyway's Spring Boot integration exists to eliminate, and would weaken the
  "backend starts only with a valid database connection" requirement.
- **Use Hibernate/JPA's `ddl-auto` for schema management instead of Flyway.**
  Rejected outright — this is explicitly prohibited by the project's database
  standards regardless of the auto-configuration issue.

## Consequences

- `FlywayMigrationConfig` is a small piece of infrastructure code the project now
  owns and maintains, instead of relying entirely on a Spring Boot-provided default.
  It is deliberately minimal (one `@Bean` method) to keep that maintenance burden low.
- Every future Spring Boot upgrade must re-verify this assumption; if a future Spring
  Boot release reintroduces Flyway auto-configuration, `FlywayMigrationConfig` would
  need to be removed to avoid running migrations twice (once from each mechanism).
- This is documented here specifically so the reason is not lost — without this ADR,
  a future contributor might reasonably "clean up" `FlywayMigrationConfig` as
  redundant boilerplate, not realizing it is load-bearing on this Spring Boot version.
