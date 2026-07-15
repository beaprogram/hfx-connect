# Task 010: Category REST API, OpenAPI, and Integration Tests

## Objective

Expose the category domain as a REST API under `/api/v1/categories`, document it with
OpenAPI, and verify the complete stack — schema, service, and HTTP layer — with
integration tests against the real database, plus a full manual verification pass.

## Context

Part of Milestone 3A (Category Domain and API), depending on Task 009's service
layer. This is where the domain becomes something an actual HTTP client (eventually
the frontend, in Milestone 4) can use.

## Scope

- `CategoryController`: `POST`, `GET /{id}`, `GET /slug/{slug}`, `GET` (paginated
  list) under `/api/v1/categories`.
- `springdoc-openapi-starter-webmvc-ui` dependency; `OpenApiConfig`; `@Operation`/
  `@ApiResponses`/`@Schema` annotations on the controller and DTOs.
- `CategoryRepositoryIntegrationTest` (10 tests), `CategoryApiIntegrationTest` (14
  tests) — both Testcontainers-backed against the real `postgis/postgis:17-3.5`
  image.
- Fixed a stale assertion in the pre-existing `FlywayMigrationIntegrationTest`
  (Milestone 2B), which hardcoded "exactly one migration-history row" — no longer
  true once a second migration exists.
- Full manual verification against the real local docker-compose database.

## Out of Scope

Any endpoint beyond the four listed; update/delete endpoints (not justified).

## Acceptance Criteria

- `POST` returns `201`, a `Location` header pointing at `GET /{id}`, and the created
  resource's body.
- Both `GET` single-resource endpoints return `200` with the resource or `404` with
  the documented error shape.
- `GET` (list) returns a bounded, explicit page shape, rejecting out-of-range
  pagination with `400 INVALID_PAGINATION`.
- The generated OpenAPI document (`/v3/api-docs`) includes all three route templates,
  verified by both a live manual check and an automated test.
- `./mvnw test` and `./mvnw verify` pass in full (53 tests, including every test from
  earlier milestones).
- Manual verification against the real docker-compose database confirms behavior
  automated tests can't fully substitute for: real Flyway log output on a genuinely
  upgrading database, the actual `Location` header a browser/client would receive,
  and the health endpoint remaining `UP` throughout.

## Technical Approach

Chose `springdoc-openapi-starter-webmvc-ui:2.8.6` — the latest published release,
predating Spring Boot 4.1 — and verified compatibility empirically (booted the app,
confirmed `/v3/api-docs` listed the routes correctly) rather than assuming it would
work or assuming it wouldn't.

Test data isolation required two different strategies depending on test type, both
sharing the Milestone 2B Testcontainers singleton container:
`CategoryRepositoryIntegrationTest` uses `@Transactional` class-level rollback, safe
because every operation runs on the test thread; `CategoryApiIntegrationTest` cannot
use the same approach (real HTTP requests are handled by the embedded server's own
thread, on a separate transaction test-method rollback doesn't reach), so it uses
UUID-suffixed category names instead, keeping tests independent of both execution
order and each other without needing cleanup.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. Manual: category creation, retrieval by ID and slug,
listing, pagination, duplicate rejection, blank-name rejection, malformed JSON,
missing-category 404, error-shape inspection, OpenAPI document inspection, and health
endpoint — all performed against the real docker-compose database with exact commands
and output recorded in the development log.

## Result

Completed. 24 new tests (10 repository, 14 API) pass alongside the existing 29 from
earlier milestones (53 total). Manual verification against the real database matched
automated test behavior exactly, with no discrepancies found.

## Related Commit

`feat: expose category REST API with validation`, `test: add category database and
API integration coverage`
