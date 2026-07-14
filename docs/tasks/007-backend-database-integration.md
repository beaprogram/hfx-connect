# Task 007: Backend Database Integration

## Objective

Connect the Spring Boot backend to the local PostgreSQL/PostGIS database through
environment-based configuration, make Flyway the authoritative schema-migration
mechanism, and expose a safe, database-aware health endpoint — backed by real
integration tests, not just manual verification.

## Context

Part of Milestone 2B (Database Environment), depending on Task 006's running
database. This is the task where the backend stops being a bare application shell and
becomes a database-connected service, without yet introducing any domain schema.

## Scope

- Dependencies: `spring-boot-starter-jdbc`, `spring-boot-starter-actuator`,
  `org.postgresql:postgresql`, `flyway-core`, `flyway-database-postgresql`
  (main); `spring-boot-testcontainers`, `testcontainers-junit-jupiter`,
  `testcontainers-postgresql`, `spring-boot-restclient`, `spring-boot-resttestclient`
  (test).
- `spring.datasource.*` configured from `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`
  environment variables, defaulting to values matching `docker-compose.yml`.
- `com.hfxconnect.common.config.FlywayMigrationConfig` — Flyway is triggered
  explicitly because Spring Boot 4.1 does not auto-configure it (see
  [ADR-004](../decisions/ADR-004-manual-flyway-configuration.md); this was discovered
  during this task, not assumed in advance).
- `V1__enable_postgis_extension.sql` — the first, deliberately minimal migration.
- Actuator configured to expose only `health`, with `show-details=when-authorized`.
- `backend/.env.example`.
- `AbstractPostgresIntegrationTest` (shared Testcontainers singleton-container setup
  using the real `postgis/postgis:17-3.5` image, not a generic Postgres image),
  `FlywayMigrationIntegrationTest`, `HealthEndpointIntegrationTest`; updated
  `HfxConnectApplicationTests` to extend the shared base.

## Out of Scope

Any category/resource entity, repository, or API; JPA/Hibernate (not needed yet —
plain JDBC is sufficient for connectivity and health verification, per the milestone's
explicit guidance not to add Spring Data JPA before it's required).

## Acceptance Criteria

- Backend connects successfully using only environment-based configuration; no
  credentials are hard-coded in source.
- `./mvnw test` and `./mvnw verify` pass (6 tests: context load, 3 Flyway/migration
  tests, 2 health-endpoint tests).
- A second application startup against an already-migrated database logs "up to date,
  no migration necessary" and leaves exactly one row in `flyway_schema_history`
  (idempotency, verified both in the Testcontainers suite and manually against the
  real docker-compose database).
- Stopping the database causes the application to fail to start, with a clear
  connection error (fail-fast, verified manually).
- `/actuator/health` returns `UP` with the database reachable, and the response body
  never contains credentials, JDBC URLs, connection-pool internals, or stack traces.

## Technical Approach

Flyway migrations initially appeared to silently do nothing — no log output, no
`flyway_schema_history` table, no error. Diagnosed by searching every class in every
downloaded Spring Boot 4.1.0 artifact for `FlywayAutoConfiguration` and finding none,
confirming its removal rather than assuming misconfiguration on our part; fixed with an
explicit `@Configuration` bean rather than a workaround, and documented as
[ADR-004](../decisions/ADR-004-manual-flyway-configuration.md) so the reasoning isn't
lost to a future "cleanup."

Two further Spring Boot 4 API relocations were found and fixed the same way (by
inspecting the actual jar contents rather than guessing): `TestRestTemplate` moved to a
new `spring-boot-resttestclient` module (and now requires `@AutoConfigureTestRestTemplate`
explicitly), which itself depends on `RestTemplateBuilder` from a further new
`spring-boot-restclient` module. Testcontainers 2.x also relocated `PostgreSQLContainer`
to a new package and changed it from a generic to a concrete class.

Integration tests use the Testcontainers "singleton container" pattern (a plain static
field started once in a static initializer, no `@Container`/`@Testcontainers`
annotations) so one container is genuinely shared across all three test classes in a
run — this is what makes the idempotency test meaningful: the second Spring context to
start in the test run is migrating against a database Flyway already migrated in the
first context, the same way a real second `./mvnw spring-boot:run` would.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. Additionally, manual end-to-end verification against
the real docker-compose database (not just Testcontainers): fresh migration, idempotent
restart, fail-fast without a database, persistence across a container restart, and the
health endpoint's exact response shape — all recorded with real command output in the
development log.

## Result

Completed. All 6 automated tests pass; all manual verifications against the real
database succeeded, including diagnosing and working around a genuine local port
conflict with a pre-existing native PostgreSQL installation (documented in
`backend/README.md`'s troubleshooting section).

## Related Commit

`feat: connect backend to PostgreSQL with Flyway migrations and health checks`
