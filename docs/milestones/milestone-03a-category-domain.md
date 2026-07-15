# Milestone 3A: Category Domain and API

## Objective

Implement the first production-quality vertical backend slice for HFX Connect —
category management — establishing the layering pattern (entity, repository, DTOs,
service, controller, centralized errors, OpenAPI, tests) every later domain will
follow.

## Product Value

Categories classify Halifax community resources (Food Assistance, Study Spaces,
Employment Support, Newcomer Support, Recreation, Community Events). A resource
cannot be meaningfully created until categories exist to classify it, making this the
correct first domain. Category management is currently an internal/administrative
capability with no public consumer yet — the public browsing experience comes with
the frontend work in later milestones.

## Technical Scope

- Flyway migration `V2__create_categories_table.sql`.
- JPA entity `Category` (setter-free — see `docs/architecture/backend-architecture.md`).
- `CategoryRepository` (four focused query methods).
- `CategorySlugGenerator` — deterministic, pure slug generation (see
  [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md)).
- `CategoryCreateRequest`, `CategoryResponse`, `CategoryPageResponse` DTOs.
- `CategoryService` — normalization, duplicate detection (application pre-check +
  database-constraint-violation translation), pagination validation, transactions.
- `CategoryController` — `POST`, `GET /{id}`, `GET /slug/{slug}`, `GET` (paginated
  list) under `/api/v1/categories`.
- `com.hfxconnect.common.error` — `ApiException` hierarchy, `ValidationException`,
  `GlobalExceptionHandler`, `ApiError` — the shared error-handling pattern.
- `springdoc-openapi` dependency and annotations; `OpenApiConfig` for document
  metadata.
- `spring-boot-starter-data-jpa` and `spring-boot-starter-validation` added;
  `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`
  configured.
- A JPA/Flyway startup-ordering fix (`EntityManagerFactoryDependsOnPostProcessor`) —
  discovered necessary during this milestone; see Risks below.
- 53 automated tests across 7 test classes (up from 6 tests / 3 classes at the end of
  Milestone 2B).

## Out of Scope

Resource tables/entities/endpoints, operating hours, frontend category pages or data
fetching, authentication/authorization/roles, organization management, saved
resources, reports, moderation, maps, search, Redis, CI/CD, deployment. No `PATCH`/
`DELETE` endpoints — not justified by anything in this milestone's scope.

## Design Decisions

Full rationale: [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md).
Summary:

- **Numeric (`BIGINT IDENTITY`) primary key**, not UUID — categories are a small,
  stable, admin-managed reference list, consistent with the project's own original
  schema sketch (`category_id BIGINT` on `resources`).
- **`normalized_name` column with a `UNIQUE` constraint** for case/whitespace-insensitive
  duplicate prevention, computed once in the service layer (not a SQL expression
  index), so the normalization rule exists in one place and is unit-tested directly.
- **Slugs are always derived from the name, never supplied by the caller** — reduces
  validation surface for a feature with no demonstrated need for custom slugs yet.
- **Slug collisions are rejected with `409`, not silently suffixed** — for a small,
  curated category list, a silent suffix would more likely mask a real duplicate-content
  mistake than serve a genuine need.
- **No update/delete endpoints** — not justified by this milestone's scope; the entity
  has no setters as a direct consequence.

## Temporary Security Limitation

`POST /api/v1/categories` is **not protected by authentication yet**. Anyone who can
reach the API can create a category. This is deliberate and documented, not an
oversight — role-based authorization is Milestone 5's responsibility. This is
restated in `docs/api/README.md`, `backend/README.md`, the root README's known
limitations, and the `CategoryController` class Javadoc, and is explicitly called out
in the OpenAPI operation description for `POST /api/v1/categories`.

## Acceptance Criteria

**Database**

- [x] `V2__create_categories_table.sql` creates the `categories` table.
- [x] Migration works from the existing V1 schema (verified: fresh Testcontainers
      database and the real docker-compose database, which already had V1 applied).
- [x] Required fields (`name`, `slug`) are `NOT NULL`.
- [x] Slug uniqueness is enforced (`categories_slug_key`).
- [x] Category-name uniqueness (case/whitespace-insensitive) is enforced
      (`categories_normalized_name_key`).
- [x] Flyway remains authoritative; Hibernate never creates/alters schema
      (`ddl-auto=validate`).
- [x] Hibernate schema validation succeeds at startup.

**Domain**

- [x] `Category` entity maps cleanly to the migration's columns.
- [x] `CategoryRepository` has four focused methods, no speculative ones.
- [x] DTOs are explicit; the entity is never returned from or accepted by the API.
- [x] `CategoryController` contains no business logic.
- [x] `CategoryService` owns normalization, slug generation, duplicate handling,
      transactions, and DTO mapping.
- [x] Slug generation is deterministic (9 dedicated unit tests).
- [x] Duplicate conflicts are handled at both the application level (fast, clear
      error) and the database level (authoritative, race-safe).

**API**

