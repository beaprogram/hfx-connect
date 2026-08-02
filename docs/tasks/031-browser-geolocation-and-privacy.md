# Task 031: Browser Geolocation and Location Privacy

## Objective

Add an explicit, user-triggered "Use my location" flow with calm,
recoverable handling of denial/timeout/unavailability, and guarantee that
no precise coordinate is ever persisted client-side or exposed with more
precision than the UI needs.

## Context

The second of four tasks completing Milestone 7B. Builds on Task 030's
`MapSearchProvider`/default-centre foundation.

## Scope

- `frontend/src/lib/map/use-geolocation.ts` — the
  idle/requesting/success/permission-denied/unavailable/timeout state
  machine wrapping `navigator.geolocation.getCurrentPosition`, called
  only from an explicit trigger.
- `frontend/src/components/map/use-my-location-control.tsx` — the
  button, status messaging (`role="status"`), and retry path.
- `MapSearchProvider.applyGeolocatedCentre` — commits a successful fix as
  the new search centre, resetting pagination and clearing any pending
  map move.
- Privacy guarantees: no coordinate ever written to `localStorage`,
  `sessionStorage`, a cookie, or the URL.

## Out of Scope

The map itself and OpenStreetMap tiles (Task 030), marker clustering and
selection sync (Task 032), search radius and "Search this area" (Task
032).

## Acceptance Criteria

- [x] Geolocation is never requested automatically — only from "Use my
      location"'s `onClick`.
- [x] `enableHighAccuracy: false`, a 10-second timeout, and a 5-minute
      `maximumAge` are used, documented with rationale.
- [x] Success recentres the map, updates the status line, resets
      pagination, and clears any pending map move.
- [x] Permission denial, timeout, and position-unavailable each show a
      distinct, calm, specific, non-alarming message; the same button
      retries.
- [x] No coordinate is ever written to `localStorage` or `sessionStorage`
      — verified by dedicated tests and by inspecting real browser
      storage after a genuine geolocation grant in a live browser
      session.
- [x] No coordinate is ever written to the URL.
- [x] No coordinate is ever intentionally logged (`console.log`) —
      `git grep`-reviewed directly.

## Testing

`use-geolocation.test.ts` (9 tests), `use-my-location-control.test.tsx`
(7 tests) — see `docs/milestones/milestone-07b-interactive-map.md`'s
"Testing" section for the full breakdown.

## Evidence

Commits on branch `milestone/07b-interactive-map`; see
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)'s
"Browser Geolocation Flow" and "Location Privacy" sections.
