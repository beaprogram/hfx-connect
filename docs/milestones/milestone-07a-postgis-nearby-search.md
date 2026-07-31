# Milestone 7A: PostGIS Resource Locations and Nearby Search

## Objective

Add the geospatial backend foundation: a protected endpoint for
`ADMIN`/`MODERATOR` accounts to assign a resource's geographic coordinate,
and a public endpoint for finding active resources near a supplied
latitude/longitude, ordered by distance, combining with every existing
public filter (`q`/`categoryId`/`costType`/`verificationStatus`/
`openNow`) — while the backend remains the sole authority for distance
calculation and pagination correctness.

## Product Value

The backend half of Milestone 7 (Geospatial Search), split into 7A
(this milestone — PostGIS locations and nearby search) and 7B (the
visual map, browser geolocation, and map/list synchronization), the same
way Milestones 3, 5, and 6 were split. Before this milestone, there was no
way to answer "what's near me" at all — a visitor had to browse or search
by name/category with no sense of actual proximity.

## Technical Scope

**Backend:**

- `V7__add_resource_location.sql` — adds a nullable
  `location GEOGRAPHY(POINT, 4326)` column to `resources`, plus a GiST
  spatial index (`idx_resources_location_gist`).
- `com.hfxconnect.resource.CoordinateValidation` — shared latitude/
  longitude range checks (finite, -90..90 / -180..180).
- `ResourceLocationRequest`/`ResourceLocationResponse`/
  `ResourceLocationValidation` — the location-write body contract, reusing
  the existing `ValidationException`/`VALIDATION_ERROR` shape.
- `PUT /api/v1/resources/{id}/location` — `ADMIN`/`MODERATOR` only,
  replaces a resource's coordinate transactionally.
- `ResourceRepository.updateLocation`/`findNearby` — native SQL (no
  Hibernate Spatial/JTS entity mapping — see ADR-012), the latter a
  closed-projection query (`NearbyResourceProjection`) combining
  `ST_DWithin`/`ST_Distance` with every existing optional filter predicate
  and the same `openNow` `EXISTS` subquery ADR-011 established, ordered
  distance-ascending with `name`/`id` tie-breakers.
- `NearbyResourceDetails`/`NearbyResourcePage`/
  `NearbyResourceSummaryResponse`/`NearbyResourcePageResponse` — the
  business-layer and HTTP-layer read models for a nearby result
  (compact summary fields plus `latitude`/`longitude`/`distanceMeters`).
- `ResourceService.replaceLocation`/`nearby` — coordinate/radius
  validation, batch-loaded operating hours reused unchanged from
  ADR-011's pattern.
- `GET /api/v1/resources/nearby` — public, `latitude`/`longitude`
  required, `radiusKm` optional (default 5 km, maximum 50 km).
- `com.hfxconnect.common.error.{InvalidLatitudeException,
  InvalidLongitudeException, InvalidRadiusException}`.
- `SecurityConfig` — `PUT /api/v1/resources/*/location` gated
  `ADMIN`/`MODERATOR`; `GET /nearby` already covered by the existing
  public `GET /api/v1/resources/**` rule.
- OpenAPI: both new endpoints fully documented, including coordinate
  order, radius limits, and the straight-line-distance disclaimer.
- [ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md) — the
  full design.
- 73 new backend tests, bringing the suite to 482 total.

**Frontend:** none — Milestone 7A is deliberately backend-only (see Out of
Scope). The existing 177 frontend tests were re-confirmed passing
unchanged; no frontend file was modified.

## Out of Scope

Visual map, browser geolocation permission, marker clustering, map/list
synchronization, map-bounds search, address geocoding, reverse geocoding,
route distance, walking/driving time estimation, transit routing, saved
resources, submissions, moderation, organizations, events, CI/CD,
deployment, Milestone 7B.

## Design Decisions

Full rationale: [ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md).
Summary:

- **`geography(Point, 4326)`, not `geometry`** — geography calculates
  distances on the actual spheroid directly in metres; no separate planar
  projection to choose and maintain for this single-city product.
