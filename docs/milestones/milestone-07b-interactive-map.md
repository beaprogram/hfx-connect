# Milestone 7B: Interactive Map, Browser Geolocation, Marker Clustering, and List/Map Synchronization

## Objective

Build the first interactive map experience on top of Milestone 7A's
stable `GET /api/v1/resources/nearby` API: a public user can browse
nearby resources on a Leaflet/OpenStreetMap map, browse the same
resources through a fully accessible list, explicitly request browser
geolocation (or use a default Halifax location without granting it),
move the map and search around a new centre, choose a search radius,
combine the map with every existing filter, see markers grouped through
clustering, and select a resource from either the list or the map with
the selection kept in sync — while the backend remains the sole
authority for nearby-resource selection, radius, distance, filtering,
pagination, and authorization.

## Product Value

The frontend half of Milestone 7 (Geospatial Search), completing the
split from Milestone 7A the same way Milestones 3, 5, and 6 were split.
Before this milestone, a visitor could only see distance-from-a-point as
a number in a list row (Milestone 7A); there was no visual sense of
*where* a resource actually is relative to anywhere else, and no way to
explore an area rather than a single fixed search origin.

## Technical Scope

**Dependencies** (`frontend/package.json`) — versions verified against
the real dependency tree, not assumed from older examples (see
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)):
`leaflet@1.9.4`, `react-leaflet@5.0.0`, `@react-leaflet/core@3.0.0`,
`react-leaflet-cluster@4.1.3`, `leaflet.markercluster@1.5.3`,
`@types/leaflet`, `@types/leaflet.markercluster`.

**State and data layer:**

- `frontend/src/lib/constants/map.ts` — `HALIFAX_DEFAULT_CENTER`,
  `RADIUS_OPTIONS_KM`/`DEFAULT_RADIUS_KM`/`MAX_RADIUS_KM`,
  `GEOLOCATION_OPTIONS`, `SEARCH_THIS_AREA_THRESHOLD_METERS`,
  `NEARBY_PAGE_SIZE`.
- `frontend/src/lib/validation/schemas.ts` —
  `nearbyResourceSummaryResponseSchema`/`nearbyResourcePageResponseSchema`
  (finite/range-checked latitude, longitude, and distance).
- `frontend/src/lib/api/resources.ts` — `getNearbyResources` — safe
  query encoding, coordinate/radius validation before any request,
  request cancellation, backend error parsing, no access token involved.
- `frontend/src/lib/query/keys.ts` — `resourceKeys.nearby` — a stable
  cache key covering every parameter that changes the result set,
  deliberately excluding `selectedResourceId`.
