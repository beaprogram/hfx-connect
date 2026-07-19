# Milestone 3B: Resource Persistence and Business Layer

## Objective

Implement the persistence model and business layer for HFX Connect community
resources — a real Halifax service (food assistance, a study space, newcomer
support, ...) classified by a category — without exposing any public HTTP
endpoint. The public resource API is Milestone 3C's responsibility.

## Product Value

Establishes the backend workflow a resource goes through: submitted to the business
layer → validated and normalized → its category confirmed to exist and be active → a
stable slug generated → stored with PostgreSQL enforcing integrity → retrievable,
updatable, or deactivatable through the service layer → excluded from active-only
reads once deactivated. Demonstrated entirely through automated tests (migration,
repository, and service-layer, all against a real database) rather than temporary
HTTP endpoints, per this milestone's explicit scope.

## Technical Scope

**Shared refactor:** `com.hfxconnect.category.CategorySlugGenerator` (package-private,
Milestone 3A) was extracted to `com.hfxconnect.common.text.SlugGenerator` (public) once
the resource domain needed byte-for-byte the same algorithm — a second real consumer,
not a speculative generalization. `CategoryService` was updated to use the shared
class; category API behavior is unchanged (verified by the full existing category test
suite still passing).

**Database:** `V3__create_resources_table.sql` — the `resources` table, with:

- `id UUID` (Hibernate-generated), unlike categories' `BIGINT IDENTITY` — resources
  are numerous, created over time, and may be referenced in public URLs, exactly as
  [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md) already
  anticipated when categories' ID type was decided.
- `category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT` — **note:**
  this is `BIGINT`, not `UUID`. The sub-milestone brief suggested a UUID foreign key,
  which is inconsistent with the already-merged, unmodifiable V2 migration
  (`categories.id` is `BIGINT`) — a foreign key must match its referenced column's
  type. Corrected during implementation; see the migration file's own comment.
- Required/bounded text fields, an allowlist-style check constraint for `province` (the
  13 real Canadian province/territory codes, not just "two letters"), a format check
  for `postal_code` and `slug`, and allowed-value checks for `cost_type` and
  `verification_status`.
- Two indexes: `resources_category_id_idx` (required — category-based access) and
  `resources_active_name_idx` (mirrors categories' own pattern — supports "list active
  resources by name").

**Domain (`com.hfxconnect.resource`):** `CommunityResource` (entity — named to avoid
colliding with `org.springframework.core.io.Resource`), `CostType`/`VerificationStatus`
enums, `ResourceRepository`, `ResourceValidation` (pure normalization/format
validation, no Spring/DB dependency), `ResourceService`, and three domain exceptions
(`ResourceNotFoundException`, `ResourceConflictException`, `CategoryUnavailableException`).
`Category` gained one new method, `deactivate()` — Milestone 3A had no way to produce
an inactive category at all (no update/deactivate endpoint), and Milestone 3B
genuinely needs one to test "resource creation under an inactive category is
rejected"; mirrors `CommunityResource#deactivate()` exactly.

**Business-layer models, not HTTP DTOs:** `CreateResourceCommand`,
`UpdateResourceCommand`, `ResourceDetails`, `ResourcePage` — deliberately not
`@RequestBody`/`@Valid`-annotated HTTP request/response types, since there is no
controller. `costType` is typed as the `CostType` enum directly (not a `String`
needing parse-and-validate), which is only possible because there's no HTTP JSON
boundary yet — Milestone 3C's HTTP DTOs will need their own string-to-enum handling.

**Bug fix (infrastructure, not resource-specific):** `GlobalExceptionHandler` had no
handler for Spring's `NoResourceFoundException`, so any genuinely unmapped route (not
just `/api/v1/resources`) returned `500 INTERNAL_ERROR` instead of `404`. Discovered
by this milestone's own required regression check ("confirm `/api/v1/resources`
returns 404, not 500"); fixed with a dedicated handler and a new
`GlobalExceptionHandlerIntegrationTest`.

## Out of Scope

`ResourceController`, `/api/v1/resources`, any HTTP-facing resource DTO, frontend
resource pages/data-fetching, operating hours, open-now logic, keyword search, cost
or verification filtering endpoints, PostGIS/lat-lon columns, radius search, maps,
authentication/authorization, saved resources, reports, submissions, moderation,
audit history, organization ownership, events, Redis, CI/CD, and deployment — all
explicitly deferred to Milestone 3C or later.

## Acceptance Criteria

**Database**

- [x] `V3` migration creates the `resources` table.
- [x] `V3` applies cleanly after `V1` and `V2` (verified against a fresh Testcontainers
      database and the real docker-compose database).
- [x] Flyway history records `V1`, `V2`, `V3` exactly once each.
- [x] Repeated startup is idempotent (extends the existing generalized idempotency
      test rather than re-deriving it).
- [x] Hibernate validates (`ddl-auto=validate`) but does not create/alter the schema.
- [x] Unique slug is enforced (`resources_slug_key`).
- [x] Category foreign key and `ON DELETE RESTRICT` are enforced (verified both by an
      integration test and manually via `psql`).
- [x] Required and enum-like constraints are enforced (name/description/address/city
      non-blank, slug format, province allowlist, postal code format, cost-type and
      verification-status allowlists).
- [x] No out-of-scope tables or columns were introduced.

**Domain and Persistence**