- **No Hibernate Spatial/JTS entity mapping** — `hibernate-spatial`
  7.4.1.Final (matching this project's actual `hibernate-core` version)
  maps through Geolatte-geom, not JTS, with no verified compatibility
  precedent in this stack; every geospatial read/write goes through native
  SQL instead, since nearby search always needed a native query for
  `ST_DWithin`/`ST_Distance` anyway.
- **`ST_MakePoint(longitude, latitude)` everywhere** — X-then-Y, never
  swapped; proven with real, asymmetric Halifax-area test coordinates.
- **One native projection query for nearby search**, not a two-stage
  ID-then-entity fetch — the query's own `ORDER BY` is the distance order,
  preserved automatically through pagination; a second by-ID query would
  risk silently losing that order.
- **Distance in metres, straight-line only** — never route distance,
  walking time, or driving time.
- **5 km default / 50 km maximum radius** — deliberate, Halifax-scoped
  limits; coordinates themselves are never restricted to a bounding box.
- **Query-param error codes for `nearby`, `VALIDATION_ERROR` for the
  location body** — the same split ADR-011 established for
  `costType`/`verificationStatus`/`openNow` vs. the operating-hours body.
- **Search coordinates are never persisted** — read, bound into the
  query, discarded; inherently present in the URL itself (browser
  history, access logs), which is documented, not hidden.

## Security Considerations

- Nearby search remains fully public — no authentication required, and
  confirmed accessible both anonymously and while authenticated.
- Location writes require a Bearer access token for an `ADMIN` or
  `MODERATOR` account — confirmed with a full role matrix
  (unauthenticated `401`, `USER`/`ORGANIZATION` `403`, `MODERATOR`/`ADMIN`
  `200`).
- Every coordinate/radius bind parameter is passed through JDBC parameter
  binding — `git grep -n "ST_DWithin"`/`"ST_Distance"`/`"ST_MakePoint"`
  across `backend/src/main` reviewed directly; no string concatenation
  anywhere.
- Inactive resources and resources with no saved location are always
  excluded from nearby results — the same `active = true AND location IS
  NOT NULL` predicate appears in both the page query and the count query.
- Search coordinates are never written to any table, never attached to a
  user account, and never logged — `git grep`-reviewed directly.
- No persistence ID beyond the already-public resource ID is exposed from
  the nearby projection.

## Performance Review

- List requests: one native page query (with the `ST_DWithin`/`ST_Distance`
  predicates and every filter), one matching native count query, and one
  batch `findByResourceIdIn` query for operating hours — the same
  three-query shape ADR-011 already established for the non-geospatial
  listing, extended with spatial predicates rather than a new query
  layer.
- `EXPLAIN` against representative development data (via `psql`, directly
  inspected, not inferred) confirms `ST_DWithin` uses
  `idx_resources_location_gist` (`Index Scan using
  idx_resources_location_gist on resources`) — the GiST index is genuinely
  index-assisted, not a fallback sequential scan.
- No benchmark was run against production-scale data — this project's
  actual dataset is far too small for one to be meaningful, and none is
  claimed.

## Acceptance Criteria

**Database**

- [x] `V7` adds `geography(Point, 4326)`, nullable; earlier migrations
      unmodified.
- [x] GiST spatial index exists and is genuinely used by `ST_DWithin`
      (confirmed via `EXPLAIN`, not assumed).
- [x] SRID 4326 and `Point` geometry type confirmed live via
      `geography_columns`.

**Location management**

- [x] `ADMIN`/`MODERATOR` can replace a resource's coordinate; `USER`/
      `ORGANIZATION` get `403`; unauthenticated gets `401`; a missing
      resource returns `404`; invalid coordinates return `400
      VALIDATION_ERROR`.
- [x] Replacement is transactional and can correct an existing coordinate.
- [x] No public location-editing UI exists.

**Nearby search**

- [x] `latitude`/`longitude` required; missing or out-of-range values
      return their own `400` codes.
- [x] `radiusKm` defaults to 5, maximum 50; out-of-range returns `400
      INVALID_RADIUS`.
- [x] Results ordered nearest-first, with `name`/`id` tie-breakers for
      equal distances.
- [x] Resources without a saved coordinate, and inactive resources, are
      always excluded.
- [x] Combines correctly with `q`/`categoryId`/`costType`/
      `verificationStatus`/`openNow` in every combination tested.
- [x] Pagination totals stay exact — filtering happens in the database
      query, never after fetching a page.
- [x] No matches returns `200` with empty content, never `404`.

**Testing**

- [x] 482 backend tests pass (409 inherited + 73 new) — authoritative per
      `./mvnw clean verify`.
- [x] 177 frontend tests pass unchanged — authoritative per `npm test`;
      no frontend file was modified.

**Manual verification** — all performed against the real docker-compose
database and real running backend:

- [x] A resource's coordinate confirmed to persist without a latitude/
      longitude swap, using real, asymmetric Halifax-area coordinates.
- [x] Nearby search confirmed live with default and explicit radii,
      nearest-first ordering, radius exclusion, no-location exclusion,
      and every filter combination.
- [x] Invalid latitude/longitude/radius each confirmed to return their
      documented `400` code live.
- [x] The full location-write role matrix confirmed live.
- [x] `EXPLAIN` confirmed the GiST index is actually used.
- [x] OpenAPI confirmed to declare both new endpoints.
- [x] Milestone 6A/6B filters, Milestone 5C authentication/authorization,
      and Milestone 4 public browsing all reconfirmed unaffected.
- [x] Logs grepped for secrets and raw coordinate values — none found.

## Known Limitations (as of Milestone 7A)

- No visual map, browser geolocation, or map/list synchronization
  (Milestone 7B).
- No address geocoding or reverse geocoding — coordinates are entered
  directly through the protected API only.
- No route distance, walking time, driving time, or transit routing —
  straight-line geography distance only.
- No production-scale geospatial performance benchmark.
- No public UI for entering or viewing a resource's coordinate.
- A caller's search coordinates appear in the request URL itself (by
  design, for shareability) — they can appear in browser history and
  server access logs, an inherent property of a `GET`-based endpoint, not
  eliminated by this milestone.

