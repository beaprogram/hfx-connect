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
login, JWT access tokens, rotating/reuse-detected refresh sessions, and logout.
Milestone 5C completed the authentication stage: request-level authentication,
role-based authorization, and a real frontend login/dashboard experience.
Milestone 6A added public keyword search. Feature-level entries (structured
operating hours, moderation) will continue to be added as those milestones
land.

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

## Request Authentication and Role-Based Authorization

### Product Purpose
A logged-in session is only meaningful once the backend actually enforces
who is allowed to do what. This is the third and final authentication
sub-milestone (5C) — it makes every earlier session (5A/5B) matter, by
protecting real routes, and gives the frontend its first real login
experience.

### Technologies Used
Spring Security 7.1 (`SecurityFilterChain`, `OncePerRequestFilter`,
`AuthenticationEntryPoint`, `AccessDeniedHandler`, `@AuthenticationPrincipal`),
JJWT (unchanged from 5B, now actually validated per request), springdoc-openapi
(`@SecurityScheme`), React Context (a hand-rolled auth provider, not a
third-party auth library), TanStack Query, Zod, JUnit 5, `psql`/direct SQL for
manual role manipulation (no role-management endpoint exists).

### Engineering Complexity
Designed and implemented the project's first real authorization boundary:
every authenticated request re-loads the account by the token's subject and
authorizes using its *current* database role and status — deliberately
never trusting the JWT's own `role` claim, which can go stale the instant an
administrator changes an account after a token was already issued. Proved
this decision live, not just in a unit test: issued a token as `USER`,
promoted the underlying account to `ADMIN` directly in the database with the
token left completely unchanged, and confirmed the same token now
authorizes an `ADMIN`-only action on its very next use — and, symmetrically,
that suspending an account mid-session revokes access on its next request
even though the token itself hasn't expired. Chose request-matcher-based
authorization over `@PreAuthorize`/method security specifically because this
milestone's policy (two role rules, no per-object ownership logic yet) didn't
justify a second configuration surface duplicating the same policy — a
documented, reasoned trade-off, not a default. Diagnosed and fixed a subtle
Spring Boot auto-configuration hazard before it ever shipped: adding
`spring-boot-starter-security` with no `UserDetailsService` bean defined
would have silently auto-configured a default user with a random generated
password printed to the console on every startup; excluded the offending
auto-configuration class explicitly and verified its absence. On the
frontend, implemented an in-memory-only access-token session (never
`localStorage`/`sessionStorage`, closing an XSS-driven token-theft path a
naive implementation would leave open) with refresh-cookie-based session
restoration and single-flight refresh coalescing — necessary because this
project's refresh tokens rotate on every use, so naïve concurrent refresh
calls could trigger a false-positive security lockout of the user's own
session. Also caught and fixed a pre-existing, order-dependent test
assumption (a test asserting a database table was globally empty, which had
only ever been true by incidental test-execution order) during this
milestone's own regression run, rather than leaving it to fail
unpredictably later.

### Implementation
`backend/src/main/java/com/hfxconnect/security/` (`JwtAuthenticationFilter`,
`CurrentUserPrincipal`, `SecurityConfig`, `ApiAuthenticationEntryPoint`,
`ApiAccessDeniedHandler`), `backend/src/main/java/com/hfxconnect/user/CurrentUserController.java`,
`frontend/src/lib/auth/auth-provider.tsx`, `frontend/src/lib/api/auth.ts`,
`frontend/src/components/auth/` (login/register forms, dashboard content,
protected-route guard, auth-aware nav).

### Tests
23 new backend tests (a full role × route authorization matrix; token
validation edge cases — missing/malformed/wrong-signature/expired/
wrong-issuer/deleted-user/disabled-account; the live stale-role-claim and
disabled-account-after-issuance proofs; CORS/error-shape regressions)
alongside the existing 272 (295 total, 0 failures). 38 new frontend tests
(API client, auth provider — including the single-flight and
no-storage-write guarantees — login/register forms, dashboard, protected
route, nav) alongside the existing 82 (120 total, 0 failures). Full manual
verification of the role matrix, stale-claim, and disabled-account scenarios
against the real running backend and database, since no role-management
endpoint exists to reach those states through the API.

