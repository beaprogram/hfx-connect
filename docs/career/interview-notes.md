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
Milestone 5A added user registration — persistence, password hashing, validation.
Milestone 5B added login, JWT access tokens, rotating/reuse-detected refresh
sessions, and logout — deliberately without roles or request-level authorization,
which is 5C. The talking points below are the ones already answerable from what
has actually been built; the rest will be added as the corresponding milestone is
completed.

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

## Answerable Now (Milestone 5B)

**Why a JWT access token plus a separate opaque refresh token, instead of one
token doing both jobs?** Because they're exposed to different threats and need
different transports. The access token is meant to be sent as `Authorization:
Bearer` on future authenticated requests (Milestone 5C), so it's never placed in
a cookie — that keeps it out of automatic browser transmission, which is what a
cookie-based credential would otherwise be vulnerable to (CSRF). The refresh
token, conversely, is never returned in JSON and is only ever an `HttpOnly`
cookie, specifically so it can never be read by JavaScript — including a
malicious script from an XSS payload. Using one token for both roles would mean
picking the worse transport for at least one of the two threats. See
[ADR-008](../decisions/ADR-008-authentication-session-architecture.md).

**Why BCrypt for passwords but SHA-256 for refresh tokens — isn't that
inconsistent?** No — they defend against different things. BCrypt's deliberate
slowness and salting exist to make offline brute-forcing a *low-entropy,
human-chosen* secret expensive; a refresh token is 256 bits of uniformly random
data a human never chose, so there's nothing to "guess" faster from a stolen
hash — the only realistic attack is stealing the raw token itself, which hashing
protects the database against (a leaked table reveals no usable tokens) without
needing BCrypt's deliberate CPU cost on every single refresh request.

**How does refresh-token rotation actually stop a stolen token from being
useful?** Every successful refresh immediately revokes the token that was just
presented and issues a brand-new one — so a stolen copy of an already-rotated
token is simply dead on arrival. The interesting case is a *race*: if an
attacker uses the stolen token before its legitimate owner does, the legitimate
owner's own next refresh attempt is what gets detected — presenting a token that
turns out to already be revoked — and at that point every session descended
from the same original login is revoked, not just the one that failed,
forcing a fresh login everywhere. This was verified against the real database
during development, not just asserted: a live `curl`+`psql` reproduction showed
the exact sequence of rows changing state in real time.

**What actually went wrong with `noRollbackFor`, and how was it found?**
`RefreshSessionService.rotate()` needs to revoke a session (or an entire
rotation family) and *then* throw an exception signaling failure — a write
that must survive even though the method is reporting an error. The
straightforward fix, `@Transactional(noRollbackFor = SpecificException.class)`,
is the standard, documented Spring mechanism for exactly this. It didn't work
in this project's stack — confirmed only by running the real application
against the real database and watching `refresh_sessions` with `psql` between
live requests, since a mocked unit test cannot exercise genuine Spring
transaction demarcation at all and would have shown the code "passing" while
the actual persisted behavior was wrong. The fix uses explicit, programmatic
transaction control instead (`TransactionTemplate` with
`PROPAGATION_REQUIRES_NEW`), which commits independently of whatever happens to
the surrounding transaction afterward — no annotation-behavior assumption
required. This is the kind of defect that specifically requires full-stack
manual verification to catch, not just unit or even mocked-integration tests.

**Why is the refresh cookie's `Secure`/`SameSite` policy different between
local development and production, instead of one fixed value?** Because the
*correct* value genuinely differs. Locally, the frontend and backend are
different origins but the same *site* (`localhost` regardless of port), where
`SameSite=Lax` cookies still flow on cross-origin requests, and plain HTTP is
normal, so `Secure=false` is required (browsers drop `Secure` cookies over
HTTP entirely). In this project's real production topology — a Vercel frontend
and a Render backend, genuinely different registrable domains — that's
cross-*site*, where `SameSite=Lax` cookies are not sent on cross-site
`fetch`/XHR at all, only top-level navigation. Production needs
`SameSite=None` with `Secure=true` instead, which is only safe paired with the
project's explicit CORS origin allowlist (never a wildcard). Hardcoding either
value would silently break one environment or the other.

