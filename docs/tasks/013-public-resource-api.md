# Task 013: Public Resource API

## Objective

Expose `ResourceService` (complete since Milestone 3B) as a public REST API:
`ResourceController`, HTTP DTOs, and full test coverage, following the exact
conventions `CategoryController` already established.

## Context

Part of Milestone 3C (Public Resource API) — see
`docs/milestones/milestone-03c-public-resource-api.md` for why this work is labeled
3C rather than the "3B" the initiating instructions used, and why it builds on the
existing resource schema rather than the richer one those instructions described.

## Scope

- `ResourceController`: `POST /api/v1/resources`, `GET /api/v1/resources/{id}`,
  `GET /api/v1/resources/slug/{slug}`, `GET /api/v1/resources` (paginated,
  `categoryId` filter, allowlisted `sort`).
- DTOs: `ResourceCreateRequest`, `ResourceResponse`, `ResourceSummaryResponse`,
  `ResourcePageResponse`, `CategorySummaryResponse`.
- `ResourceDetails` extended with `categoryName`/`categorySlug`; `ResourceRepository`
  given `JOIN FETCH` variants to avoid N+1 when building responses.
- `ResourceService`: added `getActiveById`; added allowlisted `sort` to both listing
  methods; split `CategoryUnavailableException` into `CategoryNotFoundException`
  (404) and `InactiveCategoryException` (400).
- New shared exception: `InvalidSortException` (`common.error`, code
  `INVALID_SORT`), for the same reason `InvalidPaginationException` exists — a
  400 the existing category domain didn't need yet but which is generically
  reusable for any future allowlisted-sort endpoint.
- `ResourceApiIntegrationTest` (24 tests) and 6 new `ResourceServiceIntegrationTest`
  methods.
- Fixed `GlobalExceptionHandlerIntegrationTest`, which had used the
  soon-to-be-mapped `/api/v1/resources` as its "genuinely unmapped route" example.

## Out of Scope

Resource update/delete HTTP endpoints (business-layer methods already exist and are
tested; not exposed here — explicitly out of scope per the milestone instructions).
Schema changes (short/full description split, accessibility info, last-verified-at,
expanded enums) — see the milestone document's "A Note on Schema Scope."

## Acceptance Criteria

- All four endpoints behave exactly per `docs/api/README.md`'s Resources section.
- No N+1 queries on any read path (verified via `JOIN FETCH` repository queries).
- `CategoryNotFoundException`/`InactiveCategoryException` produce distinguishable
  `404`/`400` responses.
- `./mvnw clean verify` passes with the full suite, including all pre-existing
  Category/health/Flyway/OpenAPI tests.

## Technical Approach

Manually verified against the real docker-compose database before writing formal
tests (create, get by id/slug, list, category filter, sort, every error scenario) —
this is what caught the N+1-avoidance requirement's practical shape early and
confirmed the exact JSON response bodies before locking them into assertions.

The `CategoryUnavailableException` split was a deliberate, small refinement made
*because* this task is what first makes those codes externally observable via HTTP —
nothing before this task depended on the single combined code, so splitting it
carried no migration cost.

## Testing Requirements

`./mvnw test`, `./mvnw verify`; manual `curl` verification against the real database
(see the development log for exact commands/output); OpenAPI document inspection.

## Result

Completed. 147/147 tests pass (30 new: 24 in `ResourceApiIntegrationTest`, 6 added to
`ResourceServiceIntegrationTest`); one pre-existing test corrected. Manual
verification against the real database matched automated test behavior exactly.

## Related Commits

`refactor: split category-unavailable exception and add invalid-sort error`
`feat: expose public resource REST API with category-fetch-joined reads`
`test: add resource API integration coverage and fix stale unmapped-route test`
`docs: document Milestone 3C public resource API`
