# Resume Evidence

This document records, feature by feature, the concrete evidence behind every resume
claim about HFX Connect. It exists so that every resume bullet drafted from this
project is traceable to real, committed, tested work — not aspiration.

**Status: early.** Milestone 1 was documentation-only. Milestone 2A added the first
real, tested code (application shells for both the backend and frontend). Milestone 2B
connected the backend to a real, migrated PostgreSQL/PostGIS database. Milestone 3A
delivered the first real feature — category management — end to end. Feature-level
entries (search, moderation, authentication) will continue to be added as those
milestones land.

## How an Entry Is Added

Each entry follows this structure:

```markdown
## <Feature Name>

### Product Purpose
Why this feature exists from the user's perspective.

### Technologies Used
Specific libraries, frameworks, and techniques — not just the top-level stack.

### Engineering Complexity
What made this non-trivial: validation, concurrency, transactions, geospatial
queries, authorization, etc.

### Implementation
Where the code lives (file paths / package names) and how it fits the architecture.

### Tests
What is actually tested, and how (unit, integration, Testcontainers, Playwright).

### Evidence
Commit hashes, PR links, or a short reproducible demonstration.

### Potential Resume Wording
Action verb + system/feature + technologies + engineering complexity + verified
result.

### Measurements Still Needed
Anything not yet measured is written as `[MEASURE AFTER DEPLOYMENT]` rather than
invented. Examples: load time, test coverage percentage, real user counts.
```

## Rules

- Never invent user counts, production usage, percentages, performance numbers, test
  coverage, accessibility scores, or user feedback.
- Use `[MEASURE AFTER DEPLOYMENT]` as an explicit placeholder until a real measurement
  exists, and replace it once it does.
- Only document decisions and features that actually exist in the committed code at
  the time of writing.

## Entries

## Backend Application Bootstrap

### Product Purpose
Establishes a runnable, testable Spring Boot service as the foundation for every
later API feature — nothing user-facing yet, but a prerequisite for all of it.