- [x] `POST /api/v1/categories` — 201, `Location` header, response body.
- [x] `GET /api/v1/categories/{id}` and `GET /api/v1/categories/slug/{slug}` — 200 or
      404.
- [x] `GET /api/v1/categories` — paginated, bounded (max size 100), sorted by name.
- [x] Correct status codes throughout (201/200/400/404/409/500).
- [x] Error shape is consistent across every failure mode.
- [x] OpenAPI documents all four endpoints (verified live and by test).
- [x] Temporary unsecured-write limitation is documented in five places (see above),
      not just claimed once.

**Testing**

- [x] Repository/database tests pass (10 tests, real PostgreSQL/PostGIS via
      Testcontainers).
- [x] Service tests pass (14 tests, mocked repository — see Risks for why mocking was
      the right call for the race-condition path specifically).
- [x] API integration tests pass (14 tests, full HTTP layer, real database).
- [x] Database constraints are tested directly (raw SQL, not only through the
      service).
- [x] Validation is tested (blank, missing, excessive length, empty-slug names).
- [x] Error responses are tested (shape, codes, no leaked internals).
- [x] Testcontainers uses the real `postgis/postgis:17-3.5` image, not a generic
      Postgres image or H2.
- [x] `./mvnw test` passes — 53/53.
- [x] `./mvnw verify` passes.

**Manual verification** — all performed against the real docker-compose database, not
only Testcontainers; see the development log for exact commands and output:

- [x] Database container healthy.
- [x] Backend starts; Flyway applies V2 against an already-V1 database.
- [x] Category creation, retrieval (by ID and slug), and listing work.
- [x] Duplicate request → 409.
- [x] Invalid request (blank name) → 400.
- [x] Missing category → 404.
- [x] Health remains UP throughout.
- [x] OpenAPI includes all three category routes.

**Documentation** — schema, API, slug rules, tests, known limitations, development
log, recruiter evidence, and interview notes were all updated; see Documentation
Requirements below for the exact file list.

**Git**

- [x] Branch contains only Milestone 3A work.
- [x] Commits are coherent, conventional, and reviewed via `git diff --staged` before
      each one.
- [x] No secrets tracked; no generated output tracked.

## Documentation Requirements

`docs/api/README.md`, `docs/database/README.md`,
`docs/architecture/backend-architecture.md` (new),
[ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md) (new),
this milestone document, three task documents, a development-log entry,
`backend/README.md`, root `README.md`, and career-evidence/interview-notes updates.

## Security Considerations

See "Temporary Security Limitation" above. Additionally: no request body or query
parameter is trusted without validation; error responses never include stack traces,
SQL, table names, or exception class names (verified by a dedicated integration test
assertion on the malformed-JSON response body).

## Accessibility Considerations

Not applicable — backend-only milestone, no UI.

## Risks

| Risk | Mitigation |
|---|---|
| Spring Boot 4.1's missing Flyway auto-configuration (Milestone 2B, ADR-004) has a second-order consequence once JPA exists: nothing orders the `flyway` bean before JPA's `entityManagerFactory` bean, so Hibernate's schema validation could run against a not-yet-migrated database | Diagnosed from the actual startup failure ("missing table [categories]") rather than assumed; fixed with `EntityManagerFactoryDependsOnPostProcessor` (the same mechanism Spring Boot's own removed auto-configuration used internally), documented in `FlywayMigrationConfig`'s Javadoc |
| springdoc-openapi's latest published release (2.8.6) predates Spring Boot 4.1 and could plausibly be incompatible given how much else has moved in this Spring Boot version | Verified empirically rather than assumed: added the dependency, booted the app, and confirmed `/v3/api-docs` correctly lists all three category routes before relying on it further |
| Testing the database-constraint-violation race-condition path with genuine concurrency would be slow and flaky | Tested the exception-translation *logic* with a mocked repository instead (`CategoryServiceTest.createTranslatesADatabaseRaceConditionIntoAConflict`) — appropriate test-pyramid placement for this specific concern, while the constraints themselves are verified for real against PostgreSQL in `CategoryRepositoryIntegrationTest` |
| Shared Testcontainers singleton container (established in Milestone 2B) means data persists across test classes within one run | Repository tests use `@Transactional` rollback (safe — same test thread); API tests use UUID-suffixed names instead of relying on cleanup, since HTTP requests run on a separate thread where test-method transaction rollback doesn't apply |

## Completion Summary

All planned Milestone 3A deliverables were completed and verified twice — once via 53
automated tests (unit, repository/database, and full API integration, all against the
real `postgis/postgis:17-3.5` image) and again via a full manual pass against the
actual local docker-compose database. A pre-existing test from Milestone 2B
(`FlywayMigrationIntegrationTest`) was found to contain a now-stale hardcoded
assertion once a second migration existed, and was fixed to assert the underlying
idempotency invariant generically instead of a specific row count, so it won't need
updating again as more migrations are added. No resource schema, APIs, or
authentication were introduced, consistent with the milestone's explicit scope.
