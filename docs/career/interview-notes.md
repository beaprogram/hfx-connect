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
and business layer, deliberately with no public API yet. Milestone 3C added that
public API. Milestone 4 built the first real public frontend over both APIs.
Milestone 5A added user registration — persistence, password hashing, validation —
deliberately without login, tokens, or roles, which are 5B and 5C. The talking
points below are the ones already answerable from what has actually been built; the
rest will be added as the corresponding milestone is completed.

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

## Answerable Now (Milestone 3C)

**How does the resource API avoid an N+1 query when every response embeds a category
summary (name and slug, not just an ID)?**
`CommunityResource.category` is a lazy `@ManyToOne`. Calling `.getId()` on an
uninitialized lazy proxy is free — Hibernate proxies know their own ID without a
query — but `.getName()`/`.getSlug()` would trigger a real query, once per resource,
turning a 20-item list page into 21 queries. `ResourceRepository` has explicit
`JOIN FETCH` query variants used specifically by the read paths that build a response
(`findByIdWithCategory`, `findByActiveWithCategory`, etc.), each with its own
`countQuery` since Spring Data can't reliably derive one for a fetch-joined `@Query`.
This is safe to combine with pagination because `category` is a to-one relationship —
a to-many fetch join combined with `Pageable` would silently paginate in memory
instead of in the database, a well-known Hibernate pitfall this design avoids by
construction, not by remembering not to do it.

**Why does a resource creation request that references a nonexistent category return
`404`, while one referencing an inactive category returns `400`?**
They're different failure modes on purpose: `CATEGORY_NOT_FOUND` (404) means the
category ID doesn't correspond to any row at all — closer to "this specific thing
doesn't exist," the traditional meaning of 404. `INACTIVE_CATEGORY` (400) means the
category is real, but the request isn't allowed to use it — closer to "this request is
invalid," a 400. Splitting what was one combined `CategoryUnavailableException`
(Milestone 3B) into two exceptions was safe to do exactly at this point, because this
milestone is what first makes either code observable over HTTP — nothing before it
depended on the old combined code.

**Why doesn't the public resource list support filtering by `active` or
`verificationStatus`, even though the Category API supports an `active` filter?**
Two different, deliberate reasons, not an oversight: every resource today is
`UNVERIFIED` (no mutator exists yet — that's Milestone 9's moderation workflow), so a
`verificationStatus` filter would have exactly one meaningful value and provide no
real utility. `active` is different — exposing it publicly would let anyone browse
deactivated resource listings, which may represent contact/location information that
was deliberately taken down, with no authentication boundary yet to restrict that to
staff. Categories don't carry the same sensitivity, so their existing `active` filter
was a reasonable choice at the time; resources warranted a different one.

**Why does this milestone's documentation include a section explaining that it isn't
the "Milestone 3B" its own initiating instructions called it?**
The instructions asked for work on branch `milestone/03b-resource-domain`, labeled
"Milestone 3B" — but that exact milestone (resource persistence, no HTTP) was already
completed and merged in a prior session, and more than a dozen already-committed
files independently referred to "Milestone 3C" as the public-API milestone. Silently
building a second, differently-scoped "Milestone 3B" would have left the project's
own documentation internally contradictory. Proceeding as Milestone 3C and explaining
why, prominently, in the milestone document itself (not buried in a commit message)
keeps the project's history honest and legible to whoever reads it next.

## Answerable Now (Milestone 4)

**Why does the resource list use TanStack Query at all, instead of just a plain
Server Component reading `searchParams`?**
A pure Server Component would genuinely be simpler, and that trade-off is written
down explicitly in `docs/architecture/frontend-architecture.md` rather than hidden.
TanStack Query was used anyway because it gives filter/sort/pagination state a
single, consistent representation (`isPending`/`isError`/`isSuccess`) that the
loading/empty/error-state requirements map onto directly, and because it's what makes
the "no infinite retry loops" and "stable query keys" requirements meaningful at all
— a plain Server Component has no concept of either. The page still does a real
server-side `prefetchQuery` + `<HydrationBoundary>` first, so the trade-off doesn't
cost the first paint anything — verified directly against the rendered HTML, not
assumed.

**Why does the Zod schema for a resource have no `accessibility` field, when the
task brief asked for an accessibility-information section on the detail page?**
The live backend's own OpenAPI document — checked directly, not assumed — has no such
field on `ResourceResponse`. Building a UI section for data the API cannot supply
would mean either inventing a value or silently defaulting one in, both of which
violate the same "no fabricated values" rule the brief itself states elsewhere.
Documented as a deliberate omission (`docs/wireframes/resource-detail.md`) rather than
silently skipped.

**How does the category/sort filter work with JavaScript disabled?**
It's a real `<form method="get" action="/resources">` with named `<select>`
elements and a visible "Apply" submit button — a browser with no JavaScript at all
still submits it as a normal GET navigation and every control works. With
JavaScript enabled, an `onChange` handler intercepts that same form and calls
`router.push` instead, for an instant client-side transition — but that's an
enhancement layered on top of a working baseline, not a replacement for it.

