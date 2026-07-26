# Resume Evidence

This document records, feature by feature, the concrete evidence behind every resume
claim about HFX Connect. It exists so that every resume bullet drafted from this
project is traceable to real, committed, tested work — not aspiration.

**Status: early.** Milestone 1 was documentation-only. Milestone 2A added the first
real, tested code (application shells for both the backend and frontend). Milestone 2B
connected the backend to a real, migrated PostgreSQL/PostGIS database. Milestone 3A
delivered the first real feature — category management — end to end. Milestone 3B
built the resource domain's persistence and business layer (no public API yet).
Milestone 4 shipped the first real public frontend. Milestone 5A added user
registration (persistence, password hashing, validation). Milestone 5B added
login, JWT access tokens, rotating/reuse-detected refresh sessions, and logout —
roles/authorization (5C) are not built yet. Feature-level entries (search,
moderation) will continue to be added as those milestones land.

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

## Resource Domain: Persistence and Business Layer

### Product Purpose
Establishes the backend workflow every future resource listing (food assistance, a
study space, ...) goes through — category-validated creation, deterministic and
stable slugging, normalized/validated fields, and active-only visibility once
deactivated — as a tested, correct foundation before any public API is built on it.

### Technologies Used
Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL check/foreign-key constraints,
Testcontainers.

### Engineering Complexity
Identified and corrected a real inconsistency between a planning document and the
already-merged database schema (a suggested `UUID` category foreign key was
impossible against the existing `BIGINT` category primary key) rather than following
it blindly or silently deviating without explanation. Diagnosed a subtle JPA identifier-
generation-strategy issue: a `save()` call that reliably catches race-condition
constraint violations for `IDENTITY`-keyed entities does not do so reliably for
Hibernate-generated `UUID` keys, and fixed it with a deliberate `saveAndFlush()`.
Diagnosed and fixed a genuine, general-purpose (not resource-specific) production bug
— unmapped routes returning `500` instead of `404` — discovered through the
milestone's own required regression testing. Implemented Canadian-specific validation
(province allowlist, Canada Post postal-code letter exclusions) and allowlist-based
(not blocklist-based) website-scheme validation, which is a strictly stronger
security guarantee against dangerous URL schemes.

### Implementation
`backend/src/main/java/com/hfxconnect/resource/` (entity, repository, service,
business-layer models, validation, exceptions),
`backend/src/main/java/com/hfxconnect/common/text/SlugGenerator.java` (extracted
shared utility), `backend/src/main/resources/db/migration/V3__create_resources_table.sql`.

### Tests
64 new tests (117 total backend tests): 24 pure unit tests for field
normalization/validation, 14 real-database repository/constraint tests, 23
Testcontainers-backed service-integration tests (chosen deliberately over mocking,
since category-existence/active-state checks are exactly the behavior mocking cannot
prove), and 2 regression tests guarding the unmapped-route fix. `./mvnw test` and
`./mvnw verify` both pass. Additionally verified manually against the real local
docker-compose database: the category/resource relationship, `ON DELETE RESTRICT`,
and data/migration-history persistence across an ordinary container restart.

### Evidence
Commits on branch `milestone/03b-resource-domain`; see
`docs/development-log/2026-07-19.md` for exact commands and observed output.

### Potential Resume Wording
Designed and implemented a PostgreSQL-backed resource domain (entity, repository,
service, business-layer validation) for a Spring Boot REST backend, including
category-relationship business rules, deterministic slug generation, and Canadian
address/postal-code validation; wrote 64 automated tests (unit, database-integration,
and service-integration against a real PostgreSQL instance via Testcontainers);
diagnosed and fixed both a JPA identifier-generation correctness issue and an
unrelated production routing defect discovered through the work's own regression
testing.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: real API response-time data once deployed (Milestone 12).

## Public Resource API

### Product Purpose
Exposes the resource domain (built and tested in Milestone 3B) as a real, callable
REST API — the first time an external client can discover actual community resources
(food banks, libraries, study spaces), not just categories.

### Technologies Used
Spring Web MVC, springdoc-openapi, Jakarta Bean Validation, Spring Data JPA
`@Query`/`JOIN FETCH`.

### Engineering Complexity
Diagnosed and prevented an N+1 query pattern before it shipped: embedding a category
summary (name/slug, not just ID) in every resource response would have triggered one
extra query per resource in a paginated list via `CommunityResource`'s lazy `category`
association. Fixed with explicit `JOIN FETCH` repository query variants (safe to
combine with `Pageable` for a to-one relationship, unlike a to-many fetch join would
be) rather than accepting the N+1 or reaching for an unrelated caching layer.
Identified and resolved two structural inconsistencies between the task's own
instructions and already-merged project state — a milestone-numbering conflict and a
described-but-unbuilt schema — documented rather than silently resolved either
direction. Refined the error-code design (splitting one combined
`CATEGORY_UNAVAILABLE` code into `CATEGORY_NOT_FOUND`/`INACTIVE_CATEGORY`) at exactly
the point those codes first became externally observable, rather than earlier
(premature) or never (leaving an imprecise combined code live in a public API).
Fixed a real regression the full test suite caught: a pre-existing test had used the
soon-to-be-mapped route itself as its "genuinely unmapped path" example.