## Answerable Now (Milestone 5C)

**How is authorization enforced server-side, independent of anything the
frontend does?** `SecurityConfig`'s `SecurityFilterChain` is the sole
authority — every route's requirement (public, any authenticated account, or
a specific role) is a request matcher evaluated on the backend before a
controller method ever runs. The frontend's `/dashboard` guard is a
client-side convenience only (it prevents a flash of protected UI before a
redirect a signed-out user was always going to hit anyway); it has no
mechanism to grant or withhold anything the backend didn't already decide.
Directly requesting any protected route's HTML or calling its API bypasses
nothing, because the backend never trusted the frontend's routing in the
first place.

**Why re-load the account and its role from the database on every
authenticated request, instead of just trusting the JWT's `role` claim it
already carries?** Because the claim is a snapshot from the moment the token
was issued, and it can go stale: if an administrator demotes or suspends an
account a minute after that account's token was issued, the token itself is
still cryptographically valid for up to 15 more minutes. Trusting the claim
would mean that demotion or suspension has no real effect until the token
naturally expires. Re-loading the account by the token's subject and using
its *current* row for the authorization decision closes that gap at the
cost of one extra indexed primary-key lookup per request — verified live,
not just reasoned about: a token issued while an account was a plain `USER`
was later used, completely unchanged, to successfully perform an
`ADMIN`-only action, immediately after that same account was promoted
directly in the database.

**Why request-matcher-based authorization (`SecurityConfig`) instead of
`@PreAuthorize` annotations on the controller methods?** Both are valid
Spring Security patterns; the choice follows from what this milestone's
actual policy needs. Every rule here reduces to "this HTTP method and path
requires this role," with no per-object or ownership logic (nothing needs
"this resource belongs to the caller") — a request matcher expresses that
directly, in one place, with no risk of a second `@PreAuthorize`
configuration surface drifting out of sync with it. If a future feature
needs object-level authorization (e.g., an organization editing only its own
listings), that's the specific trigger to introduce `@PreAuthorize` for that
narrower need, not a reason to add it preemptively now.

**How does the frontend keep the access token away from XSS, while still
surviving a page reload?** The token lives only in a React context's
in-memory state — never `localStorage`, `sessionStorage`, or a
JavaScript-writable cookie — so there's no persistent, script-readable
location for an injected script to steal it from. The trade-off is that a
full page reload always discards it; the frontend recovers by calling the
refresh endpoint once on load, which exchanges the `HttpOnly` refresh
cookie (never itself readable by JavaScript) for a fresh access token.
Concurrent refresh attempts are coalesced into a single in-flight request,
because this project's refresh tokens rotate on every use — two independent
refresh calls racing each other could each present the same soon-to-be-
rotated token and trigger a false-positive security lockout of the user's
own, entirely legitimate session.

## Answerable Now (Milestone 6A)

**How do you build a keyword search over a SQL database without opening a
SQL-injection hole?** Never concatenate the search text into the query
string. The query text itself (`LOWER(r.name) LIKE :likePattern ESCAPE '\'`)
is a fixed literal, written once at compile time; only the *value* bound to
`:likePattern` varies per request, and that value is built entirely in Java
before it's ever near a query — lowercased, wrapped in `%...%`, with `%`
and `_` themselves escaped so a literal percent sign or underscore in
someone's search doesn't get treated as a wildcard. The `ESCAPE '\'` clause
that makes that escaping actually work is also a fixed literal in the query
text, never something derived from user input.

**Why bother escaping `%` and `_` specifically — what's the actual risk?**
Not injection (parameter binding already prevents that) — it's a
correctness and information-leak risk. `LIKE`'s own metacharacters mean a
search for `50%` or `user_name` would silently behave like a wildcard
search for "50 followed by anything" or "user, any single character,
name" if left unescaped — returning unrelated matches the searcher never
asked for, and in principle letting a search phrase probe for data in ways
that were never intended. I didn't just trust the escaping logic's design
either: I built a resource specifically crafted to produce a false-positive
match if `_` were ever treated as a real wildcard, and confirmed live
against the real database that the escaped query correctly rejected it.