**What CORS decision did this milestone make, and why not just allow `*`?**
The frontend and backend are different origins even in local development
(`:3000` vs `:8080`), so the browser's own same-origin policy blocks the frontend's
client-side requests unless the backend explicitly allows it. A wildcard (`*`) was
rejected because the backend's `POST` endpoints are still unauthenticated — a
wildcard would let any website's JavaScript create categories/resources through a
visitor's browser, a strictly worse version of a limitation that's already documented
and accepted for same-origin requests. Instead, `WebCorsConfig` allows only an
explicit, environment-configured origin list, verified with both an automated test and
a real preflight request. See [ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md)
for the full reasoning, including why a Next.js proxy layer was considered and not
chosen.

**What happened with the disk-space issue mid-milestone, and how was it handled?**
The development machine's internal disk filled to near-capacity partway through the
session, which degraded filesystem performance badly enough that `npm test`,
`tsc --noEmit`, and even reading a small tracked file started hanging indefinitely —
confirmed to be a disk problem, not a code problem, by reproducing the same hang with
completely unrelated tools and by watching a stuck process's CPU usage stay near zero
for minutes (genuinely blocked, not computing). Rather than guessing at what might be
safe to delete on someone else's machine, the issue was reported plainly, and — with
explicit direction — the project was relocated to external storage, verified
byte-for-byte identical to the original before being treated as the new working
copy. This is the kind of engineering judgment call — recognizing an environment
problem is not the same class of problem as a code bug, and requires a different kind
of response (verification and explicit authorization, not unilateral action) — that's
easy to get wrong under time pressure.

## Answerable Now (Milestone 5A)

**Why add `spring-security-crypto` instead of `spring-boot-starter-security` just to
get `BCryptPasswordEncoder`?** The full starter auto-configures a default security
filter chain that secures every endpoint unless explicitly permitted — adding it now
would have auto-secured the still-public, still-unauthenticated Category/Resource
APIs a full milestone before Milestone 5B/5C actually builds the real authentication
those endpoints are waiting for. `spring-security-crypto` is the same project, but
just the hashing/crypto classes, with no filter chain and no auto-configuration —
verified directly by confirming the existing Category/Resource APIs still return
`200` in an integration test after adding the dependency, not just assumed. See
[ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md).

**Why is a newly-registered account `ACTIVE` instead of `PENDING_VERIFICATION`, given
`emailVerified` is always `false`?** Because no email-delivery mechanism exists in
this project yet. A `PENDING_VERIFICATION` account with no real way to leave that
state would be permanently unusable — worse than an account that's simply
unverified. `emailVerified` is deliberately its own column, independent of `status`,
specifically so a real verification flow can be added later by flipping one boolean
rather than needing a status migration or a fabricated verification endpoint (which
the milestone brief explicitly ruled out).

**How is privilege escalation through the registration request actually prevented —
is there a check somewhere that strips out a `role` field?** No — there's no field to
strip, because `RegistrationRequest` was never given one. There is no code path,
constructor, or setter anywhere in the `user` package that accepts a caller-supplied
role at all; `RegistrationService` always calls the two-argument `User` constructor
that hardcodes `Role.USER`. The DTO is also `@JsonIgnoreProperties(ignoreUnknown =
true)`, so a client that submits an unrecognized `role` field is ignored
deterministically rather than depending on whatever the project's default Jackson
configuration happens to do. Verified with both an automated test and a live `curl`
request submitting `"role":"ADMIN"`, confirming the created account is `USER`
regardless.

**Why does `User.id` use `UUID` instead of the `BIGINT` identity column `categories`
uses?** Same reasoning ADR-005 already established for `resources`: users are
numerous and self-registered over time by many independent actors, and a sequential
integer ID would let one account estimate the total user count or enumerate other
accounts by incrementing a value. This also meant reusing `ResourceService`'s
`saveAndFlush` (not `save`) pattern for the duplicate-email race condition, since a
Hibernate-generated UUID doesn't force a synchronous `INSERT` the way an `IDENTITY`
column does — and a repository test written with plain `save()` during this
milestone silently failed to catch the constraint violation until switched to
`saveAndFlush`, a direct, reproducible demonstration of why that distinction matters
in practice, not just in theory.

## To Be Added in Later Milestones

- How login, access tokens, and refresh cookies work (Milestone 5B).
- How authorization is enforced server-side, independent of the frontend
  (Milestone 5C).
- How moderation approval and audit-history writes are made transactional (Milestone
  9).
- How geographic search is kept fast as content grows (Milestone 7).
- How map performance is protected through bounds-based queries and compact marker
  payloads (Milestone 7).
- How the product stays fully usable when location permission is denied or the map
  cannot be used (Milestone 7).
- Trade-offs made and limitations knowingly deferred, updated at each milestone.