## Risks

| Risk | Mitigation |
|---|---|
| A latitude/longitude swap anywhere in the query or write path would silently return wrong or empty results in a way that might look plausible at a glance | Every coordinate constant in this milestone's own tests uses real, asymmetric Halifax-area values (different magnitude and sign for lat vs. lon) specifically so a swap fails loudly; `ST_MakePoint(longitude, latitude)`'s parameter order is documented at every call site |
| No verified precedent for Hibernate Spatial with this project's exact Hibernate ORM 7.4.1/Spring Boot 4.1 stack could have led to a fragile, half-working entity mapping | Resolved by not adopting Hibernate Spatial at all — every geospatial operation is native SQL with a closed-projection read model, verified against the real database before building anything on top of the assumption |
| Combining `ST_DWithin` with the already-complex existing filter query (keyword/category/cost/verification/open-now) could silently break pagination totals or duplicate ADR-011's `openNow` logic incorrectly | The native `countQuery` shares the identical `WHERE` clause with the page query; the `openNow` `EXISTS` subquery is the same semantics ADR-011 already tested exhaustively, re-expressed in SQL syntax and re-verified with dedicated nearby+openNow tests |

## Completion Summary

All planned Milestone 7A deliverables were completed and verified three
ways: 482 automated backend tests (including real-coordinate swap-proof
assertions and exhaustive filter-combination coverage), a full manual
pass against the real running backend/database — including a live
`EXPLAIN` confirming the GiST index is genuinely used — and a full
regression pass confirming Milestone 6A/6B's filters and Milestone 5C's
authentication/authorization remain unaffected. The frontend was
deliberately untouched, as scoped.
