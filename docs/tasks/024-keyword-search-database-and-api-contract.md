# Task 024: Keyword-Search Database and API Contract

## Objective

Add a safe, parameterized keyword-search capability to the backend's public
resource listing, consolidating the existing category-filter query into one
unified, optionally-filtered query rather than multiplying the repository/
service surface.

## Context

The first of two tasks completing Milestone 6A. Builds directly on
Milestone 3C's public `GET /api/v1/resources` endpoint and its existing
`categoryId`/`sort`/pagination handling.

## Scope

- `com.hfxconnect.resource.ResourceSearchQuery` — normalizes `q` (trim,
  whitespace collapse, control-character stripping, 100-character maximum)
  and builds the wildcard-escaped `LIKE` pattern.
- `com.hfxconnect.common.error.InvalidSearchQueryException` — `400
  INVALID_SEARCH_QUERY`.
- `ResourceRepository.search(categoryId, likePattern, pageable)` — replaces
  `findByActiveWithCategory`/`findByCategoryIdAndActiveWithCategory` with
  one parameterized JPQL query; both filters independently optional via
  `(:param IS NULL OR ...)` predicates.
- `ResourceService.search(query, categoryId, page, size, sort)` — replaces
  `listActive`/`listActiveByCategory`.
- `ResourceController` — `GET /api/v1/resources` accepts and documents `q`.
- OpenAPI documentation for `q`.
- [ADR-010](../decisions/ADR-010-keyword-search-design.md).

## Out of Scope

Frontend search UI (Task 025), structured operating hours/open-now
(Milestone 6B), geospatial search (Milestone 7).

## Acceptance Criteria

- [x] `q` is optional; blank/whitespace-only is treated as no filter; an
      over-100-character normalized query returns `400
      INVALID_SEARCH_QUERY`.
- [x] Search matches `name`, `description`, `addressLine1`, `city`,
      case-insensitively, as a substring.
- [x] `%` and `_` in the query match literally, not as `LIKE` wildcards —
      proven with a deliberate false-positive "trap" resource, not just
      asserted from the escaping code's design.
- [x] `categoryId` and `q` combine correctly in every combination
      (neither/either/both set).
- [x] Sorting and pagination remain correct with `q` applied.
- [x] No SQL/JPQL string concatenation of user input anywhere in the query.
- [x] All 295 inherited backend tests still pass, alongside 40 new ones
      (335 total).

## Technical Approach

A single JPQL query replaces the previous two-method pair
(`findByActiveWithCategory`/`findByCategoryIdAndActiveWithCategory` and
`listActive`/`listActiveByCategory`) — see
[ADR-010](../decisions/ADR-010-keyword-search-design.md) for why a second
independent optional filter (keyword, alongside the existing category
filter) is exactly the trigger that should consolidate two methods into one
parameterized query rather than multiplying into four. The `LIKE` pattern
is built entirely in Java (`ResourceSearchQuery.toLikePattern`) —
lowercased, `\`/`%`/`_`-escaped, `%`-wrapped — and passed as a single bound
parameter; the query text's `ESCAPE '\'` clause is a fixed literal, never
user-controlled.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. `ResourceSearchQueryTest` (14 pure unit
tests covering normalization and escaping edge cases), extended
`ResourceServiceIntegrationTest` (16 new tests: matching each searchable
field, case-insensitivity, category combination, pagination, sorting,
wildcard-literal proofs, blank/over-length query handling) and
`ResourceApiIntegrationTest` (10 new HTTP-layer tests covering the same
behavior end to end, plus the OpenAPI assertion) against the real
`postgis/postgis:17-3.5` database via Testcontainers.

## Result

Completed. 40 new tests (14 unit, 16 service integration, 10 API
integration) pass alongside the 295 from Milestones 3A-5C (335 total,
authoritative per `./mvnw clean verify`).

## Related Commits

`feat: add public resource keyword search`,
`test: add keyword search and URL-state coverage`.