### Implementation
`backend/src/main/java/com/hfxconnect/resource/` (`ResourceController`,
`ResourceCreateRequest`, `ResourceResponse`, `ResourceSummaryResponse`,
`ResourcePageResponse`, `CategorySummaryResponse`, updated `ResourceRepository`/
`ResourceService`/`ResourceDetails`),
`backend/src/main/java/com/hfxconnect/common/error/InvalidSortException.java`.

### Tests
30 new tests (147 total backend tests): 24 full-HTTP-layer integration tests
(`ResourceApiIntegrationTest`, via `TestRestTemplate` against the real database) plus
6 new service-layer tests for sort behavior and the new `getActiveById` method.
`./mvnw clean verify` passes — confirmed against the Surefire reports directly, not
hand-summed. Manually verified every scenario against the real local docker-compose
database before writing the automated tests.

### Evidence
Commits on branch `milestone/03c-public-resource-api`; see
`docs/development-log/2026-07-23.md` for exact commands and observed output.

### Potential Resume Wording
Designed and shipped a public REST API for a Spring Boot resource domain (create,
paginated/filtered list, detail-by-ID/slug), including embedded-association responses
with proactive N+1 prevention via JPA fetch joins, allowlisted sorting, and precise
error-code design; wrote 30 new automated tests (147 total) spanning full-HTTP-layer
and service-layer coverage; identified and resolved conflicts between task
instructions and already-shipped project state through direct verification rather
than assumption.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: real API response-time data once deployed (Milestone 12).

## Public Frontend

### Product Purpose
The first HFX Connect interface a real visitor can use: browse Halifax community
resources by category, sort and paginate results, and view full resource detail —
all backed by the real Category and Resource APIs, with no hard-coded data.

### Technologies Used
Next.js App Router (Server + Client Components), TypeScript strict mode, Tailwind
CSS, TanStack Query (server-side prefetch/hydration), Zod (runtime response
validation), Jest + React Testing Library.

### Engineering Complexity
Designed a typed API client (`getJson`) that centralizes query encoding, non-2xx
error mapping, and Zod schema validation, rather than scattering `fetch` calls across
components — a malformed or unexpected backend response fails safely into a generic
error state instead of rendering broken data or crashing. Verified every Zod schema
against the live backend's OpenAPI document before writing it, which caught that the
backend has no `accessibility` field on a resource despite the task brief describing
one — deliberately not implemented rather than fabricated, and documented as such.
Implemented the TanStack-Query-with-Next.js-App-Router server-prefetch/hydration
pattern so the initial page load already contains real data (verified directly
against the rendered HTML) while client-side interactions (filter/sort/pagination)
stay fully cached and query-key-scoped. Built the category/sort filter as a
progressive-enhancement `<form>` that works with JavaScript disabled and is
enhanced (not replaced) by client-side navigation when available. Diagnosed a
same-origin browser restriction (CORS) and resolved it on the backend with a
minimal, environment-configured allowlist (`WebCorsConfig`) rather than a wildcard
or an unnecessary proxy layer — documented as [ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md)
with the rejected alternatives and why. Debugged and fixed a real, pre-existing
`next/jest` module-resolution bug (`nextJest is not a function`) by tracing it to a
CommonJS/ESM interop mismatch specific to this Node/Next.js version combination,
rather than working around it superficially. Diagnosed a mid-session disk-space
exhaustion that was silently degrading filesystem performance system-wide (not a
code defect), and safely relocated the entire project — application code and full
git history — to external storage without data loss, verified byte-for-byte before
treating the new location as canonical.

### Implementation
`frontend/src/lib/api/` (typed client, per-resource operations),
`frontend/src/lib/validation/schemas.ts` (Zod), `frontend/src/lib/query/`
(TanStack Query client/provider/keys/URL-param parsing),
`frontend/src/app/` (homepage, `/resources`, `/resources/[slug]`),
`frontend/src/components/` (resources, categories, navigation, feedback),
`backend/src/main/java/com/hfxconnect/common/config/WebCorsConfig.java`.

### Tests
82 new frontend tests across 18 suites (API client, formatting, every shared
component, the resource-list and resource-detail experiences) plus 2 new backend
CORS tests (149 total backend tests, no regression in the existing 147). Two real
defects were caught and fixed by the test suite before commit: a duplicate "Reset
filters" link rendered simultaneously in two places, and an ambiguous test
assertion. `npm run lint`, `npm run typecheck`, and `npm run build` all pass
cleanly.

