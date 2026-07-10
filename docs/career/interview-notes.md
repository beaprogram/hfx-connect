# Interview Notes

This document collects the "why" behind HFX Connect's real technical decisions, in a
form that can be reviewed before a technical interview. Entries are added only once
the corresponding decision is actually implemented — this file describes what was
built, not what is planned.

**Status: foundation only.** Milestone 1 established the product definition and the
architecture direction (see [system-overview.md](../architecture/system-overview.md)
and the ADRs in [docs/decisions/](../decisions/)), but no implementation exists yet.
The talking points below are the ones already answerable from Milestone 1's decisions;
the rest will be added as the corresponding milestone is completed.

## Answerable Now (Milestone 1)

**Why PostgreSQL with PostGIS instead of computing distances in application code?**
See [ADR-002](../decisions/ADR-002-postgresql-and-postgis.md). Short answer: PostGIS's
`ST_DWithin`/`ST_Distance` functions can use a spatial (GiST) index, so a nearby-search
query stays fast as the number of resources grows, instead of scanning and computing
distance for every active row on every request.

**Why REST instead of GraphQL?**
See [ADR-003](../decisions/ADR-003-rest-api.md). Short answer: the frontend's data
needs are a small, well-known set of screens rather than many independent clients with
divergent query shapes — the case GraphQL is built for — so REST plus TanStack Query
caching covers the need with less operational overhead, and is more directly relevant
to the backend job market this project targets.

**Why a monorepo instead of separate frontend/backend repositories?**
See [ADR-001](../decisions/ADR-001-monorepo-structure.md). Short answer: one developer,
one release cadence, and most features touch both the API and the UI together, so a
single repository keeps related changes reviewable as one unit.

## To Be Added in Later Milestones

- How DTOs protect the API boundary from the JPA entity model (Milestone 3).
- How database constraints enforce rules that also exist in application validation
  (Milestone 3).
- How authentication tokens and refresh cookies work (Milestone 5).
- How authorization is enforced server-side, independent of the frontend (Milestone 5).
- How moderation approval and audit-history writes are made transactional (Milestone
  9).
- How duplicate submissions/bookmarks are prevented at the database level (Milestones
  3, 8).
- How geographic search is kept fast as content grows (Milestone 7).
- How map performance is protected through bounds-based queries and compact marker
  payloads (Milestone 7).
- How the product stays fully usable when location permission is denied or the map
  cannot be used (Milestone 7).
- Trade-offs made and limitations knowingly deferred, updated at each milestone.
