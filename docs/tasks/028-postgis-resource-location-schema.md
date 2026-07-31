# Task 028: PostGIS Resource-Location Schema and Protected Write Endpoint

## Objective

Add a nullable geographic coordinate per resource, backed by a real
PostGIS `geography` column and GiST index, with an `ADMIN`/`MODERATOR`-only
endpoint to assign or replace it.

## Context

The first of two tasks completing Milestone 7A. Builds on the existing
`resources` table (V1-V6) and the `ADMIN`/`MODERATOR` authorization
pairing Milestones 5C/6B already established for resource writes.

## Scope

- `V7__add_resource_location.sql` — `resources.location
  GEOGRAPHY(POINT, 4326)`, nullable, plus
  `idx_resources_location_gist` (GiST).
- `CoordinateValidation` — shared latitude/longitude range checks.
- `ResourceLocationRequest`/`ResourceLocationResponse`/
  `ResourceLocationValidation`.
- `ResourceRepository.updateLocation` — native `@Modifying` SQL update;
  no Hibernate-mapped geometry field (see ADR-012).
- `ResourceService.replaceLocation`.
- `PUT /api/v1/resources/{id}/location`, `SecurityConfig` gating.
- OpenAPI documentation.
- [ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md).

## Out of Scope

Nearby search (Task 029), any frontend UI, address geocoding, holiday/
seasonal concerns (unrelated), Milestone 7B.

## Acceptance Criteria

- [x] `V7` adds the column and index without modifying V1-V6.
- [x] `ADMIN`/`MODERATOR` can set/replace a coordinate; `USER`/
      `ORGANIZATION` get `403`; unauthenticated gets `401`; missing
      resource returns `404`.
- [x] Missing, non-finite, or out-of-range latitude/longitude return `400
      VALIDATION_ERROR` with field-level detail.
- [x] A real, asymmetric Halifax-area coordinate round-trips through the
      write endpoint and a raw `ST_X`/`ST_Y` read without being swapped.
- [x] All 409 inherited backend tests still pass, alongside the new ones
      from this task.

## Technical Approach

`hibernate-spatial` 7.4.1.Final (matching this project's actual
`hibernate-core` version) maps through Geolatte-geom, not JTS, with no
verified compatibility precedent in this project's stack — see
[ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md) for the
full reasoning. Rather than adopt an unverified entity-mapping dependency,
`location` is never mapped on `CommunityResource` at all: the write path
is one native `@Modifying UPDATE ... SET location =
ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography`, and
the response echoes back the just-validated input coordinates directly
rather than re-querying. `ST_MakePoint(x, y)` takes longitude first,
latitude second — the opposite of the conversational order — documented
at every call site and proven with real, asymmetric test coordinates.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. `ResourceLocationValidationTest` (16 pure
unit tests: boundary values, NaN/infinity, missing fields, combined field
errors), `ResourceLocationRepositoryIntegrationTest`'s location-write
tests (migration shape, SRID/type via `geography_columns`, GiST index
presence, null-location validity, coordinate round-trip, replacement),
new `ResourceServiceIntegrationTest`/`ResourceApiIntegrationTest`
location tests, and 5 new role-matrix tests in
`AuthorizationMatrixApiIntegrationTest` — all against the real
`postgis/postgis:17-3.5` database via Testcontainers.

## Result

Completed as part of Milestone 7A's combined 73 new tests (409 → 482
total, authoritative per `./mvnw clean verify`). See Task 029 for the
nearby-search half.

## Related Commits

`build: add PostGIS resource-location schema`,
`feat: add protected resource-location management`.
