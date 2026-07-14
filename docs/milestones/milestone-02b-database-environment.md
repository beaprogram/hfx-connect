# Milestone 2B: Database Environment

## Objective

Provide a reproducible local database environment using PostgreSQL and PostGIS,
connect the Spring Boot backend to it safely through environment-based configuration,
introduce Flyway as the authoritative schema-migration mechanism, and expose an
operational backend health endpoint that reflects real database availability.

## Product Value

Nothing user-facing yet. The value is a reliable, reproducible local development
environment and a schema-change process (Flyway) that will hold up under real feature
development starting in Milestone 3 — without it, every later migration would risk
drifting between developers' machines and production.

## Technical Scope

**Infrastructure:**

- `docker-compose.yml`: a single `database` service running `postgis/postgis:17-3.5`
  (PostgreSQL 17.5, PostGIS 3.5.2), pinned to `platform: linux/amd64` (the official
  image publishes no arm64 build — see Risks), a named volume
  (`hfx_connect_postgres_data`), a `pg_isready` health check, and environment-variable
  substitution with safe local-development defaults (`POSTGRES_DB`, `POSTGRES_USER`,
  `POSTGRES_PASSWORD`, `POSTGRES_PORT`) so `docker compose up -d` works with zero
  configuration.
- Only the database service is defined; no unnecessary services are exposed.

**Backend:**

- New dependencies: `spring-boot-starter-jdbc` (a `DataSource`, without pulling in
  Hibernate/JPA, which nothing needs yet), `spring-boot-starter-actuator`,
  `org.postgresql:postgresql`, `flyway-core`, `flyway-database-postgresql`.
- `spring.datasource.*` configured from environment variables (`DB_HOST`, `DB_PORT`,
  `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`) with defaults matching
  `docker-compose.yml`, so no environment variables are required for standard local
  development.
- `com.hfxconnect.common.config.FlywayMigrationConfig`: Flyway is configured and
  triggered explicitly, because Spring Boot 4.1 does not ship built-in Flyway
  auto-configuration (see [ADR-004](../decisions/ADR-004-manual-flyway-configuration.md)).
- First Flyway migration, `V1__enable_postgis_extension.sql` — infrastructure-focused
  and minimal, as required; no application tables yet.
- `management.endpoints.web.exposure.include=health` and
  `management.endpoint.health.show-details=when-authorized`: only the health endpoint
  is exposed, and only a plain status (no component/connection detail) to
  unauthenticated requests.
- `backend/.env.example`: documents `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`
  with development-only example values.
- Testcontainers-backed integration tests (`spring-boot-testcontainers`,
  `testcontainers-junit-jupiter`, `testcontainers-postgresql`,
  `spring-boot-restclient`/`spring-boot-resttestclient`), running against the exact
  `postgis/postgis:17-3.5` image via `@ServiceConnection`, covering the application
  context, Flyway migration + idempotency, and the health endpoint.

## Out of Scope

Category or resource tables, category/resource APIs, authentication, frontend data
fetching, maps, Redis, CI/CD, and cloud deployment — all explicitly deferred to later
milestones.

## Acceptance Criteria

**Infrastructure**

- [x] `docker compose config` succeeds.
- [x] PostgreSQL/PostGIS starts successfully.
- [x] The database container reports `healthy`.
- [x] A named volume is configured.
- [x] PostGIS is available in the database (verified: `postgis_full_version()` returns
      `POSTGIS="3.5.2"`).
- [x] Normal stop/start cycles preserve data (verified with a manual marker row before
      Flyway was introduced, and again via `flyway_schema_history` surviving a
      `docker compose stop`/`start` cycle after migration).
- [x] Destructive volume-removal behavior is documented (`docker compose down -v`).

**Backend**

- [x] Backend connects using environment-based configuration.
- [x] No credentials are hard-coded in tracked source files (development-only defaults
      live in `application.properties`/`docker-compose.yml`/`.env.example`, consistently,
      and are clearly labelled as development-only; real secrets are never committed).
- [x] Flyway runs successfully (verified against both a fresh Testcontainers database
      and the real docker-compose database).
- [x] The baseline migration is recorded in `flyway_schema_history`.
- [x] Repeated startup is idempotent (verified: second startup logs "Schema \"public\"
      is up to date. No migration necessary." and the history table still has exactly
      one row).
