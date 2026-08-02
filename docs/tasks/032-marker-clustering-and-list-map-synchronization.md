# Task 032: Marker Clustering, Search Radius, and List/Map Synchronization

## Objective

Combine nearby search with clustering, a search-radius control,
"Search this area", and full list/map selection synchronization, all
integrated with the existing keyword/category/cost/verification/open-now
filters.

## Context

The third of four tasks completing Milestone 7B. Builds on Task 030's map
foundation and Task 031's geolocation flow.

## Scope

- `frontend/src/lib/api/resources.ts` — `getNearbyResources`.
- `frontend/src/lib/validation/schemas.ts` —
  `nearbyResourceSummaryResponseSchema`/`nearbyResourcePageResponseSchema`.
- `frontend/src/lib/query/keys.ts` — `resourceKeys.nearby`, deliberately
  excluding `selectedResourceId`.
- `frontend/src/components/resources/nearby-map-view.tsx` — the query,
  loading/empty/error states, and mobile Map/List sub-toggle.
- `frontend/src/components/resources/nearby-resource-card.tsx` — the
  distance-aware, selectable list card.
- `frontend/src/components/map/radius-selector.tsx`,
  `search-this-area-button.tsx`, `nearby-pagination.tsx`.
- `MarkerClusterGroup` integration and per-marker selection/popup logic in
  `nearby-map.tsx`.
- Additive `showSort` prop on `ResourceFilterForm` (hidden in Map mode).

## Out of Scope

The map foundation and default centre (Task 030), geolocation itself
(Task 031), a rectangular bounds-search endpoint (out of scope for the
whole milestone).

## Acceptance Criteria

- [x] Radius selector offers the backend-supported 1/2/5/10/25/50 km
      range, defaulting to 5 km; changing it resets pagination.
- [x] Map movement never continuously refetches — only `moveend` records
      a pending centre; "Search this area" appears only past a 50 m
      threshold and commits the pending centre on activation.
- [x] Keyword/category/cost/verification/open-now filters all combine
      correctly with nearby search, preserving centre/radius/view and
      resetting pagination.
- [x] Clusters render for genuinely close markers; a cluster click
      spiderfies into individual, selectable markers.
- [x] Selecting a list card selects the matching marker and vice versa,
      through one shared `selectedResourceId`.
- [x] A selection change never triggers a nearby-query refetch.
- [x] A selection that no longer exists in a new result page is cleared.
- [x] The map and list always reflect the same current result page —
      never a client-fabricated marker.

## Testing

`nearby-map.test.tsx` (7 tests, Leaflet mocked per ADR-013),
`nearby-map-view.test.tsx` (9 tests), `radius-selector.test.tsx` (3),
`search-this-area-button.test.tsx` (3), `nearby-pagination.test.tsx` (3),
`nearby-resource-card.test.tsx` (5) — see the milestone document's
"Testing" section for the full breakdown, plus live manual verification
of clustering/spiderfy/selection in a real browser.

## Evidence

Commits on branch `milestone/07b-interactive-map`; see
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)'s
"Search This Area", "Marker Clustering", and "List/Map Selection
Synchronization" sections.
