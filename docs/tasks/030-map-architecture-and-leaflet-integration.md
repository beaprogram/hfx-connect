# Task 030: Map Architecture and Leaflet Integration

## Objective

Stand up the client-only Leaflet/React Leaflet map foundation — dependency
selection, dynamic client-only loading, OpenStreetMap tiles and
attribution, the default Halifax viewport, and the `/resources`
List/Map presentation split — before any geolocation or synchronization
logic is layered on top.

## Context

The first of four tasks completing Milestone 7B. Builds directly on
Milestone 7A's `GET /api/v1/resources/nearby` (ADR-012) and establishes
the architectural pattern (client-only rendering, session state at the
`/resources` layout) every later task in this milestone depends on.

## Scope

- Dependency selection and installation — `leaflet`, `react-leaflet`,
  `react-leaflet-cluster`, `leaflet.markercluster`, and their type
  packages — with every version checked against this project's real
  `react@19.2.4`/`next@16.2.11` dependency tree via each package's own
  published `peerDependencies`, not assumed.
- `frontend/src/lib/constants/map.ts` — Halifax default centre/zoom,
  radius options, geolocation options, search-this-area threshold, nearby
  page size.
- `frontend/src/components/map/nearby-map.tsx` — the Leaflet
  `MapContainer`/`TileLayer` foundation, the default-marker-icon bundler
  fix, OpenStreetMap tile URL and attribution.
- `frontend/src/lib/map/map-search-context.tsx` — `MapSearchProvider`/
  `useMapSearch`, mounted at `frontend/src/app/resources/layout.tsx`.
- `frontend/src/components/resources/view-toggle.tsx`,
  `resource-explorer.tsx`, `map-explorer-view.tsx` — the List/Map
  top-level switch.
- [ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)
  — the full design.

## Out of Scope

Browser geolocation (Task 031), marker clustering interaction and list/map
selection sync (Task 032), search radius and "Search this area" (Task
032), automated test suite and manual verification writeup (Task 033).

## Acceptance Criteria

- [x] `react-leaflet@5.0.0`/`@react-leaflet/core@3.0.0`/
      `react-leaflet-cluster@4.1.3`/`leaflet@1.9.4` confirmed compatible
      via each package's real published `peerDependencies` before
      installation.
- [x] `npm ls leaflet react-leaflet react-leaflet-cluster
      leaflet.markercluster` shows a single deduped `leaflet` version —
      no duplicate-instance risk.
- [x] The map component loads only via `next/dynamic({ ssr: false })`
      from within an already-`"use client"` component.
- [x] A production build (`next build --webpack`) succeeds with no
      `window is not defined` or hydration-related errors.
- [x] OpenStreetMap attribution renders and is never hidden or obscured.
- [x] The default Halifax centre is used, and is never presented as the
      user's own location.
- [x] `MapSearchProvider` survives a filter-driven `/resources?...`
      navigation and a round trip to `/resources/[slug]` and back.
- [x] `ResourceListView` (Milestone 6) is unmodified in its own rendering
      logic and has zero dependency on `useMapSearch()`.

## Testing

Covered together with the rest of the milestone in Task 033
(`resource-explorer.test.tsx`, `map-search-context.test.tsx`,
`nearby-map.test.tsx`).

## Evidence

Commits on branch `milestone/07b-interactive-map`; see
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)
and `docs/milestones/milestone-07b-interactive-map.md` for the full design
rationale and acceptance criteria.