- [x] `CommunityResource` entity and `CostType`/`VerificationStatus` enums implemented.
- [x] Category relationship is `LAZY`, no cascade.
- [x] Repository methods are limited to what the service actually needs.
- [x] Entities never escape the persistence boundary — only `ResourceDetails`/
      `ResourcePage` do.
- [x] Slugs are deterministic (shared `SlugGenerator`), unique, and stable — `update()`
      never changes a resource's slug.
- [x] Timestamps populated via `@PrePersist`/`@PreUpdate`, same pattern as `Category`.

**Business Layer**

- [x] Create, update, deactivate, and active-read behavior all implemented and tested.
- [x] Missing and inactive categories are rejected with distinguishable exceptions.
- [x] Moving a resource to another category requires that category to exist and be
      active.
- [x] Inputs are normalized consistently (whitespace, province case, postal code
      format).
- [x] Postal code, email, phone, and website validation implemented and tested.
- [x] Unsafe website schemes rejected via an allowlist (`http`/`https` only), which
      inherently covers `javascript:`/`data:`/`file:` and any other scheme without
      needing a blocklist.
- [x] Failed operations do not partially persist (verified: repository row counts
      stay zero after rejected creates).
- [x] Domain errors never expose SQL, constraint names, or internal class names.

**Testing**

- [x] Migration tests pass (extended `FlywayMigrationIntegrationTest`).
- [x] Repository integration tests pass (`ResourceRepositoryIntegrationTest`, 14 tests).
- [x] Service tests pass (`ResourceServiceIntegrationTest`, 23 tests, against a real
      database — not mocks, since category-existence/active checks are exactly the
      kind of behavior mocking can't meaningfully prove).
- [x] Pure normalization/validation unit tests (`ResourceValidationTest`, 24 tests, no
      Spring context).
- [x] Database constraints proven against real PostgreSQL/PostGIS via Testcontainers.
- [x] Existing category/health/OpenAPI tests still pass unmodified in behavior.
- [x] `./mvnw test` passes (117/117).
- [x] `./mvnw verify` passes.

## Testing Requirements

See the Testing section above; exact commands and output are recorded in
`docs/development-log/2026-07-19.md`.

## Documentation Requirements

This document, two task documents, a development-log entry, `docs/database/README.md`,
`backend/README.md`, root `README.md`, `docs/architecture/backend-architecture.md`,
career-evidence and interview-notes updates.

## Security Considerations

No secrets introduced. Website URL validation uses an allowlist (only `http`/`https`
schemes accepted), which is a stronger guarantee than blocklisting specific dangerous
schemes — any scheme not on the allowlist is rejected, including ones not explicitly
anticipated. No resource mutation is reachable over HTTP yet (no controller exists),
so authentication/authorization remain legitimately out of scope for this milestone.
The `NoResourceFoundException` fix (see Technical Scope) also improves security
posture slightly: a generic 500 with a logged stack trace server-side was already safe
(no leakage to the client), but a 404 is the semantically correct, less
information-obscuring response for "this route doesn't exist."

## Accessibility Considerations

Not applicable — no frontend changes in this milestone.

## Risks

| Risk | Mitigation |
|---|---|
| The sub-milestone brief specified `category_id` as a UUID foreign key, which is impossible given `categories.id` is `BIGINT` (Milestone 3A, already merged, unmodifiable) | Used `BIGINT` (the only type that can actually reference `categories.id`), documented the correction explicitly in the migration file and this document, rather than silently deviating without explanation |
| `CommunityResource` uses a Hibernate-generated UUID `@Id`, not `GenerationType.IDENTITY` like `Category` — a plain `repository.save()` is not guaranteed to hit the database synchronously the way it is for `IDENTITY`, which would have made the race-condition-conflict catch in `ResourceService.create()` unreliable | Used `saveAndFlush()` specifically in the one place that needs to catch `DataIntegrityViolationException` synchronously; documented why in a code comment so it isn't "simplified" back to `save()` later |
| Testing "delete a referenced category" by deleting the same in-memory `Category` instance a loaded `CommunityResource` still references confuses Hibernate's own in-memory consistency check before any SQL runs, masking the real `ON DELETE RESTRICT` behavior under test | Detached the persistence context and re-fetched the category fresh before attempting the delete, so the actual SQL statement (and PostgreSQL's real rejection of it) is what the test observes |
| `Category` had no way to become inactive at all, but Milestone 3B genuinely needs to test "resource creation under an inactive category is rejected" | Added one focused `Category.deactivate()` method (no update endpoint, no broader API surface) — the same justified, minimal pattern already used for `CommunityResource` |

## Completion Summary

All planned Milestone 3B deliverables were completed and verified against a real,
running PostgreSQL/PostGIS instance: the `resources` table, the `CommunityResource`
domain, category-relationship business rules (existence, active-state, move
validation), deterministic and stable slugging, comprehensive field
normalization/validation, and 64 new automated tests (117 total across the backend,
confirmed against the Surefire reports — `grep -h "Tests run:" target/surefire-reports/*.txt`
— not just added up by hand, per the lesson from Milestone 3A's test-count
correction).
A real inconsistency in the sub-milestone brief (UUID vs. the already-established
`BIGINT` category key) was identified and corrected rather than silently followed or
silently deviated from. A genuine pre-existing infrastructure bug (500 instead of 404
for unmapped routes) was discovered through this milestone's own required regression
verification and fixed with a guarding test. No HTTP endpoint, controller, or
public-facing resource API was introduced, consistent with the milestone's explicit
scope — that is Milestone 3C's responsibility.