**Why did adding search replace two existing methods with one, instead of
adding two new ones?** Before this feature, the resource listing had two
methods — one for "all active resources," one for "active resources in
this category" — because category was the only optional filter. Keyword
search is a *second* independent optional filter; naively adding it as its
own pair would mean four method combinations, and a third future filter
(cost type, say) would make eight. One method with two independently
optional predicates (`(:categoryId IS NULL OR ...) AND (:pattern IS NULL OR
...)`) covers every combination without multiplying, and this project
already had a documented precedent for "generalize once a second real need
arrives, not before" from an earlier milestone's utility-extraction
history — the same reasoning, just applied to a query shape this time.

**Why not add a database index for the search right away?** Because a
`LIKE '%term%'` pattern (a leading wildcard) can't use a normal B-tree index
regardless — it would need PostgreSQL's `pg_trgm` extension and a GIN
index specifically. Adding that now, for a project whose actual dataset is
an MVP directory of dozens to low hundreds of resources, would be paying a
real cost (a new extension dependency, index-maintenance overhead on every
write) to solve a performance problem that doesn't exist yet, on a guess.
The decision — and the exact trigger for revisiting it (real evidence of
slow queries at real scale) — is written down, not left as something only
discoverable by reading the code.

## Answerable Now (Milestone 6B)

**How do you calculate "is this place open right now" correctly, including
overnight hours?** Store each day's schedule as plain local times (no
timezone component — they mean "this is what the sign on the door says").
An entry is "overnight" purely by comparing its own two times: if the
closing time is earlier in the day than the opening time, the interval
crosses midnight. To check "open now," I evaluate two conditions: does
*today's* entry cover the current time (handling both the same-day case
and, for an overnight entry, "after opening, before midnight"), or does
*yesterday's* entry, if it was overnight, still cover the current time
because it hasn't reached its closing time yet? That second condition is
the one people forget — a resource open until 2am is still open at 1am
even though "today," by the calendar, technically started at midnight.

**Why inject a `Clock` instead of just calling `Instant.now()`?** Because
"is it open right now" is exactly the kind of logic that's nearly
impossible to unit-test properly against the real clock — you'd either
need to run tests at specific times of day, or only test the "obviously
open" and "obviously closed" cases and hope the boundaries are right. With
a `java.time.Clock` injected as a constructor dependency, tests supply
`Clock.fixed(...)` at an exact chosen instant, so I can assert "exactly at
opening time is open, one second before is closed" and "this exact instant
during a daylight-saving transition converts to the correct Halifax local
time" — deterministically, every run, not just whenever the test happens
to execute.

**How did you make sure the SQL filter and the display calculation agree
with each other?** They're necessarily two separate expressions — one a
correlated `EXISTS` subquery for the `openNow=true` filter, one a Java
method for what a resource's detail page shows — and I was honest with
myself that duplicated logic is exactly where the two commonly drift out
of sync. So both are tested against identical seeded data at the service
layer: a resource whose schedule makes `OpenNowCalculator` say "open" is
also asserted to actually appear when filtering `openNow=true`, for the
same clock, same schedule, same request. If they ever disagreed, that
assertion — not a bug report from a confused user — would be the first
thing to fail.

**Why does an empty schedule mean "unknown," but a schedule that just
doesn't cover today mean "closed"?** Because those are genuinely different
facts. A resource with zero schedule rows has never told anyone its hours
at all — claiming "closed" would be a guess. A resource with, say, only a
Monday-Friday schedule and no Saturday entry has told me enough to know
it's not scheduled to be open on Saturday — that's a real, if incomplete,
answer, not a guess. I drew the line at "does this resource have *any*
schedule data," not "does it have data for today specifically," because a
per-day unknown would make the `openNow=true` filter ambiguous — would an
unknown-for-today resource count as a candidate or not? With one dividing
line, the filter has exactly one meaning: "currently calculated as open."

