# Interview Notes

This document collects the "why" behind HFX Connect's real technical decisions, in a
form that can be reviewed before a technical interview. Entries are added only once
the corresponding decision is actually implemented — this file describes what was
built, not what is planned.

**Status: early.** Milestone 1 established the product definition and the
architecture direction (see [system-overview.md](../architecture/system-overview.md)
and the ADRs in [docs/decisions/](../decisions/)). Milestone 2A added the first real
code (application shells only). Milestone 2B connected the backend to a real,
migrated PostgreSQL/PostGIS database. Milestone 3A delivered the first complete
feature (category management). Milestone 3B built the resource domain's persistence
and business layer, deliberately with no public API yet. The talking points below are
the ones already answerable from what has actually been built; the rest will be added
as the corresponding milestone is completed.

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
table's row count matches its distinct-version count (proving no migration was ever
recorded twice, without hardcoding a total that would need updating every time a new
migration is added). Separately, the same thing was verified manually against the
real `docker-compose` database by starting the application twice in a row and
confirming the second run logs "up to date, no migration necessary."

**How does the health endpoint avoid leaking sensitive information?**
`management.endpoints.web.exposure.include=health` exposes only the health endpoint
(no other Actuator endpoints), and
`management.endpoint.health.show-details=when-authorized` means unauthenticated
requests see only `{"status":"UP"}` plus health-check group names — no
component/connection-level detail (which would otherwise reveal datasource internals)
is shown until real authentication exists (Milestone 5). Verified with an integration
test that asserts the response body never contains credential, JDBC URL, connection
pool, or stack trace strings.

## Answerable Now (Milestone 3A)

**Why does `Category` have no setters?**
There's no update or delete endpoint yet — nothing in this milestone's scope
justifies one. The entity is built once via its constructor and only read afterward.
When a real update requirement exists for some entity, that entity gains the specific
setters it actually needs at that point, not generic ones added ahead of time
"just in case."

**Why are there two separate unique constraints (`normalized_name` and `slug`)
instead of one?**
Because they can diverge: two different display names can generate the identical
slug even though their normalized names differ. `"Food Assistance"` and
`"Food, Assistance!"` both slugify to `food-assistance` (punctuation collapses into
the same hyphen run), but their normalized names — `"food assistance"` vs.
`"food, assistance!"` — are different strings. A single shared constraint would miss
that collision. See [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md).
This is directly tested (`CategorySlugGeneratorTest.differentNamesCanProduceTheSameSlug`
and `CategoryServiceTest.createRejectsDuplicateSlugEvenWhenNameDiffers`) and was
confirmed manually against the running API.

**How is race-safe duplicate handling actually verified, given a real concurrent-request
race is hard to reproduce in a test?**
Two layers, tested two different ways. The application-level pre-check
(`existsByNormalizedName`/`existsBySlug`) is exercised naturally by ordinary
sequential integration tests. The database-constraint-violation path — what actually
fires if two requests both pass the pre-check before either commits — is tested with
a mocked repository that's stubbed to throw `DataIntegrityViolationException` from
`save()`, verifying the service's translation logic directly
(`CategoryServiceTest.createTranslatesADatabaseRaceConditionIntoAConflict`) rather
than trying to engineer genuine concurrency in an integration test, which would be
slower and flakier for no extra confidence in this specific code path.

**Why does Hibernate schema validation need an explicit bean-ordering fix?**
Because Spring Boot 4.1's missing Flyway auto-configuration (ADR-004) has a
second-order consequence once JPA is introduced: nothing in the bean graph forces the
hand-written `flyway` bean to run before JPA's `entityManagerFactory` bean, so
Hibernate's schema validation could run against a database Flyway hadn't migrated
yet. Fixed by registering `EntityManagerFactoryDependsOnPostProcessor("flyway")` —
found at its actual Spring Boot 4.1 location by inspecting jar contents, since it had
also moved packages — which is the same mechanism the now-removed
`FlywayAutoConfiguration` used internally for exactly this problem.

## Answerable Now (Milestone 3B)

**Why does `resources.category_id` use `BIGINT` instead of the `UUID` a planning
document for this milestone suggested?**
Because `categories.id` is `BIGINT` (decided and merged in Milestone 3A —
see [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md)), and a
foreign key must match the type of the column it references. Following the suggestion
literally would have been a type error against an already-applied, unmodifiable
migration. Corrected during implementation, with the reasoning documented in the
migration file itself so a future reader doesn't have to guess why it deviates from
the original brief.

**Why does `ResourceService.create()` use `saveAndFlush()` where `CategoryService.create()`
uses plain `save()`?**
`Category.id` uses `GenerationType.IDENTITY`, which forces Hibernate to execute the
`INSERT` immediately inside `save()` (it has no other way to learn the
database-assigned id) — that's what makes `CategoryService`'s synchronous
`DataIntegrityViolationException` catch reliable. `CommunityResource.id` is a
Hibernate-generated `UUID`, assigned in memory before persistence, so Hibernate has no
such forcing requirement and could defer the actual `INSERT` to a later flush —
possibly after the `try`/`catch` around a plain `save()` had already exited, making
the race-condition catch unreliable. `saveAndFlush()` forces the flush to happen
inside the `try` block, where it belongs.

**Why is `ResourceServiceIntegrationTest` written against a real database instead of
mocking `CategoryRepository`, the way `CategoryServiceTest` mocks its own
repository?**
`ResourceService`'s most important rules are cross-entity: does the referenced
category exist, and is it active? A mock can only return what it's told to return —
it can't meaningfully prove that a real foreign-key relationship and a real "is this
category active" check behave correctly together. Testing this against a real,
Testcontainers-provisioned PostgreSQL instance (the same pinned image used in local
development) proves the actual behavior, not just that the code calls the mock the
way the test expects.

**Why does website URL validation use an allowlist (`http`/`https` only) instead of
blocking specific dangerous schemes like `javascript:`?**
An allowlist is a strictly stronger guarantee: it rejects everything not explicitly
permitted, including schemes nobody thought to blocklist. A blocklist only ever
catches the specific patterns someone remembered to write down.

## To Be Added in Later Milestones

- How authentication tokens and refresh cookies work (Milestone 5).
- How authorization is enforced server-side, independent of the frontend (Milestone 5).
- How moderation approval and audit-history writes are made transactional (Milestone
  9).
- How geographic search is kept fast as content grows (Milestone 7).
- How map performance is protected through bounds-based queries and compact marker
  payloads (Milestone 7).
- How the product stays fully usable when location permission is denied or the map
  cannot be used (Milestone 7).
- Trade-offs made and limitations knowingly deferred, updated at each milestone.