### Evidence
Commits on branch `milestone/04-public-frontend`; see
`docs/development-log/2026-07-24.md` for the full, exact sequence including the
disk-space/SSD-relocation incident.

### Potential Resume Wording
Built a Next.js App Router public frontend over a Spring Boot REST API, including a
typed/validated API client (TypeScript + Zod), TanStack Query server-prefetch/
hydration, URL-driven filter/sort/pagination state, and a fully keyboard-accessible
UI; resolved a cross-origin browser restriction with a minimal, documented backend
CORS policy; wrote 82 automated frontend tests that caught two real defects before
they shipped; diagnosed and worked around both a third-party tooling bug and a
mid-project infrastructure failure (disk exhaustion) without losing any work.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: real Lighthouse/Core Web Vitals scores and API
response-time data once deployed (Milestone 12).

## User Registration Foundation

### Product Purpose
Every authenticated capability the product plans (saved resources, submissions,
organization management, moderation) needs an account to belong to. This is the
first of three deliberately separated authentication sub-milestones (5A/5B/5C) —
the account itself, before login/tokens or roles/authorization exist.

### Technologies Used
Spring Data JPA, Flyway, BCrypt (`spring-security-crypto`'s `BCryptPasswordEncoder`,
strength 12 — not the full `spring-boot-starter-security`), Bean Validation,
springdoc-openapi, JUnit 5, Mockito, Testcontainers (real `postgis/postgis:17-3.5`).

### Engineering Complexity
Chose `spring-security-crypto` deliberately over `spring-boot-starter-security` to
get password hashing without the starter's auto-configured, default-secured filter
chain — which would have broken the explicit requirement that the existing
unauthenticated Category/Resource APIs keep working — documented as
[ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md), including why
strength-12 BCrypt over the default and over Argon2id for this milestone. Designed
account status (`ACTIVE`, not `PENDING_VERIFICATION`) around a real constraint: no
email-delivery mechanism exists yet, so gating new accounts behind a status they
could never leave would make them permanently unusable — solved by keeping
`emailVerified` as an independent column instead of collapsing verification into
status. Prevented privilege escalation structurally rather than by runtime check:
`RegistrationRequest` has no `role` field for a value to bind to at all, plus
`@JsonIgnoreProperties(ignoreUnknown = true)` so a submitted one is deterministically
ignored — verified with both an automated test and a live `curl` request. Applied
the project's established `saveAndFlush`-for-UUID-keys race-safety pattern
(`ResourceService`'s own precedent) to the new domain, and caught a real test bug
during development where a repository test used plain `save()` and its
constraint-violation assertion silently never fired until switched to
`saveAndFlush`. Diagnosed two Colima-specific Testcontainers environment failures
(missing `DOCKER_HOST`, then a failing Ryuk resource-reaper sidecar) and a
previously-encountered local port-5432 conflict during manual verification, none of
which were code defects, and documented the fixes in `backend/README.md`'s
Troubleshooting section rather than working around them silently.

### Implementation
`backend/src/main/java/com/hfxconnect/user/` (`User`, `Role`, `AccountStatus`,
`UserRepository`, `RegistrationValidation`, `RegistrationRequest`, `UserResponse`,
`UserConflictException`, `RegistrationService`, `AuthController`),
`backend/src/main/java/com/hfxconnect/common/config/PasswordEncoderConfig.java`,
`backend/src/main/resources/db/migration/V4__create_users_table.sql`.

### Tests
37 new backend tests (11 repository/database — real PostgreSQL constraints, not
mocks; 8 service unit tests — mocked repository and password encoder for
deterministic race-condition/hashing-path coverage; 18 full HTTP-layer API
integration tests, including a real `BCryptPasswordEncoder`) alongside the existing
149 (186 total, 0 failures). Two real test-authoring bugs were caught and fixed
before commit — a missing `saveAndFlush` that silently prevented a constraint-violation
assertion from ever firing, and a test email containing the literal word "password"
that broke its own leak-detection assertion. Manual verification against the real
database directly inspected the `users` table to confirm a genuine BCrypt hash is
stored and that no plaintext password ever appears.

### Evidence
Commits on branch `milestone/05a-user-registration`; see
`docs/development-log/2026-07-24.md` for the full session record and
`docs/milestones/milestone-05a-user-registration.md` for acceptance criteria.

### Potential Resume Wording
Designed and implemented a secure account-registration foundation for a Spring
Boot/PostgreSQL REST API, including BCrypt password hashing with a deliberately
scoped dependency choice that avoided prematurely securing existing public
endpoints, database-level race-safe uniqueness enforcement, and structural
(schema-level, not just runtime-checked) prevention of privilege escalation through
request input; documented the design trade-offs as an ADR and wrote 37 automated
tests spanning database, service, and full HTTP-layer integration coverage.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: registration endpoint latency under real BCrypt cost
once deployed (Milestone 12).

## Login, Token Refresh, and Logout (Authentication Sessions)

### Product Purpose
A registered account is only useful once its owner can actually log in and stay
logged in. This is the second of three deliberately separated authentication
sub-milestones (5A/5B/5C) — real sessions, before request-level authorization
exists to make use of them.

### Technologies Used
JJWT 0.12.6 (JWT issuance/validation, HS256), `SecureRandom`-backed opaque
tokens, SHA-256 hashing, Spring `TransactionTemplate`/`PROPAGATION_REQUIRES_NEW`,
`ResponseCookie`, Spring MVC CORS (`allowCredentials`), JUnit 5, Mockito
(including a `SimpleTransactionStatus`-backed transaction-manager mock),
Testcontainers (real `postgis/postgis:17-3.5`).

### Engineering Complexity
Designed a two-token authentication model (short-lived signed JWT access token
in the response body; opaque, SHA-256-hashed, rotating refresh token in an
`HttpOnly` cookie) where each token's transport was chosen to match the threat
it's actually exposed to, documented as an ADR including three alternatives
considered and rejected with reasons. Implemented refresh-token rotation with
family-wide reuse detection: presenting an already-consumed token revokes every
session descended from the same login, not just the one presented — a real
defense against a stolen-token replay, not just single-token invalidation.
Closed a login timing side channel deliberately: unknown-email and
wrong-password attempts perform the identical number of BCrypt comparisons (a
precomputed dummy hash stands in when no account exists), verified with a
dedicated Mockito interaction test, not just an identical response body.
**Found and fixed a genuine Spring transaction-management defect** during this
milestone's own manual verification, not through unit testing (which could not
have caught it): `@Transactional(noRollbackFor = ...)` did not actually prevent
a security-critical revocation from being rolled back immediately before the
exception signaling that revocation was thrown — discovered by running the real
application against the real database and inspecting `refresh_sessions`
directly with `psql` between live `curl` requests, then fixed with explicit
programmatic transaction control (`TransactionTemplate`/`PROPAGATION_REQUIRES_NEW`)
and re-verified with the same live reproduction. Made cookie `Secure`/`SameSite`
environment-configured rather than hardcoded, having reasoned through why local
development (same-site, different ports) and this project's actual production
topology (genuinely cross-site, per an earlier ADR) require different values.
Caught and removed a speculative, never-called repository method during a
deliberate self-review pass against the project's own established conventions,
before it ever reached a pull request.