### Evidence
Commits on branch `milestone/05c-role-authorization`; see
`docs/development-log/2026-07-27.md` for the full session record and
`docs/milestones/milestone-05c-role-authorization.md` for acceptance
criteria.

### Potential Resume Wording
Designed and implemented request-level authentication and role-based
authorization for a Spring Boot REST API using a custom JWT filter chain,
deliberately re-validating account role/status against the database on every
request rather than trusting a signed token's own claims — closing a
stale-privilege gap and proving the fix with a live database-state
reproduction, not just a unit test; built the corresponding frontend session
layer (in-memory token storage, refresh-cookie session restoration,
single-flight token refresh) to eliminate common JWT-in-the-browser XSS
exposure; and authored 61 new automated tests across both layers.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: authenticated-request latency overhead from the
per-request database role/status reload, once deployed (Milestone 12).

## Public Keyword Search

### Product Purpose
A category dropdown alone doesn't help a visitor who knows roughly what
they're looking for by name, neighbourhood, or a phrase from a description.
Keyword search is the first slice of Milestone 6 (Search and Filtering),
letting the public find a specific resource directly rather than scanning a
full category listing.

### Technologies Used
Spring Data JPA `@Query` (parameterized JPQL with optional-filter
predicates), PostgreSQL `LIKE`/`ESCAPE`, Next.js App Router (progressive-
enhancement GET forms), TanStack Query cache-key design, JUnit 5,
Testcontainers (real `postgis/postgis:17-3.5`), React Testing Library.

### Engineering Complexity
Designed and implemented a SQL-injection-safe keyword search using bound
JPQL parameters and an explicit `ESCAPE` clause to neutralize `LIKE`'s own
metacharacters (`%`, `_`) — then didn't just trust the design: built a
dedicated "trap" resource during manual verification, deliberately crafted
to produce a false-positive match if the escaping had a bug, and confirmed
live against the real database that it did not match. Recognized that
adding keyword search as a *second* independent optional filter (alongside
the existing category filter) was the right trigger to consolidate two
near-duplicate service/repository methods into one parameterized query with
two optional predicates, rather than multiplying into four method
combinations — a deliberate architectural decision, documented as an ADR
with alternatives considered and rejected, not an ad hoc addition. Caught
and fixed two bugs in the milestone's own newly-written tests (a search
phrase that wasn't actually a contiguous substring of the seeded data) by
re-verifying the test's own logic against real behavior rather than
assuming a failing test meant the implementation was wrong.

### Implementation
`backend/src/main/java/com/hfxconnect/resource/ResourceSearchQuery.java`,
`ResourceRepository.search`, `ResourceService.search`,
`backend/src/main/java/com/hfxconnect/common/error/InvalidSearchQueryException.java`,
`frontend/src/lib/query/resource-list-params.ts`,
`frontend/src/components/resources/resource-filter-form.tsx`,
`frontend/src/components/resources/resource-list-view.tsx`.

### Tests
40 new backend tests (14 pure unit tests for query normalization/escaping;
16 service-integration tests including case-insensitivity, category
combination, pagination, sorting, and a wildcard-literal proof; 10 full
HTTP-layer integration tests against a real database) alongside the
existing 295 (335 total, 0 failures). 23 new frontend tests (API client
encoding, URL-state parsing, the search form's submit/preserve/clear
behavior, and keyword-aware result-summary/no-results wording) alongside
the existing 120 (143 total, 0 failures).

### Evidence
Commits on branch `milestone/06a-keyword-search`; see
`docs/development-log/2026-07-28.md` for the full session record and
`docs/milestones/milestone-06a-keyword-search.md` for acceptance criteria.

### Potential Resume Wording
Designed and implemented a SQL-injection-safe keyword search feature for a
Spring Boot REST API using parameterized JPQL with explicit `LIKE`-wildcard
escaping, verified with a deliberately crafted false-positive test case
against a real PostgreSQL database rather than assumed correct from the
escaping logic alone; consolidated two near-duplicate filtered-listing code
paths into a single parameterized query when a second independent optional
filter made that the right generalization, documented as an architecture
decision record; and authored 63 new automated tests spanning unit,
integration, and full-stack HTTP-layer coverage across both the backend and
frontend.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: query latency for the `LIKE`-based search at
real production data volume, to validate (or invalidate) the "no index
needed yet" assumption documented in ADR-010.