- `frontend/src/lib/map/map-search-context.tsx` — `MapSearchProvider`/
  `useMapSearch` — the session-scoped view/centre/radius/geolocation-
  status/selection/pagination state (see ADR-013's "State Architecture").
- `frontend/src/lib/map/use-geolocation.ts` — explicit-trigger
  `navigator.geolocation.getCurrentPosition` wrapper with a
  idle/requesting/success/permission-denied/unavailable/timeout state
  machine.
- `frontend/src/app/resources/layout.tsx` — mounts `MapSearchProvider`
  above both `/resources` and `/resources/[slug]`.

**Map and UI components:**

- `frontend/src/components/map/nearby-map.tsx` — the Leaflet map itself
  (`MapContainer`/`TileLayer`/`MarkerClusterGroup`/`Marker`/`Popup`),
  loaded only via `next/dynamic({ ssr: false })`.
- `frontend/src/components/map/use-my-location-control.tsx`,
  `radius-selector.tsx`, `search-this-area-button.tsx`,
  `nearby-pagination.tsx`.
- `frontend/src/components/resources/view-toggle.tsx`,
  `nearby-resource-card.tsx`, `nearby-map-view.tsx`,
  `map-explorer-view.tsx`, `resource-explorer.tsx`.
- `frontend/src/lib/formatting/distance.ts` — `formatDistanceAway`
  (metres under 1 km, kilometres with one decimal above).
- Additive-only changes to two existing Milestone 6 files:
  `resource-list-view.tsx` (an optional `headerActions` prop, unused by
  existing callers/tests) and `resource-filter-form.tsx` (an optional
  `showSort` prop, defaulting to `true`, hidden in Map mode since nearby
  results are always distance-ordered).
- `frontend/src/app/resources/page.tsx` — now renders `ResourceExplorer`
  instead of `ResourceListView` directly.

**Frontend tests:** 85 new tests across 12 new/extended files — see
"Testing" below. **Backend:** none — Milestone 7B consumes Milestone 7A's
existing API as-is; the 482 backend tests were reconfirmed passing
unchanged with zero backend files modified.

## Out of Scope

Address geocoding, reverse geocoding, route directions/distance/time
(walking, driving, or transit), a rectangular map-bounds search endpoint,
map-based resource-coordinate editing, user location history, saved map
preferences, offline maps, custom vector tiles, heat maps, organization
ownership, saved resources, submissions, correction reports, moderation,
events, CI/CD, deployment, Milestone 8.

## Map Experience Location

Integrated into the existing `/resources` route rather than a separate
`/map` route — one coherent experience, not duplicated filter/URL-state
logic across two pages (see ADR-013's "Two Top-Level View Components").
A `ViewToggle` (**List**/**Map**, `aria-pressed`) switches between:

- **List** — the pre-existing, unmodified Milestone 6 experience.
- **Map** — the new nearby-search experience: a desktop split layout
  (list panel + map panel, both visible at `lg` breakpoint and above) and
  a mobile Map/List sub-toggle (one panel visible at a time below `lg`,
  matching the milestone's "one primary view at a time" requirement for
  small screens).

## Progressive Enhancement

`ResourceListView` has no dependency on any map or geolocation code —
`useMapSearch()` is never called from within it. Public list browsing,
search, filtering, sorting, and pagination all work exactly as they did
before this milestone, with or without the map ever loading successfully.
A user never needs to interact with the map to reach a resource's detail
page — every list card, in both List and Map mode, links to
`/resources/[slug]` through a standard anchor element.

## Design Decisions

Full rationale:
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md).
Summary:

- **Leaflet + React Leaflet 5 + OpenStreetMap raster tiles**, every
  package version checked against this project's real
  React 19/Next.js 16 dependency tree before installation — not assumed
  from older tutorials.
- **Client-only map rendering** via `next/dynamic({ ssr: false })`,
  required because Leaflet reads browser globals at import time; this
  Next.js version's own documentation was checked directly rather than
  assumed (per `frontend/AGENTS.md`).
- **Explicit, user-triggered geolocation only** — never requested on
  page load; `enableHighAccuracy: false`, 10 s timeout, 5-minute
  `maximumAge`.
- **Session state lives in a `MapSearchProvider` mounted at the
  `/resources` layout**, not the URL or browser storage — chosen because
  a layout survives filter-driven navigations and detail-page round
  trips that a page-level state would not; `view`/`radiusKm` are
  best-effort mirrored to the URL for shareability, coordinates never
  are.
- **Two separate top-level view components** (`ResourceListView`
  unmodified, `MapExplorerView` new) rather than one branching component
  — protects the already-tested list experience from any map-mode risk.
- **"Search this area" is radius-based**, centred on the map's current
  centre point — not a rectangular viewport-bounds query; no bounds
  endpoint exists.
- **Nearby pagination is client state, not URL state** — follows
  directly from centre never being in the URL.
- **One shared `selectedResourceId`** for both list cards and map
  markers, deliberately excluded from the nearby query's cache key so
  selection changes never trigger a refetch.

## Browser Geolocation Flow

`idle → requesting → success | permission-denied | unavailable | timeout`,
driven entirely by `UseMyLocationControl`'s single button. Every non-idle
state renders calm, specific copy (never a raw browser error object) and
the same button becomes the retry action. Success recentres the map,
updates the status line ("Showing resources near your location."),
resets pagination, and clears any pending map move. Denial/timeout/
unavailability leave the current (default or manually chosen) centre and
the full list untouched and usable — never a blocking or alarming state.

## Location Privacy

- Coordinates (geolocated, default, or manually chosen) live only in
  React state — never `localStorage`, `sessionStorage`, a cookie, or the
  URL. Verified by dedicated tests and by inspecting real browser storage
  after a genuine geolocation grant in a live Chromium session.
- `view`/`radiusKm` are the only nearby-related values ever written to
  the URL.
- The backend inherently receives coordinates as `GET` query parameters
  (already documented in ADR-012); this document does not claim they are
  invisible to network/infrastructure logs — only that HFX Connect never
  deliberately persists or logs them at the application level (`git grep`
  confirms no `console.log` of a coordinate anywhere in the new frontend
  code).
- No analytics or location-tracking integration was added.

## Search Radius and Search-This-Area Behavior

A labelled, keyboard-accessible `<select>` offers 1/2/5/10/25/50 km
(the backend's own supported range — ADR-012), defaulting to 5 km.
Changing it resets pagination and preserves the current centre and every
other filter. Panning/zooming the map records a pending centre on
`moveend` only (never mid-drag, so dragging never floods the API);
"Search this area" appears once that pending centre has moved at least
50 m from the currently-searched one (filtering out programmatic-
recentre jitter) and, once activated, commits it as the new search
origin at the existing radius.

## Marker Clustering

`react-leaflet-cluster`'s `MarkerClusterGroup` wraps one real `<Marker>`
per current-page nearby result — never a client-fabricated marker, never
data outside the actual API response. Verified with real, distinctly
placed Halifax-area test resources: two genuinely close pairs correctly
clustered into "2" badges; clicking a cluster spiderfied it into
individual markers; clicking an individual marker afterward correctly
selected its matching list card. The list is documented, in the UI and
here, as the complete accessible alternative to the map.

## List and Map Synchronization

One shared `selectedResourceId`. Selecting a list card marks it visually
(a filled "Selected" badge plus a thicker border — never colour alone)
and, if its marker exists on the map, opens that marker's popup and
gently pans it into view only if it isn't already visible. Selecting a
marker does the same in reverse. Neither direction changes the nearby
query's centre or triggers a refetch (verified directly). A selection
that no longer appears in a new result page (after a filter change, a
new search, or pagination) is cleared automatically.

## Pagination Strategy

The map displays markers for, and the list displays cards for, the
current nearby-result page only (`NEARBY_PAGE_SIZE = 12`, matching the
plain list's page size) — never every matching resource fetched at once.
Pagination is client-side state (see "Design Decisions" above) with
Previous/Next controls mirroring the existing list's pagination labels.

## Responsive Design

Desktop (`lg` and above): list panel and map panel shown together,
side by side. Below `lg`: a compact Map/List segmented control shows one
panel at a time, defaulting to Map; both panels stay mounted (visibility
toggled via CSS, not conditional rendering) so the Leaflet instance is
never destroyed and recreated by switching panels on a phone. Verified
visually at 375px, 768px, 1024px, and 1440px viewport widths in a real
browser.

## Accessibility

- The map container has an accessible name (`aria-label="Map of nearby
  resources"`).
- "Use my location" and "Search this area" are standard, labelled,
  keyboard-focusable `<button>` elements — confirmed reachable and
  operable via `Tab`/`Enter` in a real browser and in tests.
- The radius selector is a labelled, keyboard-operable `<select>`.
- Geolocation status text uses `role="status"` (polite, non-interrupting)
  — permission denial/timeout/unavailability are normal, recoverable
  outcomes, not alarms.
- Selection is communicated through a visible text badge and a thicker
  border, never colour alone.
- View-toggle state uses `aria-pressed`, not colour alone.
- Every resource-detail link is a real, standard `<a>`/Next `Link` —
  reachable independent of any map interaction.
- The list is documented as the complete accessible alternative; the
  interactive map itself is not claimed to be screen-reader-equivalent.

## Testing

`npm test` (Jest + React Testing Library) — **85 new tests**, all passing
alongside the existing 177 (**262 total**, 0 failures, authoritative per
`npm test -- --ci`):

- **Nearby API client** (`resources.test.ts`, 16 new tests): coordinate
  order (not swapped), radius/page/size defaults and encoding, filter
  combination, blank/false-value omission, `AbortSignal` propagation,
  synchronous rejection of invalid coordinates/radius before any request,
  malformed-response rejection (negative distance, out-of-range
  coordinate), backend-error propagation.
- **Geolocation** (`use-geolocation.test.ts` + `use-my-location-control.test.tsx`,
  16 tests): never auto-requested, requests only on explicit trigger,
  success/denial/timeout/unavailable state coverage, retry, correct
  `getCurrentPosition` options, no `localStorage`/`sessionStorage` writes.
- **Map-search state** (`map-search-context.test.tsx`, 13 tests):
  default-centre activation, centre-preservation across re-activation,
  `applyGeolocatedCentre`/`searchThisArea`/`setRadiusKm` behavior, the
  "Search this area" distance threshold, URL mirroring (including the
  regression test for the detail-page URL-leak bug found and fixed during
  manual verification — see "Known Limitations"), no storage writes.
- **Leaflet coordination logic** (`nearby-map.test.tsx`, 7 tests, Leaflet/
  react-leaflet/react-leaflet-cluster mocked per ADR-013's testing
  guidance): one marker per result, none for an empty result set, correct
  coordinates used verbatim, popup content (name/category/distance/
  link), marker click → selection callback, `moveend` → pending-centre
  callback with no query involved.
- **List/map view coordination** (`nearby-map-view.test.tsx`, 9 tests,
  the real Leaflet component stubbed): default-Halifax status text,
  loading/empty/error states, card ↔ marker selection sync in both
  directions, selection change triggers no refetch, stale-selection
  clearing.
- **View toggle, radius selector, search-this-area button, nearby
  pagination, nearby resource card** (5 files, 24 tests): rendering,
  `aria-pressed`/`aria-current` semantics, keyboard operability, distance
  formatting, threshold-gated visibility.
- **`ResourceExplorer`** (5 tests): List renders by default with no
  nearby request made; switching to Map activates the nearby experience;
  List remains one click away; Sort is hidden in Map mode.
- **Regression**: all 177 pre-existing frontend tests pass unchanged.

`./mvnw clean verify` — **482/482 backend tests pass unchanged**
(authoritative per the Maven Surefire summary); zero backend files were
modified.

`npm run lint` / `npm run typecheck` / `npm run build` (production,
`next build --webpack`) — all clean.

## Manual Verification

Performed against the real running backend (`./mvnw spring-boot:run`,
`DB_PORT=55432`), the real docker-compose PostGIS database, and the real
Next.js dev server, using a genuine headless Chromium session (Playwright)
for reproducible, scripted checks plus direct visual screenshot review —
not claimed as more than what it is (a real browser, driven by an
automation script, is not the same as a human clicking through the UI,
though every interaction exercised was a real DOM event dispatched to
real rendered React/Leaflet output).

Test data: four real Halifax-area resources created via the protected
API (Central Library and Dalhousie University coordinate pairs placed at
genuinely close-but-distinct coordinates to exercise clustering, plus a
Toronto coordinate and a no-location resource to exercise exclusion),
cleaned up afterward.

Confirmed live:

- `/resources` list view loads and works with no location permission
  granted.
- Map view activates, defaults to central Halifax, and says so.
- OpenStreetMap attribution is visible (`.leaflet-control-attribution`
  contains "OpenStreetMap").
- Markers and clusters render from real tile/marker imagery (screenshot-
  reviewed directly).
- No hydration mismatch or `window is not defined` console errors.
- Geolocation is never requested automatically; "Use my location" is
  present but idle until clicked.
- Clicking without a granted permission shows the denial fallback
  message; the app remains fully usable.
- Granting geolocation permission (via Playwright's real permission/
  position mocking) and clicking "Use my location" recentres the map,
  updates the status text, and the resulting nearby request uses exactly
  the granted coordinates (latitude/longitude not swapped).
- Clicking a cluster spiderfies it; clicking the resulting individual
  marker selects the matching list card (screenshot-verified: card
  highlighted, "Selected" badge, marker popup open showing name/category/
  distance/cost/hours/link).
- The Cost filter applied while in Map mode correctly narrows the nearby
  result set (a `PAID`-cost resource disappeared from the list after
  filtering to `Cost=Free`).
- "View details" from a list card navigates to the resource-detail page;
  returning via the browser's back button preserves Map view
  (`aria-pressed="true"` still set).
- `localStorage`/`sessionStorage` inspected directly after a real
  geolocation grant — no access token, no coordinate value present.
- Responsive layout confirmed at 375px, 768px, 1024px, and 1440px
  viewport widths (screenshots captured and reviewed).
- `/login`, `/register` still load; `/dashboard` still redirects/protects
  when unauthenticated — Milestone 5 regressions reconfirmed.
- Backend regression: all 482 tests pass against the same running
  database used for manual verification.

## Performance Review

- The Leaflet/clustering bundle (~44 KB for the map logic chunk, ~145 KB
  including the bundled CSS) is split into its own webpack chunk,
  confirmed directly by inspecting `.next/static/chunks/` after a
  production build and grepping for Leaflet-specific class names — it is
  absent from the main application bundle and loads only once Map view
  is activated (`next/dynamic({ ssr: false })`).
- Clustering visibly reduces marker clutter at wider zoom levels
  (screenshot-confirmed: four results rendered as two cluster badges at
  the default zoom).
- "Search this area" prevents request flooding by design — `moveend`
  (not `move`/`drag`) records a pending centre locally, and no network
  request is made until the button is explicitly activated (verified
  directly: the nearby-fetch mock's call count is unchanged immediately
  after a simulated map move).
- Backend pagination (`NEARBY_PAGE_SIZE = 12`) bounds the number of
  markers rendered at once — the map never renders more markers than a
  single result page.
- Selecting a marker or list card never triggers a query refetch
  (`selectedResourceId` is excluded from the query key) — confirmed by a
  dedicated test asserting the nearby-fetch mock's call count is
  unchanged after a selection change.
- No production-scale frontend performance benchmark was run — the
  manual-verification dataset was small (four resources) by design; this
  is stated honestly rather than extrapolated.

## Security Review

- Nearby search remains public and unauthenticated by design — no access
  token appears anywhere in map-related code (`git grep`-reviewed
  directly across `frontend/src/lib/map`, `frontend/src/components/map`,
  and the nearby API client).
- Geolocation is requested only after an explicit user click — never
  automatically.
- Coordinates are never persisted client-side (`localStorage`,
  `sessionStorage`) or associated with an authenticated account from the
  frontend.
- Marker popups render only pre-validated, schema-checked resource
  fields through normal JSX text interpolation — no `innerHTML` or
  `dangerouslySetInnerHTML` anywhere in the new code (`git grep`-
  reviewed directly, zero matches).
- The OpenStreetMap attribution link uses `target="_blank"
  rel="noopener noreferrer"`.
- Access tokens remain memory-only and refresh tokens remain HttpOnly —
  unaffected, since this milestone touches no authentication code.
- Frontend view guards (`ViewToggle`, Map/List state) are UI presentation
  only — they are not, and are not described as, an authorization
  boundary; the backend's existing role checks (ADR-009) remain the sole
  authority for protected operations, none of which this milestone adds
  any new frontend UI for.

## Acceptance Criteria

**Map foundation**

- [x] Leaflet integrates successfully with the project's real
      React 19/Next.js 16 versions (verified via published
      `peerDependencies`, not assumed).
- [x] The map renders only on the client (`next/dynamic({ ssr: false })`);
      a production build succeeds with no SSR-related errors.
- [x] OpenStreetMap attribution is visible and un-obscured.
- [x] The default Halifax centre works before any location permission is
      granted.
- [x] The list remains fully available without any map interaction.
- [x] No placeholder or fabricated marker data is ever used.

**Nearby integration**

- [x] The map consumes the real `GET /resources/nearby` endpoint.
- [x] Latitude/longitude are sent in the correct order (verified with a
      real granted geolocation coordinate, live).
- [x] Radius is bounded to the backend-supported options.
- [x] Keyword/category/cost/verification/open-now filters all combine
      correctly with nearby search (verified live for Cost; verified for
      every filter in the automated test suite).
- [x] Distances come from the backend; the frontend never recalculates
      them.
- [x] The current result page is respected — no unbounded fetch-everything
      behavior.

**Geolocation**

- [x] Never requested on initial load.
- [x] "Use my location" triggers the request explicitly.
- [x] Success recentres the map and updates the status text.
- [x] Denial, timeout, and unavailability each show a calm, recoverable,
      distinct message; retry works.
- [x] Coordinates are never stored, associated with an account, or
      intentionally logged.

**Search this area**

- [x] Map movement never continuously refetches.
- [x] The button appears only after a meaningful move and disappears once
      activated.
- [x] Activating it updates the query centre, preserves filters/radius,
      and resets pagination.
- [x] The UI never claims exact rectangular-bounds search.

**Clustering**

- [x] Clusters render for genuinely close markers; a cluster click
      spiderfies into individual, selectable markers (screenshot-verified
      live).
- [x] The list remains the complete accessible alternative.

**List/map synchronization**

- [x] List and map always reflect the same result page.
- [x] Card selection updates marker state and vice versa (verified live).
- [x] Resource-detail links work from both.
- [x] Selection change never triggers a refetch.
- [x] Selection is communicated without colour alone.

**Responsive and accessibility**

- [x] Mobile List/Map sub-toggle works; desktop split layout works
      (verified at 375/768/1024/1440px).
- [x] The map has an accessible name; "Use my location"/"Search this
      area"/the radius selector are all keyboard-operable.
- [x] Permission/status messages use `role="status"`, not an alarming
      presentation.

**Testing**

- [x] 262/262 frontend tests pass (177 inherited + 85 new).
- [x] 482/482 backend tests pass unchanged.
- [x] Lint, typecheck, and production build all pass cleanly.

**Manual verification** — see "Manual Verification" above; all items
performed live against the real stack.

**Documentation**

- [x] Milestone document (this file) and
      [ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)
      complete.
- [x] Architecture, wireframe, and career docs updated (see the
      commit history on this branch).

## Known Limitations (as of Milestone 7B)

- No address geocoding or reverse geocoding.
- No route distance, walking/driving time, or transit directions —
  straight-line distance only (inherited from ADR-012).
- No rectangular viewport-bounds search — "Search this area" is
  radius-based.
- Markers represent only the current paginated nearby-result page.
- No production tile-provider contract — local development and manual
  verification both use the public OpenStreetMap tile server directly;
  a real production deployment would need a managed provider.
- No map-based location editing, saved map preferences, location
  history, or shareable precise-location links.
- No production-scale frontend performance benchmark.
- `view`/`radiusKm` URL restoration is best-effort (a one-frame default
  flash on a fresh load of a bookmarked map link) — see ADR-013's
  "Consequences".
- A real bug was found and fixed during this milestone's own manual
  verification, not by the automated suite alone: an early version of
  the `view`/`radiusKm` URL-mirroring effect leaked those parameters onto
  the resource-detail page's URL after navigating away from `/resources`.
  Fixed by scoping the effect to the `/resources` pathname exactly, with
  a dedicated regression test added afterward.

## Risks

| Risk | Mitigation |
|---|---|
| Leaflet reading `window`/`document` during server rendering could crash the entire `/resources` route, not just the map | The map component is loaded exclusively through `next/dynamic({ ssr: false })` inside an already-`"use client"` component, verified by a clean production build (`next build --webpack`) with no SSR errors |
| Session state (centre, radius, geolocation status, selection) could be silently lost across a filter-driven navigation or a detail-page visit, since both are real Next.js route transitions | State lives in a `MapSearchProvider` mounted at the shared `/resources` layout (which persists across those specific transitions, unlike page-level state), verified directly by manual browser testing (Cost filter preserved centre; browser back-navigation from a detail page preserved Map view) |
| A URL-mirroring feature meant only for shareability could leak onto unrelated pages if not scoped correctly | Exactly this happened during manual verification (caught before merge, not by automated tests alone) and was fixed by scoping the effect to the `/resources` pathname, with a regression test added that exercises a real pathname change |
| Marker selection or map movement could accidentally trigger extra network requests, defeating the "no request flood" requirement | `selectedResourceId` is excluded from the nearby query's cache key, and `moveend` only ever writes local pending-centre state — both behaviors are covered by dedicated tests asserting the fetch mock's call count is unchanged |

## Completion Summary

All planned Milestone 7B deliverables were completed and verified three
ways: 262 automated frontend tests (85 new, 177 inherited unchanged) plus
482 unchanged backend tests, a full manual pass against the real running
stack using a genuine headless-Chromium session with real geolocation
permission/position mocking (including direct visual screenshot review at
four viewport widths), and a full regression pass confirming Milestone
5's authentication pages and Milestone 6's filters remain unaffected. One
real bug (a URL-mirroring leak onto the resource-detail page) was found
during manual verification, fixed, and covered by a new regression test
before this branch was pushed.
