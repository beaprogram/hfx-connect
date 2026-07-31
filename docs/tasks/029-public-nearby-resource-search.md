# Task 029: Public Nearby-Resource Search

## Objective

Add a public `GET /api/v1/resources/nearby` endpoint that finds active
resources within a caller-supplied radius of a coordinate, ordered by
distance, combining correctly with every existing public filter.

## Context

The second of two tasks completing Milestone 7A. Builds directly on Task
028's `resources.location` column and extends the same unified-query
pattern ADR-010/ADR-011 established for `ResourceRepository.search`.

## Scope

- `NearbyResourceProjection` — closed interface projection for the native
  query result.
- `NearbyResourceDetails`/`NearbyResourcePage` (business layer),
  `NearbyResourceSummaryResponse`/`NearbyResourcePageResponse` (HTTP DTOs).
- `ResourceRepository.findNearby` — native `ST_DWithin`/`ST_Distance`
  query with `categoryId`/keyword/`costType`/`verificationStatus`/
  `openNow` predicates, a matching `countQuery`, and a fixed
  distance-ascending `ORDER BY`.
- `ResourceService.nearby` — radius default/validation (5 km default, 50
  km maximum), batch-loaded operating hours reused from ADR-011's
  pattern, one Halifax "now" per request.
- `GET /api/v1/resources/nearby`, `InvalidLatitudeException`/
  `InvalidLongitudeException`/`InvalidRadiusException`.
- OpenAPI documentation, including the straight-line-distance disclaimer.
- [ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md).

## Out of Scope

Resource-location writes (Task 028), any frontend UI or map, address
geocoding, route distance/travel time, Milestone 7B.

## Acceptance Criteria

- [x] `latitude`/`longitude` required; missing or out-of-range values
      return `400 INVALID_LATITUDE`/`INVALID_LONGITUDE`.
- [x] `radiusKm` optional (default 5, maximum 50); out-of-range returns
      `400 INVALID_RADIUS`.
- [x] Results ordered nearest-first with `name`/`id` tie-breakers;
      resources without a coordinate, and inactive resources, always
      excluded.
- [x] Combines correctly with `q`/`categoryId`/`costType`/
      `verificationStatus`/`openNow` in every tested combination,
      preserving each filter's existing semantics unchanged.
- [x] Pagination totals stay exact — no post-pagination radius filtering.
- [x] No matches returns `200` with empty content, never `404`.
- [x] All 409 inherited backend tests still pass, alongside the new ones
      from this task.

## Technical Approach

One native `@Query` (with a matching native `countQuery`) returns
`Page<NearbyResourceProjection>` — the same "one unified,
independently-optional-filter query" shape ADR-010/ADR-011 established,
expressed in native SQL because `ST_DWithin`/`ST_Distance` have no JPQL
equivalent. A two-stage ID-then-entity fetch was considered and rejected:
a second by-ID query has no guaranteed row order, risking silently losing
the distance order pagination depends on — see
[ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md)'s
"Alternatives Considered". The `openNow` `EXISTS` subquery is
ADR-011's exact same-day/overnight/overnight-continuation logic,
re-expressed against `resource_operating_hours`'s raw column names.
Operating hours for the returned page are batch-loaded via the existing
`findByResourceIdIn` — nearby search does not duplicate or diverge from
that N+1-avoidance strategy.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. `ResourceLocationRepositoryIntegrationTest`'s
nearby tests (radius inclusion/exclusion, no-location exclusion,
inactive exclusion, count-matches-content, projection field mapping),
22 new `ResourceServiceIntegrationTest` tests (distance ordering, radius
default/boundaries, every filter combination, pagination, empty results),
and 17 new `ResourceApiIntegrationTest` tests (the same scenarios over
HTTP, plus OpenAPI) — all against the real `postgis/postgis:17-3.5`
database via Testcontainers, using real, asymmetric Halifax-area
coordinates chosen specifically so an ordering or coordinate-swap bug
would be immediately, obviously wrong.

## Result

Completed as part of Milestone 7A's combined 73 new tests (409 → 482
total, authoritative per `./mvnw clean verify`). Manual `EXPLAIN`
verification confirmed `ST_DWithin` uses the GiST index from Task 028
(`Index Scan using idx_resources_location_gist`), not a sequential scan.

## Related Commits

`feat: add public nearby-resource search`,
`test: add geospatial query and authorization coverage`.