**You verified the time-serialization format against a live server before
writing frontend code for it — why does that matter?** Because the
milestone's own written brief gave an example (`"09:00"`) that turned out
not to match what the backend actually sends (`"09:00:00"`, because
Jackson's default time formatter always includes seconds). If I'd trusted
the brief's example instead of checking, I'd have shipped a frontend time
parser that worked in my head and failed the first time it touched real
data. Writing a backend test that asserts on the literal JSON string
before building anything downstream of it turned an assumption into a
verified fact — and left a permanent regression test behind, so if a
future dependency upgrade ever changed that serialization format, it would
fail loudly in CI instead of silently breaking every frontend consumer.

## Answerable Now (Milestone 7A)

**Why not use Hibernate Spatial to map the new location column onto your
entity?** I checked, rather than assumed. I fetched the exact POM for the
`hibernate-spatial` version matching this project's actual
`hibernate-core` version straight from Maven Central, and found it
depends on Geolatte-geom — not JTS, which is what most Hibernate Spatial
tutorials and Stack Overflow answers you'd find assume. Adopting it would
have meant either learning a second geometry library or bolting on an
unverified JTS integration with zero precedent in this project's own
dependency history. And since nearby search was always going to need a
native `ST_DWithin`/`ST_Distance` query anyway — those have no JPQL
equivalent — an entity-mapped geometry field would only ever have been
used for the simple "read this resource's own coordinate back" case,
which a native query handles just as well with a plain `Double`. So I
kept the column entirely outside the ORM's entity mapping and did every
geospatial operation through native SQL instead — a real trade-off I made
deliberately and wrote down, not a corner I cut because mapping it looked
hard.

**How do you make sure PostGIS's `ST_MakePoint(longitude, latitude)`
argument order — which is backwards from how people say it out loud —
never gets swapped?** Two things. First, I never pass a bare "pair" of
numbers anywhere — every DTO field is named explicitly `latitude`/
`longitude`, and every query binds them by name, not position. Second, I
tested with real, deliberately asymmetric coordinates — Halifax is around
44.6° latitude and -63.6° longitude, different enough in both magnitude
and sign that if I'd swapped them by accident, the test would have failed
by miles, not by a suspiciously-plausible small amount. A test that uses
`(1.0, 2.0)` as its coordinates would happily pass even with a swap bug;
mine wouldn't.

**Tell me about a bug you found in your own tests, not the implementation.**
My first version of a distance-ordering test asserted the *first* result
in an unscoped nearby-search query had a positive distance from the
origin. It passed running alone, but failed once the full suite ran
together — because a completely different test class, using real HTTP
calls that commit independently of my test's own transaction rollback,
had already saved a resource at the exact same coordinate I was using as
my search origin, landing at distance zero and displacing my own
"nearest" resource from position zero. The fix wasn't to loosen the
assertion — it was to scope the query with a keyword filter unique to
that one test, the same isolation pattern the rest of the test suite
already used everywhere else. I'd just missed it in this one test.

**How did you verify the spatial index actually helps, instead of just
assuming it does because it exists?** I ran `EXPLAIN` against the real
query PostgreSQL would actually execute, connected directly via `psql` —
not inferred from documentation. The output showed `Index Scan using
idx_resources_location_gist`, which is the concrete, verifiable proof
that PostgreSQL's query planner is choosing to use the index for the
`ST_DWithin` radius search, not falling back to a full table scan. I also
wrote down explicitly that this project's actual dataset is far too small
for a real latency benchmark to mean anything yet — I'd rather say "I
haven't measured that at scale" than imply a number I never actually
produced.

## Answerable Now (Milestone 7B)

**How did you decide where the map's session state — search centre,
radius, geolocation status, selection — should actually live?** This was
the hardest design decision in the milestone. Three constraints pulled in
different directions: the coordinate could never go in the URL or browser
storage (privacy); the state had to survive a filter change, which
already causes a full `/resources?...` navigation; and it also had to
survive visiting a resource's detail page and clicking back. A plain
`useState` in the page component would have been wiped out by either of
those navigations. The fix was to mount a React Context provider at
`app/resources/layout.tsx` instead of inside the page — in the Next.js
App Router, a layout persists across navigations to sibling routes it
wraps, while a page gets torn down and rebuilt. Once I understood that
distinction, the rest of the design fell into place: `view`/`radiusKm`
get a best-effort mirror to the URL for shareability, and everything
location-related stays in memory only.

**Walk me through a bug you found after your automated tests were
already green.** The URL-mirroring feature I just described had a bug my
test suite didn't catch, because I'd mocked `usePathname()` to a fixed
value in every test. In a real browser, clicking "View details" from Map
mode navigated to a resource's detail page, and the mirroring effect —
which re-ran on every pathname change by design, so it could keep
`view`/`radiusKm` in sync after a filter navigation — fired again on
*that* navigation too, and unconditionally rewrote the current URL,
leaving `?view=map&radiusKm=5` stuck onto a page where it means nothing.
I only found this because I drove a real headless-Chromium session
through the actual click, not because I re-read the code more carefully.
The fix was a one-line guard — only mirror while the pathname is exactly
`/resources` — and I added a test that mocks a *changing* pathname, not
just a fixed one, so this specific failure mode can't silently come back.

**How do you know the map isn't flooding your API with requests while
someone drags it around?** Leaflet's `moveend` event — not `move` or
`drag` — only fires once, after the gesture actually settles. My handler
for it does nothing but update a local "pending centre" value; no network
request happens until the user explicitly clicks "Search this area." I
have a test that simulates a map move and asserts the nearby-fetch mock's
call count didn't change afterward, so this isn't just something I
believe about the code — it's asserted.

**How did you verify the mapping library versions you picked actually
work with this project's React/Next.js versions, instead of just
following a tutorial?** I checked the real dependency tree first
(`react@19.2.4`, `next@16.2.11`), then fetched each candidate package's
own published `peerDependencies` straight from the npm registry — not a
blog post's `package.json` — before installing anything.
`react-leaflet@5.0.0` and `react-leaflet-cluster@4.1.3` both declared
exact matches for React 19. After installing, I ran `npm ls` and
confirmed a single deduped Leaflet instance across the whole tree, which
rules out a common failure mode where a clustering plugin quietly pulls
in its own separate copy.

## Answerable Now (Milestone 8A)

**How does saving a resource stay correct if two requests race each
other — say, a double-click?** The application checks first ("does this
already exist?"), but the thing that actually guarantees correctness
under a real race is the database's own `UNIQUE (user_id, resource_id)`
constraint. If two near-simultaneous requests both pass that initial
check before either commits, one insert wins and the other fails with a
constraint violation — which I catch and treat as success, because the
caller's desired end state ("this resource is saved") is already true
either way. I didn't just reason about this: I wrote an integration test
that fires two genuinely concurrent save requests for the same user and
resource, and asserts exactly one row exists afterward.

**Saved resources are private per-account data. How do you make sure one
user's browser session never shows another user's saved state?** The
backend side is straightforward — every query is scoped by the
authenticated user's id, so there's no cross-account read path at all.
The harder problem was the frontend's TanStack Query cache, which is
shared, in-memory browser state that persists across a logout or a
same-tab account switch unless something explicitly clears it. I put
that responsibility in the one place that already knows exactly when
those two events happen — the existing `AuthProvider` — rather than
trusting every future component that touches saved-resource data to
remember to invalidate its own cache. I verified this live, not just
with mocked tests: logged in as one real account, saved a resource,
logged out, logged in as a second real account in the same browser tab,
and confirmed the second account's dashboard showed its own (empty)
saved state, not the first account's.

**Walk me through a bug you found after your automated tests were
already green, again.** During this milestone's manual verification, the
very first real save click in the browser failed — not with a visible
error message, but with a raw CORS preflight failure in the console:
"No 'Access-Control-Allow-Origin' header." The `GET` requests on the
same page had worked fine moments earlier. That asymmetry was the clue:
I checked `WebCorsConfig` and found `allowedMethods` only ever listed
`GET`/`POST` — it had been that way since the bean was first written,
several milestones earlier. Nothing had caught it before because the
only other `PUT` routes in the app (an admin operating-hours/location
endpoint) had only ever been exercised by backend integration tests,
which call the API directly and never go through a real browser's CORS
layer at all. My own new frontend unit tests couldn't have caught it
either, since they mock the API client rather than making a real
cross-origin browser request. I fixed the allowlist and added a
dedicated preflight regression test before considering the milestone
verified. The lesson I took from it: a mocked test suite proves your
application logic is correct; it doesn't prove a real browser can
actually reach that logic in the first place. Only a genuinely
browser-driven check can prove that.

**Why didn't you just clear the whole TanStack Query cache on logout
instead of writing something more targeted?** I considered it, but a
full `queryClient.clear()` would also throw away harmless public data —
the category list, already-fetched public resource pages — for no
security benefit, since none of that is private. I scoped the clearing
to exactly the `saved-resources`-prefixed query keys instead, so a
logout stays cheap for everything that doesn't need to be private.

## Answerable Now (Milestone 8B)

**You have two contribution tables now. Why not one generic "report"
table with a type column?** I considered it, but the two shapes are
genuinely different — a submission proposes an entire new resource
record with no target to reference; a report references an existing
resource and carries a small set of optional proposed fields plus a
required issue type. Forcing both through one table would mean every
row carries a large number of columns that are meaningless for the
other kind of contribution, and every query needs a discriminator
check. I'd rather have two legible tables than one table that needs a
comment explaining which half of its columns actually apply.

**Why does deleting a user versus deleting a resource have three
different foreign-key behaviors across these two tables?** Because
they mean three different things. Deleting the account that submitted
or reported something shouldn't silently destroy that history — a
submission or report has standalone value even after the account is
gone — so both owner columns use `ON DELETE RESTRICT`. But deleting the
*resource* a correction report is about is different again: the report
still has review value afterward — "this was reported as a duplicate,
then removed" is exactly the kind of thing worth keeping — so that
column uses `ON DELETE SET NULL`, and I capture the resource's name and
slug in a snapshot at creation time so the report stays meaningful even
after the live association is gone. Three columns, three real
policies, chosen from what each relationship actually means rather
than picking one default and applying it everywhere.

**Walk me through a bug that had nothing to do with your actual
feature logic.** While writing service-layer tests, I chained
"withdraw a submission" immediately followed by "resubmit the same
name and category" in one test method, and the resubmission failed
with an unexpected duplicate-pending conflict — even though the
withdrawal had already run first, in program order. It turned out
Hibernate's flush action queue processes entity insertions before
updates within a single flush, regardless of the order your code
actually made those changes in. So within one shared transaction, the
new row's insert could physically reach the database before the
withdrawal's update did, and the unique index still saw the old row as
pending. I fixed it by having the withdrawal call `saveAndFlush`
explicitly, forcing that specific update to commit before the method
returns, instead of trusting default auto-flush timing. The lesson I
took from it: if a later write's correctness depends on an earlier
write already being visible, don't assume ORM flush order matches your
code's call order — verify it with a test that actually chains both
operations.

**How did you catch the CORS-style "this only breaks in a real
browser" class of bug this time?** I didn't have to reproduce it fresh
— I already knew the shape of the risk from Milestone 8A's CORS gap, so
I made sure this milestone's own manual verification actually drove
both new protected routes through a real browser redirect, not just
asserted against the API directly. But I did find two *different*
real bugs the same way manual verification always seems to catch: two
pre-existing tests in other files had each implicitly assumed a
pristine shared test database, and once my own new tests added enough
rows to that same shared database, both assumptions quietly stopped
holding. Neither was caught by any single test in isolation — only by
running the full suite together, which is exactly why "all tests pass
in isolation" isn't the same guarantee as "the full suite is green."

## Answerable Now (Milestone 9A)

**Two moderators click approve on the same submission at nearly the
same instant. Walk me through what actually happens.** Both requests
try to acquire a `PESSIMISTIC_WRITE` lock (`SELECT ... FOR UPDATE`) on
the submission row as the very first thing their review transaction
does. Whichever gets there first holds the lock for its *entire*
transaction — revalidating fields, creating the resource, marking the
submission approved, writing the audit event — and the second request
blocks on that same `SELECT ... FOR UPDATE` the whole time. Once the
first transaction commits, the second one's lock finally succeeds, it
re-reads the row, sees the status is no longer `PENDING_REVIEW`, and
returns `409` immediately. I picked this over JPA's optimistic
`@Version` locking specifically because it makes the *whole* decision
atomic against a competitor, not just the final write — there's no
window where one request could get partway through side effects before
discovering it lost the race, and no separate exception-translation
code path for the optimistic-lock-failure case to get wrong. I proved
it with an actual test spinning up two real threads and a
`CountDownLatch` to force them to race, not just a unit test asserting
what exception type a mock would throw.

**Why lock the target resource too, not just the correction report?**
Because two *different* pending reports can target the same resource.
If moderator A approves a report changing the address while moderator
B concurrently approves a different report changing the phone number,
locking only each report's own row doesn't stop both transactions from
reading the resource's "current" state before either commits — B's
transaction would then write back a resource object that still has the
*old* address, silently reverting A's already-committed change. I only
caught this by explicitly reasoning through the failure mode — it's not
something a single-report-focused test would ever surface, since it
needs two *different* reports on the *same* resource to manifest.
Locking the resource row itself, independently of whichever report is
being processed, closes it.

**Tell me about a bug your own tests didn't catch.** I added a
`lastVerifiedAt` column and threaded it correctly through the entity,
the service layer, and the moderation-facing audit snapshots — all of
it compiled, and every test I'd written against those layers passed.
It just never occurred to me to check whether the *public* resource
API response — a completely different DTO that happens to expose
overlapping data — had been updated too. It hadn't. I only found it
because manual verification meant literally curling a real approved
resource and reading the JSON back, and `lastVerifiedAt` wasn't there.
The fix was trivial once I saw it; the lesson is the part I wrote down —
when a field gets added to an entity that already backs more than one
response shape, "my tests for the layer I touched are green" doesn't
mean every DTO claiming to expose that data actually got updated. I
added a regression test asserting the live response body specifically,
not just the entity/service objects, so this exact gap can't reopen
silently.

**Why does `ADMIN` get no exception to the self-review rule?** Because
it's a conflict-of-interest rule, not a permission gate — the concern
isn't "does this account have enough privilege," it's "should this
specific person be the one deciding on their own submission," and role
doesn't change the answer to that. It would have been easy to write the
check as "if not ADMIN, block self-review," and it would have passed
every test I'd have thought to write for the *submission* side of the
flow — I only avoided it because I wrote a dedicated test asserting an
`ADMIN` account gets blocked reviewing its own contribution before
implementing the check, not after.

## To Be Added in Later Milestones

- How geographic search is kept fast as content grows at production scale (deferred —
  Milestone 7A/7B's own datasets were too small to benchmark meaningfully).
- How saved-resource query/status-lookup performance holds up at realistic
  per-account saved-item counts (deferred — Milestone 8A's own manual-verification
  dataset was two resources and three accounts).
- How submission/correction-report volume and review-queue growth behave at
  production scale (deferred — Milestone 8B's own manual-verification dataset
  was two resources, three accounts, and a handful of contributions).
- How moderation queue depth and reviewer throughput behave at production scale,
  and whether reviewer assignment/queue prioritization end up justified (deferred —
  Milestone 9A explicitly excluded both from scope; its own manual-verification
  dataset was six accounts and a handful of contributions).
- Trade-offs made and limitations knowingly deferred, updated at each milestone.
