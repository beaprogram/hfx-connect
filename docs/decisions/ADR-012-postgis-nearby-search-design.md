# ADR-012: PostGIS Resource Locations and Nearby-Search Design

## Status

Accepted — 2026-07-30

## Context

Milestone 7A adds a geographic coordinate per resource and a public
"resources near this point" search, on top of Milestone 6's keyword/
category/cost/verification/open-now filtering. Several decisions need
making before writing code:

- Whether to map the new spatial column onto `CommunityResource` as a
  Hibernate-managed Java geometry type, or to keep it outside the entity
  and operate on it through native SQL.
- `geography` vs `geometry` column type, and the SRID.
- How nearby search combines with every existing filter without breaking
  pagination correctness or reintroducing N+1.
- Distance units and rounding.
- Radius default/maximum, deliberately scoped to this product's
  Halifax-only reality.
- What happens to a caller's search coordinates — whether they're ever
  persisted or logged.

## Decision

### `geography(Point, 4326)`, Not `geometry`

`resources.location` is `GEOGRAPHY(POINT, 4326)`. `geography` (not
`geometry`) is deliberate: PostGIS's geography type calculates distances
on the actual spheroid (accounting for the Earth's curvature) using
`ST_Distance`/`ST_DWithin` directly in metres, with no separate
projection/reprojection step the application would otherwise have to get
right. `geometry` would require picking and maintaining a planar
projection appropriate for Halifax's latitude to get correct distances —
solving a problem `geography` already solves correctly out of the box, for
a single-city-scale product where the extra complexity has no payoff. SRID
4326 (WGS 84, the standard latitude/longitude coordinate reference system,
the same one GPS and virtually every public coordinate source use) is the
only sensible choice for public-facing lat/lng input — anything else would
require a caller to already know this project's internal SRID choice.

### No Hibernate Spatial / JTS Entity Mapping — Native SQL Only

`CommunityResource` gains **no new field** for `location`. Every read and
write of the geography column goes through a dedicated native SQL query on
`ResourceRepository`, never through the JPA entity.

This was a deliberate choice, not an oversight. `hibernate-spatial`
7.4.1.Final (the version matching this project's actual `hibernate-core`
7.4.1.Final, confirmed via `hibernate-spatial`'s own published POM) maps
Java geometries through **`org.geolatte.geom.Geometry`**, not
`org.locationtech.jts.geom.Geometry` — JTS is not even a declared
dependency of this Hibernate Spatial version's POM. Adopting it would mean
either learning and mapping through Geolatte-geom (a second geometry
library, with its own conversion story, additional to whatever a future
milestone's actual geospatial needs turn out to require) or bolting on a
JTS integration whose compatibility with Hibernate ORM 7.4.1 / Spring Boot
4.1 has no verified precedent anywhere in this project's dependency
history — exactly the kind of "copy an older example without checking
compatibility" risk the milestone brief warns against.

Meanwhile, nearby search was always going to need a native query — the
distance calculation and the `ST_DWithin` radius predicate are PostGIS
functions with no JPQL equivalent — so an entity-mapped `Point` field would
only ever have been used for the simple "read/write this resource's own
coordinates" path. That path is fully served by:

- **Write**: a `@Modifying` native `UPDATE ... SET location =
  ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography`.
- **Read-back**: the just-validated input coordinates are returned
  directly in the response — no re-query needed, since the write already
  proves what was stored.
- **Nearby search**: a native `SELECT` returning a closed interface
  projection (`NearbyResourceProjection`) with plain `Double`
  latitude/longitude/distance fields, no geometry type touching Java code
  at all.

Every public-facing coordinate in this codebase is a plain
`Double`/`double` — there is no Geolatte, no JTS, no WKT anywhere in the
DTOs or the service layer. `Hibernate.ddl-auto=validate` (this project's
existing schema-authority rule) does not require every table column to be
entity-mapped — it only validates the columns an entity *does* map — so
leaving `location` unmapped on `CommunityResource` is fully compatible
with that rule, not a workaround for it.

### Coordinate Order: `ST_MakePoint(longitude, latitude)`

PostGIS's `ST_MakePoint(x, y)` takes X first, and in a geographic
coordinate system X is longitude, Y is latitude — the opposite of the
conversational "latitude, longitude" order most people say out loud. Every
query in this codebase passes `longitude` before `latitude` to
`ST_MakePoint`, and every DTO field is named explicitly (`latitude`,
`longitude`, never a positional pair) specifically so this can never be
silently swapped. A dedicated integration test uses real, asymmetric
Halifax-area coordinates (latitude ~44.6, longitude ~-63.6 — different
enough in magnitude and sign that a swap would be immediately, obviously
wrong) rather than a coincidentally-symmetric test value that could hide a
swap bug.

### Nearby Query Strategy: One Native Projection Query, Batch-Loaded Hours

`ResourceRepository.findNearby(...)` is one native `@Query` (with a
matching native `countQuery`) returning `Page<NearbyResourceProjection>` —
a closed interface projection exposing exactly the compact
`ResourceSummaryResponse`-equivalent fields plus `latitude`, `longitude`,
and `distanceMeters`. This mirrors ADR-010/ADR-011's established pattern
(one parameterized query, `(:param IS NULL OR ...)` predicates for every
independently optional filter) translated into native SQL syntax, with the
same `EXISTS` subquery ADR-011 already established for `openNow`
re-expressed against the raw `resource_operating_hours` table (identical
semantics, identical bind parameters, just SQL column names instead of
JPQL property names). Operating-hours rows for the returned page are then
batch-loaded exactly the same way `ResourceService.search` already does
(`ResourceOperatingHoursRepository.findByResourceIdIn`) — nearby search
does not duplicate or diverge from that N+1-avoidance strategy.

The `ORDER BY` (`ST_Distance(...) ASC, r.name ASC, r.id ASC`) is a fixed
literal in the query text, never influenced by a caller-supplied `sort`
parameter — nearby search has exactly one meaningful order (nearest
first), with `name` then `id` as deterministic tie-breakers for equal-
distance rows (avoiding PostgreSQL's otherwise-unspecified ordering for
ties, which could otherwise make page 2 inconsistent with page 1 across
requests).

**Alternative considered and rejected**: fetching a page of resource IDs
ordered by distance first, then a second query loading full entities by ID
(a "two-stage" approach the brief itself suggested as one option). Rejected
because it adds a second round-trip and — more importantly — creates a
real risk of the second query silently re-ordering results by ID (a
`WHERE id IN (...)` query has no guaranteed row order unless an explicit
`ORDER BY` re-derives it), which is exactly the kind of bug the brief
explicitly warns against ("do not accidentally reorder results by ID").
The single-projection-query approach has no such risk: the distance order
*is* the query's own `ORDER BY`, preserved automatically through
pagination.

### Distance in Metres, Never a Travel Estimate

`distanceMeters` is `ST_Distance`'s own geography-mode output — straight-
line ("as the crow flies") distance over the WGS84 spheroid, in metres,
full `double` precision (no server-side rounding; a caller wanting a
rounded display value rounds it there). Documented explicitly, everywhere
it appears, as straight-line geographic distance — never route distance,
walking time, or driving time, none of which this milestone calculates or
claims to.

### Radius: 5 km Default, 50 km Maximum

Both deliberate, documented, Halifax-scoped choices, not arbitrary limits
applied to reject valid coordinates. 5 km covers most of the Halifax
Regional Municipality's urban core from a central point; 50 km comfortably
covers the entire HRM (Halifax to the far edges of Dartmouth/Bedford/
Sackville and beyond) without allowing an effectively-unbounded search that
would defeat the purpose of a "nearby" endpoint or make `ST_DWithin`'s
index usage pointlessly expensive. A request above the maximum returns
`400 INVALID_RADIUS` rather than silently clamping to 50 km — silent
clamping would make a caller's own request and the server's actual
behavior diverge without any signal. Latitude/longitude themselves are
**not** restricted to a Halifax bounding box — a resource (or a search
origin) outside Halifax is still a structurally valid coordinate; only the
*radius* is scoped to this product's actual current service area.

### Error Codes: Query-Param Codes for `nearby`, `VALIDATION_ERROR` for the Location Body

Continues the exact split ADR-011 already established: a single bad query
parameter on a `GET` gets its own named code (`INVALID_LATITUDE`,
`INVALID_LONGITUDE`, `INVALID_RADIUS` — matching `INVALID_COST_TYPE`/
`INVALID_VERIFICATION_STATUS`/`INVALID_OPEN_NOW_FILTER`'s precedent),
while the `PUT .../location` request *body*'s coordinate validation reuses
the existing `ValidationException`/`VALIDATION_ERROR` shape with
`fieldErrors` — the same body-validation shape every other write endpoint
in this codebase already uses, for the same reason ADR-011 gave: every
violation is already expressible as a named field error, and a second
body-validation error shape would be inconsistency without benefit.

### Privacy: Search Coordinates Are Never Persisted

A caller's `latitude`/`longitude` on `GET /resources/nearby` are read,
bound into the query, and discarded — never written to any table, never
attached to a user account, never included in a refresh session or any
analytics record this project has. They are, however, unavoidably present
in the request URL itself (a `GET` endpoint, by design, so results are
shareable/cacheable/bookmarkable and work without JavaScript) — meaning
they can appear in browser history and in the web server's own access
logs, an inherent property of URL query parameters this ADR does not
pretend to eliminate. Application-level logging never logs raw coordinate
values at `INFO` (or any level) — the only place a coordinate value exists
in this codebase is as a bound SQL parameter, never a log statement
argument.

## Alternatives Considered

- **Hibernate Spatial + JTS entity mapping.** Rejected — see "No Hibernate
  Spatial / JTS Entity Mapping" above; no verified compatibility with this
  project's actual Hibernate ORM 7.4.1/Spring Boot 4.1 stack, and no real
  benefit once nearby search already requires native SQL.
- **Two-stage ID-then-entity nearby query.** Rejected — real risk of
  losing distance order on the second query; see "Nearby Query Strategy"
  above.
- **KNN (`<->`) index-accelerated ordering** instead of `ORDER BY
  ST_Distance(...)`. Considered, not adopted: `<->` changes the exact
  distance semantics subtly and adds a second spatial-operator concept to
  reason about for a dataset this small; recorded as a documented, honest
  future optimization if real production-scale latency ever justifies it —
  not adopted speculatively now.
- **Restricting all resource coordinates to a Halifax bounding box.**
  Rejected — no documented product rule requires it, and the milestone
  brief explicitly warns against it; only the nearby *radius* is
  Halifax-scoped.

## Consequences

- Every geospatial read/write in this codebase is native SQL, reviewable
  in one place (`ResourceRepository`'s spatial methods) rather than spread
  across entity mapping configuration and query code.
- A future milestone needing richer geometry support (polygons, routes,
  multi-point features) will need to actually introduce a geometry
  library then — this ADR's narrow scope (one nullable point per resource)
  deliberately does not build that flexibility in early.
- The nearby query is now the second-most-complex query in the codebase
  (after the already-extended `ResourceRepository.search`) — if a third
  independent geospatial concern arrives, this ADR's "Alternatives
  Considered" section is the documented trigger for revisiting the
  strategy, not something to rediscover from scratch.

## Honest Limitations Not Solved By This ADR

- No visual map, browser geolocation, or map/list synchronization
  (Milestone 7B).
- No address geocoding or reverse geocoding — coordinates are entered
  directly (decimal degrees) through the protected API only.
- No route distance, walking time, driving time, or transit routing —
  straight-line geography distance only.
- No per-resource timezone (unrelated to this ADR, but still true —
  `America/Halifax` remains fixed per ADR-011).
- No production-scale geospatial performance benchmark — this project's
  actual dataset is far too small for one to be meaningful yet.