### Implementation
`backend/src/main/java/com/hfxconnect/auth/` (`RefreshSession`+repository,
`AccessTokenService`, `RefreshTokenGenerator`, `RefreshSessionService`,
`AuthenticationService`, `RefreshService`, `LogoutService`, DTOs,
`RefreshCookieConfig`, exception types),
`backend/src/main/java/com/hfxconnect/common/error/` (`UnauthorizedException`,
`ForbiddenException`), `backend/src/main/java/com/hfxconnect/common/text/EmailNormalizer.java`,
`backend/src/main/resources/db/migration/V5__create_refresh_sessions_table.sql`.

### Tests
76 new tests (10 repository/database against real PostgreSQL constraints and
cascade-delete behavior; 10 access-token unit tests including signature/
expiration/issuer/claim-content verification; 7 refresh-token-generator unit
tests for entropy and hash determinism; 9 refresh-session-service unit tests
for rotation/reuse-detection logic; 6+3+3 further service unit tests; 27 full
HTTP-layer integration tests against a real database and real
`BCryptPasswordEncoder`/JJWT stack, including live cookie-attribute assertions;
1 CORS-credentials regression test) alongside the existing 197 (272 total, 0
failures). One speculative repository method was found and removed, with its
test, during self-review before the branch was pushed.

### Evidence
Commits on branch `milestone/05b-authentication-sessions`; see
`docs/development-log/2026-07-26.md` for the full session record, including the
transaction-rollback discovery, and
`docs/milestones/milestone-05b-authentication-sessions.md` for acceptance
criteria.

### Potential Resume Wording
Designed and implemented a JWT-plus-rotating-refresh-token authentication
session for a Spring Boot REST API, including family-wide refresh-token reuse
detection, a timing-safe generic login-failure response, and an
environment-aware secure-cookie policy reasoned through for both local
development and a genuinely cross-site production deployment; diagnosed and
fixed a real Spring transaction-rollback defect discovered only through live,
full-stack manual verification against a real database — not assumed away by
an annotation's documented (but, in this stack, incorrect) behavior — and wrote
76 automated tests spanning token, service, and full HTTP-layer integration
coverage.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: login/refresh endpoint latency under real BCrypt/JWT
cost once deployed (Milestone 12).