- [x] Backend starts only with a valid database connection (verified: stopping the
      database causes context startup to fail with a clear connection-refused error;
      no lenient/dev-only bypass profile was introduced since the strict behavior is
      correct and desired).
- [x] Health endpoint reports application and database health safely (verified: `UP`
      when the database is reachable, no credentials/connection strings/stack traces in
      the response body).
- [x] Relevant tests pass (`./mvnw test`, `./mvnw verify` — 6/6 tests).

**Documentation**

- [x] Setup instructions work from a clean checkout (the exact commands in
      `backend/README.md` and the root README were the commands actually run and
      verified during this milestone).
- [x] Environment-variable instructions are accurate.
- [x] Startup and shutdown commands are documented.
- [x] Migration behavior is documented.
- [x] Troubleshooting guidance covers a real port conflict encountered during this
      milestone (a pre-existing native PostgreSQL install on port 5432), a Colima/Docker
      socket-detection gotcha, and credential-change-after-first-init behavior.
- [x] Known limitations are stated honestly.

**Security**

- [x] `.env` files are ignored (verified: `git check-ignore -v backend/.env`).
- [x] Example credentials are clearly development-only (`.env.example` and
      `docker-compose.yml` comments say so explicitly).
- [x] No secret is committed.
- [x] Actuator exposure is minimal (`health` only).
- [x] Health details do not leak sensitive information to unauthenticated requests.

## Testing Requirements

`docker compose config`, `docker compose up -d`, `docker compose ps` (healthy),
`./mvnw test`, `./mvnw verify` — all run and passed. Manual verification against the
real docker-compose database (not just Testcontainers) covered: fresh-database
migration, idempotent restart, fail-fast without a database connection, data
persistence across a container restart, and the health endpoint's response shape —
all documented in the development log with the actual commands and output observed.

## Documentation Requirements

`backend/README.md` (rewritten with database setup, health endpoint, testing, and
troubleshooting), root `README.md` (status, local setup, architecture, known
limitations), `docs/database/README.md` (the real first migration), this milestone
document, two task documents, a development-log entry, one new ADR
([ADR-004](../decisions/ADR-004-manual-flyway-configuration.md)), and career-evidence/
interview-notes updates.

## Security Considerations

See the Security acceptance criteria above. Additionally: the official
`postgis/postgis` image was pulled and smoke-tested before being wired into
`docker-compose.yml`, so its behavior (including that it does not publish arm64
builds) was understood before being depended on.

## Accessibility Considerations

Not applicable — no user interface changes in this milestone.

## Risks

| Risk | Mitigation |
|---|---|
| `postgis/postgis` publishes no `linux/arm64` image, so it runs under emulation on Apple Silicon dev machines | Pinned `platform: linux/amd64` explicitly and smoke-tested that the image boots and PostGIS works correctly under emulation before adopting it; documented the trade-off. Production deployment targets (Milestone 12) run on amd64 infrastructure regardless, so this does not affect production. |
| Spring Boot 4.1 silently dropped Flyway auto-configuration, which could easily have gone unnoticed (migrations "just don't run," no error) | Diagnosed by direct classpath inspection rather than guessing; fixed with an explicit, documented configuration class (ADR-004) rather than a workaround; covered by an integration test that would fail loudly if migrations stopped running. |
| Local port 5432 conflicts are common (this exact conflict was hit during this milestone, against a pre-existing native PostgreSQL install) | `docker-compose.yml` already supports a `POSTGRES_PORT` override with a safe default; documented the exact symptom and fix in `backend/README.md`'s troubleshooting section from firsthand experience, not speculation. |
| Fail-fast startup (no database = no application) could be mistaken for a bug rather than intended behavior | Explicitly tested and documented as intended behavior in this milestone's acceptance criteria and in `backend/README.md`. |

## Completion Summary

All planned Milestone 2B deliverables were completed and verified against a real,
running PostgreSQL/PostGIS instance (not only mocked or assumed): Docker Compose
infrastructure, environment-based backend database configuration, Flyway migrations
(including a genuine "Spring Boot 4.1 removed Flyway auto-configuration" issue that was
diagnosed and fixed rather than routed around), a safe Actuator health endpoint, and
Testcontainers-backed integration tests. A real local-environment port conflict was
encountered, diagnosed, resolved without touching unrelated infrastructure, and turned
into troubleshooting documentation. No category/resource schema, APIs, or
authentication were introduced, consistent with the milestone's explicit scope.