### Technologies Used
Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`), Maven Wrapper.

### Engineering Complexity
Resolved a real dependency-resolution failure: the Spring Initializr-generated parent
POM version (`4.1.0.RELEASE`) does not correspond to an actual published Maven Central
coordinate for Spring Boot 4.x; diagnosed via Maven Central's `maven-metadata.xml` and
corrected to the real version (`4.1.0`). Installed and pinned a JDK 21 toolchain via
Homebrew since none of the four JDKs already on the machine matched the project's
requirement.

### Implementation
`backend/pom.xml`, `backend/src/main/java/com/hfxconnect/HfxConnectApplication.java`,
`backend/src/test/java/com/hfxconnect/HfxConnectApplicationTests.java`.

### Tests
One `@SpringBootTest` application-context test (`./mvnw test`); full build lifecycle
verified with `./mvnw verify`; manually confirmed the server actually boots and
listens on port 8080 via a live `curl` check.

### Evidence
Commit `chore: initialize Spring Boot backend on Java 21` on branch
`milestone/02a-application-initialization`.

### Potential Resume Wording
Bootstrapped a Java 21 / Spring Boot 4 backend service with Maven Wrapper tooling and
automated application-context testing, diagnosing and resolving a Maven Central
dependency-resolution issue in the process.

### Measurements Still Needed
None applicable at this stage — no performance-sensitive behavior exists yet.

## Frontend Application Shell

### Product Purpose
Establishes an accessible, responsive Next.js application shell — the foundation
every later page (search, resource detail, dashboards) will be built inside.

### Technologies Used
Next.js 16 (App Router, Turbopack), TypeScript (strict + `noUncheckedIndexedAccess`),
Tailwind CSS v4, ESLint, Jest, React Testing Library.

### Engineering Complexity
Diagnosed and fixed a moderate-severity supply-chain vulnerability (`postcss` XSS
advisory) bundled transitively inside Next.js's own dependency tree, without
downgrading Next.js seven major versions as npm's automated `--force` fix suggested —
used a scoped `overrides` entry instead and verified `npm audit` reports zero
vulnerabilities. Built the shell to meet concrete accessibility requirements (skip
link, semantic landmarks, visible focus states, single heading) rather than relying on
defaults.

### Implementation
`frontend/src/app/layout.tsx`, `frontend/src/app/page.tsx`,
`frontend/src/components/site-header.tsx`, `frontend/src/components/site-footer.tsx`.

### Tests
Four Jest + React Testing Library tests covering the homepage heading/link and both
shared layout components (`npm test`); full quality gate (`lint`, `typecheck`, `test`,
`build`) verified passing; manually confirmed the dev server serves the expected
content via a live `curl` check.

### Evidence
Commit `chore: initialize Next.js frontend with strict TypeScript and Tailwind` on
branch `milestone/02a-application-initialization`.

### Potential Resume Wording
Bootstrapped a Next.js 16 / TypeScript frontend with strict type checking, an
accessible responsive application shell, and an automated test suite; identified and
remediated a transitive supply-chain security advisory without a breaking downgrade.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: Lighthouse performance/accessibility scores once real
pages exist to measure.

## Database Environment and Migration Foundation

### Product Purpose
Establishes a reproducible local database environment and a schema-change process
(Flyway) that every future feature's data model depends on — without it, schema
changes would risk drifting between developers' machines and production.

### Technologies Used
Docker Compose, PostgreSQL 17 with the PostGIS 3.5 extension, Flyway, Spring Boot
Actuator, Testcontainers, Spring Boot `@ServiceConnection`.

### Engineering Complexity
Diagnosed, from classpath evidence rather than assumption, that Spring Boot 4.1
removed built-in Flyway auto-configuration entirely (searched every class in every
downloaded Spring Boot 4.1.0 artifact to confirm `FlywayAutoConfiguration` does not
exist), and implemented an explicit, documented replacement
([ADR-004](../decisions/ADR-004-manual-flyway-configuration.md)) rather than routing
around the problem. Also diagnosed and fixed: the official PostGIS image publishing no
`linux/arm64` build (pinned and smoke-tested `platform: linux/amd64` before adopting
it); two further Spring Boot 4 module relocations affecting integration testing
(`TestRestTemplate`/`RestTemplateBuilder`); a Testcontainers 2.x API change
(`PostgreSQLContainer` relocated and made non-generic); a Colima-specific Docker
socket path mismatch breaking Testcontainers' Ryuk sidecar; and a real local port
conflict with a pre-existing native PostgreSQL installation, resolved via the
project's own configurable-port design without touching unrelated infrastructure.
Verified fail-fast startup behavior, migration idempotency, and data persistence
end-to-end against a real running database, not only via unit tests.

### Implementation
`docker-compose.yml`, `backend/src/main/resources/application.properties`,
`backend/src/main/java/com/hfxconnect/common/config/FlywayMigrationConfig.java`,
`backend/src/main/resources/db/migration/V1__enable_postgis_extension.sql`.

### Tests
Three Testcontainers-backed integration test classes (6 tests total) running against
the real `postgis/postgis:17-3.5` image via Spring Boot's `@ServiceConnection`:
application-context load, Flyway migration success, PostGIS extension enablement,
migration idempotency across repeated startups, and health-endpoint correctness
(`./mvnw test`, `./mvnw verify`). Additionally verified manually against the real
docker-compose database: fresh-database migration, idempotent restart, fail-fast
startup without a database, and data persistence across a container restart.

### Evidence
Commits on branch `milestone/02b-database-environment`; see
`docs/development-log/2026-07-13.md` for exact commands and observed output.

### Potential Resume Wording
Built a reproducible PostgreSQL/PostGIS local development environment with Docker
Compose and Flyway-managed schema migrations for a Spring Boot backend, diagnosing and
resolving multiple Spring Boot 4 / Testcontainers 2 breaking changes (including a
missing Flyway auto-configuration) through direct classpath investigation; verified
fail-fast startup, migration idempotency, and data persistence against a real running
database with automated Testcontainers integration tests.

### Measurements Still Needed
None applicable at this stage — no performance-sensitive query behavior exists yet
(geospatial query performance will be measured starting in Milestone 7).

## Category Domain — First Full Backend Vertical Slice

### Product Purpose
Lets administrators create and browse the categories (Food Assistance, Study Spaces,
...) that will classify community resources — the first real, usable feature in the
product, and the layering pattern every later domain follows.

### Technologies Used
Spring Data JPA, Jakarta Bean Validation, Flyway, Spring MVC, springdoc-openapi,
Testcontainers, Mockito.

### Engineering Complexity
Designed and implemented a full layered domain slice from schema to HTTP API:
deterministic slug generation (Unicode normalization, diacritic stripping, punctuation
handling) with 9 dedicated edge-case tests; a two-constraint uniqueness design
(case/whitespace-insensitive name uniqueness independent from slug uniqueness, since
two different names can generate the same slug); centralized exception handling
reused by every future domain; and race-condition-safe duplicate handling (application
pre-check for fast, clear errors, database constraint as the authoritative guard,
with the constraint-violation path specifically unit-tested via a mocked repository
rather than attempted as a flaky real-concurrency test). Diagnosed and fixed a genuine
Spring Boot 4.1 JPA/Flyway bean-ordering bug (Hibernate schema validation running
before Flyway had migrated) by locating and applying the same internal mechanism
Spring Boot's own now-removed Flyway auto-configuration used to use. Also caught and
fixed a previously-passing test (from Milestone 2B) that had become silently stale
once a second migration existed, replacing its hardcoded assertion with one that
verifies the same invariant without needing future updates.

### Implementation
`backend/src/main/java/com/hfxconnect/category/` (entity, repository, service,
controller, DTOs, slug generator, exceptions),
`backend/src/main/java/com/hfxconnect/common/error/` (shared error-handling
pattern), `backend/src/main/resources/db/migration/V2__create_categories_table.sql`.

### Tests
53 tests total (up from 6 at the end of Milestone 2B) across 7 classes: 9 pure-function
slug-generation tests, 14 mocked-repository service tests, 10 real-database
repository/constraint tests, and 14 full-HTTP-layer API integration tests — all
against the real `postgis/postgis:17-3.5` image via Testcontainers, not H2 or mocks
for the database-dependent tests. `./mvnw test` and `./mvnw verify` both pass.
Additionally verified manually against the real local docker-compose database:
creation, retrieval, listing, pagination, duplicate rejection, validation errors,
malformed JSON, 404s, the generated OpenAPI document, and continued health-endpoint
correctness.

### Evidence
Commits on branch `milestone/03a-category-domain`; see
`docs/development-log/2026-07-15.md` for exact commands and observed output.

### Potential Resume Wording
Designed and implemented a layered Spring Boot REST domain (entity, repository,
service, controller, DTOs) with deterministic slug generation, database-enforced
uniqueness, centralized error handling, and OpenAPI documentation; wrote 53 automated
tests spanning unit, database-integration, and full-HTTP-layer coverage against a real
PostgreSQL/PostGIS instance via Testcontainers; diagnosed and fixed a Spring Boot 4.1
JPA/Flyway startup-ordering defect through direct classpath investigation.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: real API response-time data once deployed (Milestone 12).