## Structured Operating Hours and Open-Now Logic

### Product Purpose
Completes Milestone 6 (Search and Filtering): a visitor can now filter by
cost type, verification status, and "open now," and see a resource's
actual weekly schedule — exactly the practical information someone
searching for a food bank or shelter after hours needs, and information no
prior milestone captured at all.

### Technologies Used
`java.time` (`Clock`, `DayOfWeek`, `LocalTime`, `ZoneId`, `ZonedDateTime`)
for deterministic, timezone-correct time handling; Spring Data JPA
correlated `EXISTS` subqueries; PostgreSQL `CHECK`/`UNIQUE` constraints for
schedule-validity enforcement at the database level; Next.js progressive-
enhancement forms; Zod schema validation; JUnit 5 fixed-`Clock` testing.

### Engineering Complexity
Designed a same-day/overnight/overnight-continuation-from-yesterday open-
now calculation that has to agree in two independent expressions — a Java
algorithm (`OpenNowCalculator`, for display) and a correlated SQL `EXISTS`
subquery (for the `openNow=true` filter) — and proved the two actually
agree by testing both against identical seeded data, rather than trusting
a shared design description alone. Injected `java.time.Clock` throughout
rather than calling `Instant.now()`/`LocalTime.now()` directly, so 14
boundary/DST test cases (exactly-at-opening, exactly-at-closing, overnight
before and after midnight, Sunday-to-Monday wraparound, standard-time and
daylight-time) are fully deterministic rather than dependent on when the
test suite happens to run. Rather than assume the milestone brief's own
`"09:00"` time-format example was correct, wrote a live backend test
asserting on the raw JSON wire format before writing a single line of
frontend code against it — caught that Jackson's actual default
serialization includes seconds (`"09:00:00"`), preventing a frontend
parsing bug that would only have surfaced once real data reached the UI.
Extended an already-unified filtered-listing query (from the keyword-
search milestone) with a correlated subquery rather than reaching for a
new query-abstraction layer, keeping pagination totals exact by
construction — the `openNow` predicate lives inside the same `count`
query as the page query, so a resource that turns out to be closed can
never be silently miscounted.

### Implementation
`backend/src/main/resources/db/migration/V6__create_resource_operating_hours.sql`,
`backend/src/main/java/com/hfxconnect/resource/OpenNowCalculator.java`,
`ResourceOperatingHours.java`, `OperatingHoursValidation.java`,
`ResourceRepository.search` (extended), `ResourceService.search`/
`replaceOperatingHours`, `backend/src/main/java/com/hfxconnect/common/config/ClockConfig.java`,
`frontend/src/lib/formatting/labels.ts` (`formatLocalTime`),
`frontend/src/components/resources/resource-detail.tsx` (weekly schedule),
`frontend/src/components/resources/resource-filter-form.tsx` (cost/
verification/open-now controls).

### Tests
74 new backend tests (14 fixed-`Clock` open-now unit tests including two
DST cases; 13 schedule-validation unit tests; 11 migration/repository
integration tests; 18 service-integration tests covering every filter
combination and the schedule-replacement endpoint; 13 HTTP-layer
integration tests including a live wire-format assertion; 5 write-endpoint
role-matrix tests) alongside the existing 335 (409 total, 0 failures). 34
new frontend tests alongside the existing 143 (177 total, 0 failures).

### Evidence
Commits on branch `milestone/06b-operating-hours-filters`; see
`docs/development-log/2026-07-29.md` for the full session record and
`docs/milestones/milestone-06b-operating-hours-filters.md` for acceptance
criteria.

