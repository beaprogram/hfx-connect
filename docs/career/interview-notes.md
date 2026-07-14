# Interview Notes

This document collects the "why" behind HFX Connect's real technical decisions, in a
form that can be reviewed before a technical interview. Entries are added only once
the corresponding decision is actually implemented — this file describes what was
built, not what is planned.

**Status: early.** Milestone 1 established the product definition and the
architecture direction (see [system-overview.md](../architecture/system-overview.md)
and the ADRs in [docs/decisions/](../decisions/)). Milestone 2A added the first real
code (application shells only). Milestone 2B connected the backend to a real,
migrated PostgreSQL/PostGIS database. The talking points below are the ones already
answerable from what has actually been built; the rest will be added as the
corresponding milestone is completed.

## Answerable Now (Milestone 1)

**Why PostgreSQL with PostGIS instead of computing distances in application code?**
See [ADR-002](../decisions/ADR-002-postgresql-and-postgis.md). Short answer: PostGIS's
`ST_DWithin`/`ST_Distance` functions can use a spatial (GiST) index, so a nearby-search
query stays fast as the number of resources grows, instead of scanning and computing
distance for every active row on every request.

**Why REST instead of GraphQL?**
See [ADR-003](../decisions/ADR-003-rest-api.md). Short answer: the frontend's data
needs are a small, well-known set of screens rather than many independent clients with
divergent query shapes — the case GraphQL is built for — so REST plus TanStack Query
caching covers the need with less operational overhead, and is more directly relevant
to the backend job market this project targets.

**Why a monorepo instead of separate frontend/backend repositories?**
See [ADR-001](../decisions/ADR-001-monorepo-structure.md). Short answer: one developer,
one release cadence, and most features touch both the API and the UI together, so a
single repository keeps related changes reviewable as one unit.

## Answerable Now (Milestone 2A)

**Why does the backend depend on `spring-boot-starter-webmvc` instead of the more
commonly documented `spring-boot-starter-web`?**
Spring Boot 4 (built on Spring Framework 7) split the former `web` starter more
explicitly along MVC vs. reactive lines; `spring-boot-starter-webmvc` is the current
equivalent for a servlet-based REST API, which is what HFX Connect needs. This was
confirmed by actually generating the project against `start.spring.io` rather than
assuming prior-version naming.

**Why is there a `postcss` entry in the frontend's `package.json` `overrides` field?**
`npm audit` flagged a moderate-severity XSS advisory in `postcss`, bundled
transitively inside `next@16.2.10`'s own dependency tree — not a package the project
depends on directly. npm's suggested automated fix (`npm audit fix --force`) would
have downgraded Next.js from 16.2.10 to 9.3.3, a seven-major-version regression that
would have broken the application. Pinning `postcss` to a patched version via
`overrides` fixes the actual vulnerability without touching the Next.js version;
`npm audit` now reports zero vulnerabilities.

**Why weren't `auth/`, `resource/`, and the other domain packages created yet?**
Git does not track empty directories, and creating them ahead of any real code inside
them would be speculative scaffolding with no enforcement value. They are added
starting in Milestone 3, alongside the entities, services, and controllers that
actually belong in them.

## Answerable Now (Milestone 2B)

**Why does the backend configure Flyway with a hand-written `@Configuration` class
instead of just adding `flyway-core` and letting Spring Boot handle it?**
See [ADR-004](../decisions/ADR-004-manual-flyway-configuration.md). Short answer:
Spring Boot 4.1 does not ship `FlywayAutoConfiguration` at all — confirmed by
inspecting every class in every Spring Boot 4.1.0 artifact, not assumed. Without the
explicit configuration, migrations silently never ran and no error was raised, which
is a genuinely dangerous failure mode to leave undiagnosed.

**Why is the database container pinned to `platform: linux/amd64` when developing on
Apple Silicon?**
The official `postgis/postgis` image publishes no `linux/arm64` build — verified with
`docker manifest inspect` before depending on it. Rather than switching to an
unofficial multi-arch mirror image, the platform is pinned explicitly and the image is
run under emulation, which was smoke-tested directly (boot, health check, a real
`CREATE EXTENSION postgis` and version check) before being adopted. This keeps the
project on the canonical, officially published image; production deployment targets
run amd64 infrastructure anyway, so this is purely a local-development trade-off.

**Why does the backend fail to start at all if the database is unreachable, instead of
starting in a degraded mode?**
This is deliberate fail-fast behavior: Flyway runs inside a `@Bean` factory method
during application context refresh, so a database that can't be reached (or a failed
migration) fails startup immediately with a clear error, rather than letting the
application come up in a state where later requests would fail confusingly. Verified
by manually stopping the database and confirming startup fails with a connection-refused
error.

**How is it verified that Flyway migrations are actually idempotent, not just
"probably fine"?**
Two ways: an integration test (`FlywayMigrationIntegrationTest`) starts a second Spring
context against a database a prior context in the same test run already migrated (via
a shared Testcontainers "singleton container"), and asserts the migration-history
table still has exactly one row. Separately, the same thing was verified manually
against the real `docker-compose` database by starting the application twice in a row
and confirming the second run logs "up to date, no migration necessary."

**How does the health endpoint avoid leaking sensitive information?**
`management.endpoints.web.exposure.include=health` exposes only the health endpoint
(no other Actuator endpoints), and
`management.endpoint.health.show-details=when-authorized` means unauthenticated
requests see only `{"status":"UP"}` plus health-check group names — no
component/connection-level detail (which would otherwise reveal datasource internals)
is shown until real authentication exists (Milestone 5). Verified with an integration
test that asserts the response body never contains credential, JDBC URL, connection
pool, or stack trace strings.

## To Be Added in Later Milestones

- How DTOs protect the API boundary from the JPA entity model (Milestone 3).
- How database constraints enforce rules that also exist in application validation
  (Milestone 3).
- How authentication tokens and refresh cookies work (Milestone 5).
- How authorization is enforced server-side, independent of the frontend (Milestone 5).
- How moderation approval and audit-history writes are made transactional (Milestone
  9).
- How duplicate submissions/bookmarks are prevented at the database level (Milestones
  3, 8).
- How geographic search is kept fast as content grows (Milestone 7).
- How map performance is protected through bounds-based queries and compact marker
  payloads (Milestone 7).
- How the product stays fully usable when location permission is denied or the map
  cannot be used (Milestone 7).
- Trade-offs made and limitations knowingly deferred, updated at each milestone.