### Potential Resume Wording
Designed and implemented a timezone-correct, DST-safe "open now"
calculation for a Spring Boot REST API using dependency-injected
`java.time.Clock` for full test determinism, expressed consistently across
both a Java algorithm and a correlated SQL subquery filter and proved
consistent by testing both against the same data; verified a third-party
serialization assumption against a running server before building a
frontend on top of it, catching a wire-format mismatch before it could
reach production; and extended an existing parameterized-query design with
two new filters without sacrificing exact pagination totals, authoring 108
new automated tests across backend unit, integration, and frontend
coverage.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: correlated-subquery `openNow` filter latency at
real production data volume, and whether the single `resource_operating_hours_resource_id_idx`
index remains sufficient once the dataset is large enough to matter.

## PostGIS Resource Locations and Nearby Search

### Product Purpose
The backend foundation for "what's near me" — the first time this
platform can answer a genuinely geographic question, rather than only
name/category/cost/verification/open-now filtering. Sets up a stable,
documented API contract for the visual map a following milestone builds
on top of.

### Technologies Used
PostgreSQL/PostGIS (`geography(Point, 4326)`, `ST_DWithin`, `ST_Distance`,
`ST_MakePoint`, GiST spatial indexing), Spring Data JPA native
`@Query`/`@Modifying` queries and closed interface projections, `EXPLAIN`
query-plan verification.

### Engineering Complexity
Before writing any mapping code, fetched the exact published POM for the
`hibernate-spatial` version matching this project's actual
`hibernate-core` version from Maven Central directly — rather than
assume from a tutorial — and found it depends on Geolatte-geom, not JTS
(the geometry library most public examples assume), with no verified
compatibility precedent anywhere in this project's own dependency
history. Rather than adopt that risk for a feature that already needed
native SQL regardless (`ST_DWithin`/`ST_Distance` have no JPQL
equivalent), made the deliberate architectural call to keep the new
`location` column entirely outside the ORM's entity mapping — every read
and write goes through a native query with a closed-projection read
model — and documented the reasoning as an ADR rather than leaving it to
be rediscovered. Extended an already-established "one unified,
independently-optional-filter query" pattern (from two prior milestones)
into native SQL syntax without duplicating its `openNow` logic — the same
same-day/overnight-continuation algorithm, expressed once in Java and
once in SQL, deliberately kept in sync by testing both against identical
seeded data. Found and fixed a genuine cross-test-class isolation bug in
my own new test suite (an HTTP-driven test in a different test class had
committed real data at the same coordinate my repository test used as its
search origin) by adding proper scoping rather than loosening the
assertion. Proved the spatial index was actually being used — not just
present — with a live `EXPLAIN` showing `Index Scan using
idx_resources_location_gist`, rather than assuming an index helps just
because it exists.

### Implementation
`backend/src/main/resources/db/migration/V7__add_resource_location.sql`,
`backend/src/main/java/com/hfxconnect/resource/ResourceRepository.java`
(`updateLocation`/`findNearby`), `NearbyResourceProjection.java`,
`ResourceService.java` (`replaceLocation`/`nearby`),
`CoordinateValidation.java`, `ResourceLocationValidation.java`.

### Tests
73 new backend tests (16 coordinate-validation unit tests; migration/
spatial/nearby repository tests including a live `EXPLAIN` check; 22
service-integration tests covering distance ordering, radius boundaries,
and every filter combination; 17 HTTP-layer integration tests; 5
role-matrix tests) alongside the existing 409 (482 total, 0 failures).
177 existing frontend tests reconfirmed passing unchanged — this
milestone was deliberately backend-only.

### Evidence
Commits on branch `milestone/07a-postgis-nearby-search`; see
`docs/development-log/2026-07-30.md` for the full session record and
`docs/milestones/milestone-07a-postgis-nearby-search.md` for acceptance
criteria.

### Potential Resume Wording
Designed and implemented a PostGIS-backed geospatial search feature for a
Spring Boot REST API, verifying a third-party ORM-mapping library's actual
transitive dependencies against its published artifact before adopting
it — discovering an undocumented incompatibility with the assumed
approach and choosing a native-SQL strategy instead, backed by a live
`EXPLAIN`-verified spatial index; extended an existing unified-filter
query design into native SQL without duplicating business logic; and
authored 73 new automated tests, including catching and fixing a genuine
cross-test-class data-isolation bug during development.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: nearby-search query latency at real production
data volume and geographic density, to validate the current single-GiST-index
approach against `<->` KNN-operator ordering as a future optimization.

## Interactive Map, Browser Geolocation, and List/Map Synchronization

### Product Purpose
The visual completion of "what's near me" — turns Milestone 7A's
distance-in-a-list-row into an actual interactive map a visitor can pan,
zoom, cluster-explore, and search from their own location, without ever
losing the fully accessible list as a fallback.

### Technologies Used
Leaflet, React Leaflet 5, `react-leaflet-cluster`/`leaflet.markercluster`,
the browser Geolocation API, Next.js `dynamic()` client-only loading,
TanStack Query, React Context for session-scoped cross-route state, and
(new to this project) Playwright for genuine headless-browser manual
verification.

### Engineering Complexity
Before installing any mapping package, fetched each candidate's real
published `peerDependencies` from the npm registry and cross-checked them
against this project's actual `react@19.2.4`/`next@16.2.11` versions —
rather than copy a React-Leaflet integration example that might target an
incompatible major version — and confirmed a single deduped Leaflet
instance across the whole dependency tree after installing. Solved a
genuinely hard state-architecture problem: session state (search centre,
radius, geolocation status, selection) had to survive both a filter-
driven URL navigation and a resource-detail-page round trip, while a
coordinate could never appear in the URL or browser storage at all —
resolved by mounting a React Context provider at the Next.js App Router
*layout* level (which persists across exactly those navigations) rather
than the page level (which doesn't), a distinction that isn't obvious
without understanding the framework's own route-segment lifecycle.
Diagnosed and fixed a real, user-facing bug during manual verification
that the automated test suite's mocked routing hadn't caught: a
URL-shareability feature was leaking query parameters onto an unrelated
page after a real client-side navigation — found only by driving an
actual headless-Chromium session through the real interaction, not by
reasoning about the code in isolation, then fixed at the root cause and
covered by a new regression test before merging. Diagnosed and fixed a
bundler-specific Leaflet default-icon asset-path issue and confirmed the
fix against both a production build and real rendered marker icons in a
live browser screenshot, rather than assuming a community fix would apply
unmodified to this project's specific static-asset-import behavior.

### Implementation
`frontend/src/lib/map/map-search-context.tsx`,
`frontend/src/lib/map/use-geolocation.ts`,
`frontend/src/components/map/nearby-map.tsx`,
`frontend/src/app/resources/layout.tsx`,
`frontend/src/components/resources/{resource-explorer,map-explorer-view,
nearby-map-view,nearby-resource-card,view-toggle}.tsx`.

### Tests
85 new frontend tests (nearby-API-client coordinate-order/validation
tests; a full geolocation state-machine test suite including a real
denial/timeout/retry path; map-search-state tests including a regression
test for the URL-leak bug found during manual verification; Leaflet-
mocked marker-coordination tests covering clustering, selection, and
popup content; list/map view-coordination tests confirming a selection
change never triggers a refetch; `ResourceExplorer` integration tests)
alongside the existing 177 (262 total, 0 failures). All 482 backend tests
reconfirmed passing unchanged — zero backend files were modified this
milestone.

### Evidence
Commits on branch `milestone/07b-interactive-map`; see
`docs/development-log/2026-08-02.md` for the full session record and
`docs/milestones/milestone-07b-interactive-map.md` for acceptance
criteria.

### Potential Resume Wording
Built an interactive map feature (Leaflet/React Leaflet, marker
clustering) for a Next.js/React 19 application on top of an existing
geospatial REST API, verifying third-party package compatibility against
the project's real dependency versions before adoption; designed a
React Context state architecture at the framework's route-layout level to
correctly persist session state across client-side navigations while
keeping precise location data out of the URL and browser storage
entirely; and used real headless-browser automation to catch and fix a
genuine navigation bug the mocked automated test suite couldn't have
caught, then added regression coverage for it.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: marker-rendering and clustering performance
at a realistic production result-page size and marker density, and real
OpenStreetMap tile-load latency from a production-region client, to
validate whether a managed tile provider is needed sooner than assumed.

## Saved Resources and Authenticated Dashboard Integration

### Product Purpose
The first feature that gives an authenticated account a reason to exist
beyond authentication itself: come back to a resource found earlier
without re-searching for it, from any of the three places a resource
appears (list card, map card, detail page) and from a dedicated
dashboard section.

### Technologies Used
Spring Data JPA (idempotent write semantics backed by a database unique
constraint), Spring Security (`@AuthenticationPrincipal`-only identity),
TanStack Query (batched status lookups, targeted cache patching and
invalidation, cross-cutting cache-lifecycle management via
`useQueryClient()` inside an existing auth provider), and Playwright for
real two-account, cross-session manual verification.

### Engineering Complexity
Designed a race-safe idempotent save/remove pair where the database's
own unique constraint — not the application-level existence check alone
— is the actual authority: a genuine concurrent double-save resolves to
one row and two successful responses by catching the losing insert's
constraint-violation exception, verified with a real concurrent-request
integration test rather than trusted by inspection. Solved a private-
data cache-isolation problem with no precedent in this codebase: saved-
resource data needed to be fully isolated per account in a client-side
cache that, unlike the backend, is shared browser state that could
otherwise leak a first account's saved status into a second account's
session after a logout or an in-tab account switch — resolved by having
the existing authentication provider itself clear every relevant cached
query at the exact two moments that risk exists, rather than trusting
every future consumer of that data to remember to do so independently.
Designed a batched-status architecture (one HTTP request per page of
resource cards, never one per card) enforced by convention and verified
directly with a test asserting the request-count invariant holds
regardless of how many cards a page renders. Found and fixed a real,
previously invisible production bug during this milestone's own manual
browser verification — a CORS configuration that had never actually
allowed `PUT`/`DELETE` methods cross-origin, undiscovered for three
milestones because nothing had ever driven a browser-originated write
of that kind against it before — diagnosed from a raw CORS preflight
failure, fixed, and covered by a new regression test.

### Implementation
`backend/src/main/java/com/hfxconnect/savedresource/` (entity,
repository, service, controller, DTOs, migration);
`frontend/src/lib/query/use-saved-resources.ts`,
`frontend/src/lib/auth/return-to.ts`,
`frontend/src/components/resources/save-resource-button.tsx`,
`frontend/src/components/auth/saved-resources-section.tsx`; the
`AuthProvider` cache-clearing addition in
`frontend/src/lib/auth/auth-provider.tsx`.

### Tests
71 new backend tests (17 repository — including a genuine concurrent-
insert race resolving to one row; 25 service; 29 API integration
covering the full `USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` role matrix
and per-account isolation) plus 1 new CORS regression test, alongside
the existing 482 (554 total, 0 failures). 65 new frontend tests across
6 new files, alongside the existing 262 (327 total, 0 failures) — plus
updates to 11 pre-existing test files whose rendered components gained
a new authentication/query-client dependency, diagnosed by running the
full suite and fixing each resulting failure systematically. 27/27
scripted real-browser manual verification checks passed, including
genuine two-independent-account isolation and account-switch cache
clearing.

### Evidence
Commits on branch `milestone/08a-saved-resources`; see
`docs/development-log/2026-08-03.md` for the full session record and
`docs/milestones/milestone-08a-saved-resources.md` for acceptance
criteria.

### Potential Resume Wording
Designed and implemented a race-safe, idempotent saved-resources feature
for a Spring Boot/Next.js application, using a database unique
constraint (not an application-level check alone) as the authoritative
guard against concurrent-write duplication, verified with a real
concurrent-request integration test; architected a private-data cache-
isolation strategy in TanStack Query so a second authenticated account
in the same browser session could never see a first account's cached
saved-resource state; and used real two-account browser automation to
discover and fix a previously invisible CORS configuration gap that had
silently blocked every cross-origin `PUT`/`DELETE` request in the
application until this milestone's own manual verification caught it.

### Measurements Still Needed
[MEASURE AFTER DEPLOYMENT]: saved-resource list/status query latency at
realistic per-account saved-item counts, and real-world save/remove
request volume, to validate whether the current page-size cap and
batch-status id limit (100) remain generous enough in practice.
