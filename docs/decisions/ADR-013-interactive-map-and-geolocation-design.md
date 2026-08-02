# ADR-013: Interactive Map, Browser Geolocation, and List/Map State Design

## Status

Accepted — 2026-08-02

## Context

Milestone 7B builds the first interactive map experience on top of
Milestone 7A's stable `GET /api/v1/resources/nearby` API (see ADR-012).
Several decisions need making before writing code:

- Which mapping library and tile provider, and whether either is actually
  compatible with this project's real React 19 / Next.js 16 App Router
  setup, not assumed from older examples.
- How to render a browser-only library (Leaflet reads `window`/`document`
  at import time) inside a framework that server-renders by default.
- When browser geolocation may be requested, and how denial/timeout/
  unavailability are handled without ever making it a hard requirement.
- Where search centre, radius, geolocation status, and the selected
  resource live — the URL, component state, or something else — given
  that a filter change already causes a full `/resources?...` navigation
  (Milestone 6A/6B) and a coordinate must never appear in that URL.
- How the map and list stay perfectly in sync without duplicating result
  data or creating feedback loops between marker and card selection.
- What happens to a user's precise coordinates: whether they are ever
  persisted, stored client-side, or exposed with more precision than the
  UI needs to show.

## Decision

### Leaflet + React Leaflet + OpenStreetMap Raster Tiles, Versions Verified Against This Project's Actual Dependencies

Checked the real dependency tree before installing anything:
`next@16.2.11`, `react@19.2.4`, `react-dom@19.2.4` (`frontend/package.json`).
Rather than assume compatibility from older React Leaflet tutorials (many
target React Leaflet v3/v4, which do not declare React 19 support),
fetched each candidate package's own published `peerDependencies` from
the npm registry directly:

- `react-leaflet@5.0.0` declares `leaflet: ^1.9.0`, `react: ^19.0.0`,
  `react-dom: ^19.0.0` — an exact match.
- `@react-leaflet/core@3.0.0` (react-leaflet's own internal dependency)
  matches the same range.
- `react-leaflet-cluster@4.1.3` (last published 2026-03-31 — actively
  maintained, not abandoned) declares `react-leaflet: ^5.0.0`,
  `react: ^19.0.0`, `react-dom: ^19.0.0`, `leaflet: ^1.9.0`,
  `@react-leaflet/core: ^3.0.0` — again an exact match, and it wraps the
  well-established `leaflet.markercluster@1.5.3` plugin rather than
  reimplementing clustering.

No version above was installed until its `peerDependencies` were checked
this way. `npm ls leaflet react-leaflet react-leaflet-cluster
leaflet.markercluster` after installation shows a single deduped
`leaflet@1.9.4` throughout — no duplicate-Leaflet-instance risk (a common
failure mode when a clustering plugin pulls in its own copy).

OpenStreetMap's standard raster tile server
(`https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`) is used directly:
no API key required for local development, matching the milestone's
explicit constraint. The required attribution
(`© OpenStreetMap contributors`, linked to
`openstreetmap.org/copyright`) is rendered by Leaflet's own attribution
control (`TileLayer`'s `attribution` prop) — never removed, hidden, or
obscured. **This is documented honestly as a local-development
configuration**: OpenStreetMap's public tile server is not an unlimited
production CDN and has a documented usage policy; a real production
deployment of HFX Connect would need a managed tile provider (e.g.
MapTiler, Mapbox, Stadia Maps, or a self-hosted tile server) once traffic
exceeds OSM's fair-use tolerance. No such provider is configured or
implied by this milestone.

### Client-Only Map Rendering via `next/dynamic`

Leaflet touches `window`/`document`/`navigator` at module-evaluation
time, so the actual `NearbyMap` component
(`frontend/src/components/map/nearby-map.tsx`) must never execute during
server rendering. It is loaded via:

```ts
const NearbyMap = dynamic(() => import("@/components/map/nearby-map").then((mod) => mod.NearbyMap), {
  ssr: false,
  loading: () => <MapLoadingPlaceholder />,
});
```

placed inside `NearbyMapView` — already a Client Component. This matters
under this Next.js version specifically: `ssr: false` is only accepted
inside a Client Component; using it from a Server Component throws a
build-time error (confirmed against
`node_modules/next/dist/docs/01-app/02-guides/lazy-loading.md`, per this
repository's own `frontend/AGENTS.md` warning not to assume this Next.js
version's behavior from training data). This also keeps Leaflet's ~44 KB
JS chunk and its CSS entirely out of the initial page bundle — it loads
only once a user activates Map view (see "Performance Review" in the
milestone document).

### Leaflet's Default-Marker Assets Under a Bundler

Leaflet's built-in default icon computes its image URLs relative to the
Leaflet CSS file's own location, which breaks under any JS bundler
(webpack, Turbopack, or otherwise) — not a Next.js-specific issue, but
one every Leaflet-in-a-bundler integration hits. The standard fix,
applied once at module load in `nearby-map.tsx`, is followed here:
`delete L.Icon.Default.prototype._getIconUrl`, then
`L.Icon.Default.mergeOptions(...)` pointed at the marker images imported
directly (`import markerIcon from "leaflet/dist/images/marker-icon.png"`,
etc.). This project's static-image-import loader returns a
`StaticImageData` object (`{ src, height, width }`) rather than a raw
string, so a small `resolveAssetUrl` helper normalizes either shape — a
detail specific to this Next.js version's asset pipeline, verified by
running a real production build (`next build --webpack`) and confirming
marker icons render (see manual verification).

### Explicit, User-Triggered Geolocation Only

`navigator.geolocation.getCurrentPosition` is called from exactly one
place — `useGeolocation().requestLocation()` — and that function is
called from exactly one place — the "Use my location" button's
`onClick`. Nothing calls it on mount, in an effect with no user gesture,
or automatically after a filter change. Options
(`frontend/src/lib/constants/map.ts`):

```ts
{ enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 }
```

`enableHighAccuracy: false` because this product needs a
neighbourhood-scale search origin, not GPS-grade precision — high
accuracy costs more battery and time for no visible benefit here.
`timeout: 10s` gives a slow fix a fair chance without an indefinite wait.
`maximumAge: 5 minutes` allows a recently cached position to be reused.

The hook exposes a small state machine — `idle | requesting | success |
permission-denied | unavailable | timeout` — and `UseMyLocationControl`
renders calm, specific, recoverable copy for every non-idle state (never
a raw `GeolocationPositionError` object), with the same button doubling
as an explicit retry action after any failure. Denial is never treated as
an application error requiring a red/alarming presentation — it's an
expected, normal outcome that leaves the default-or-manual map centre and
the full list fully usable.

### State Architecture: `MapSearchProvider` at the `/resources` Layout, Not the URL or Storage

The hardest design question was **where session state lives** — search
centre, radius, geolocation status, pending map centre, selected
resource, nearby pagination — given three constraints pulling in
different directions:

1. **Privacy** (see below): the current search centre must never be
   written to the URL, `localStorage`, or `sessionStorage`.
2. **Continuity**: switching List ↔ Map, changing a keyword/category/
   cost/verification/open-now filter (which navigates to a new
   `/resources?...` URL — existing Milestone 6A/6B behavior, unaware of
   any of this), and visiting a resource's detail page and returning must
   all preserve centre, radius, geolocation status, and selection.
3. **No extra dependency**: this milestone doesn't warrant introducing
   Redux/Zustand/Jotai for a handful of session-scoped values.

A `React.createContext`-based `MapSearchProvider`
(`frontend/src/lib/map/map-search-context.tsx`) is mounted once at
`frontend/src/app/resources/layout.tsx` — a **layout**, not the page.
Next.js App Router layouts persist across sibling-route navigations
within them; pages do not. Because this layout wraps both
`app/resources/page.tsx` (the list/map explorer) and
`app/resources/[slug]/page.tsx` (resource detail), the provider survives:

- A filter change (`ResourceFilterForm`'s existing `router.push` to a new
  `/resources?...` URL) — confirmed by a dedicated test and by manual
  verification (Cost filter applied while in Map mode kept the existing
  search centre).
- Visiting a resource's detail page and clicking back — confirmed by
  manual verification (`aria-pressed="true"` on **Map** persisted after
  a real `goBack()`).

`view` (List/Map) and `radiusKm` are **best-effort mirrored to the URL**
(read once on mount, written via `history.replaceState` on change) purely
so the current presentation is shareable/bookmarkable — never the
authoritative source powering re-renders, and **only while the pathname
is exactly `/resources`**. An early version of this mirroring effect kept
re-firing after navigating to `/resources/[slug]`, leaking
`?view=map&radiusKm=5` onto the resource-detail page's own URL; this was
caught during manual browser verification (not by the automated test
suite, which had mocked `usePathname` to a fixed value) and fixed by
guarding the effect on `pathname === "/resources"`, with a regression
test added afterward that exercises a real pathname change.

`centre`, `pendingCentre`, and `selectedResourceId` are **pure
`useState`, never mirrored anywhere** — see "Location Privacy" below.

### Two Top-Level View Components, Not One Branching Component

`ResourceExplorer` renders either the pre-existing, already-tested
`ResourceListView` (Milestone 6, completely unmodified in its rendering
logic — only an additive, optional `headerActions` prop was added so
`ViewToggle` can sit beside its heading) or the new `MapExplorerView`,
never both, and never a single component with an internal `view === ...`
branch threaded through its JSX. This was deliberate: `ResourceListView`
already has full test coverage and is the guaranteed working fallback the
milestone requires ("a user must never need to operate the map to open a
resource"); rewriting it to also understand map mode would have put that
guarantee at risk for a first implementation. `useMapSearch()` is never
called from inside `ResourceListView` itself, specifically so its
existing 20+ tests — which render it with no `MapSearchProvider` — keep
passing unmodified.

### Nearby Query Strategy, Pagination, and Filter Integration

`NearbyMapView` builds one `useQuery` keyed by `resourceKeys.nearby(...)`
— latitude/longitude (rounded to 5 decimal places, ~1.1 m, purely for
cache-key stability against floating-point noise; never for the request
itself), `radiusKm`, `page`, `size`, and every filter. Changing any filter,
the radius, or the centre resets `nearbyPage` to 0 (one `useEffect`
keyed on a serialized filter string plus centre/radius). **Selection is
deliberately excluded from the query key** — `selectedResourceId` is UI
state, not server-state identity; changing it must never trigger a
network request (verified directly: a dedicated test asserts
`getNearbyResources`'s mock call count is unchanged after a selection
change).

Nearby pagination is **client state (`nearbyPage` in the context), not
URL state** — unlike the plain list's page, which is a `Link`-driven URL
parameter (Milestone 6). This follows directly from centre never being in
the URL: if page were the only nearby-related URL parameter, a shared/
reloaded link with `?page=1` but no centre would be meaningless. The map
displays exactly the current page's results (`NEARBY_PAGE_SIZE = 12`,
matching the plain list's page size) — never every matching resource
fetched at once; the result-count line ("12 of 40 resources within 5 km"
would read, though only small manual-verification datasets were actually
exercised) makes the current subset explicit.

List mode and Map mode are a **deliberate behavioral split**, matching
the milestone brief's own suggested design: **List** is the plain,
existing, non-geospatial browsing experience — no search centre involved
at all. **Map** activates nearby search, defaulting to central Halifax
the first time it's opened in a session. Returning to List does not
discard the chosen centre/radius (re-activating Map later resumes where
the user left off, within the same page-load session).

### Search This Area: Radius-Based, Not a Bounds Query

The map records a `pendingCentre` on every Leaflet `moveend` event
(fires once, after a pan/zoom gesture settles — never mid-drag, so
dragging the map never floods the API). "Search this area" appears only
once `pendingCentre` is at least `SEARCH_THIS_AREA_THRESHOLD_METERS`
(50 m, Haversine) from the currently-searched centre — filtering out
sub-metre jitter from a programmatic recentre (e.g. "Use my location")
registering as a spurious pending move; this was verified directly with
a dedicated test. Activating it commits `pendingCentre` as the new
`centre` (triggering a fresh nearby query at the existing radius) and
resets pagination. **This searches around the map's centre point using
the selected radius — not the rectangular viewport** — the underlying
`GET /resources/nearby` endpoint (ADR-012) is radius-based; no bounds
endpoint exists, and none was added for this milestone. The UI never
claims otherwise.

### List/Map Selection Synchronization

One `selectedResourceId` (context state) is shared by
`NearbyResourceCard` and the map's markers — never two separate
selection states. Selecting a card calls `setSelectedResourceId`
directly. Selecting a marker (`Marker`'s `eventHandlers.click`) calls the
same setter via a prop. A small `useEffect` inside each `ResourceMarker`
opens that marker's popup and gently `panTo`s it into view *only* when it
is not already within the visible bounds, whenever `selectedResourceId`
matches that marker — this is one-directional (selection → marker focus),
so there is no loop where focusing a marker could itself trigger another
selection change. A selection that no longer exists in the current
result page (after a new search, filter change, or pagination) is
cleared rather than left pointing at stale data.

### Marker Clustering

`react-leaflet-cluster`'s `MarkerClusterGroup` wraps one `Marker` per
current-page nearby result — never a client-computed subset, never data
absent from the actual API response. Manual verification with four real
Halifax-area resources (two pairs at genuinely close coordinates)
confirmed clusters render correctly (two clusters of "2"), a cluster
click spiderfies into individual markers, and clicking an individual
marker post-spiderfy correctly selects its matching list card. The list
is documented, throughout the UI and here, as the complete accessible
alternative — clusters are a visual convenience, never claimed as a
screen-reader-equivalent interaction.

## Location Privacy

- The caller's current coordinates (whether from geolocation, the
  Halifax default, or a manual map move) live only in
  `MapSearchProvider`'s React state — never `localStorage`, never
  `sessionStorage`, never the URL, never a cookie. Confirmed by dedicated
  tests (`map-search-context.test.tsx`, `use-my-location-control.test.tsx`)
  and by a real-browser check (`window.localStorage`/`sessionStorage`
  dumped and grepped for the granted test coordinate — none found).
- `view`/`radiusKm` are the only nearby-search-related values ever
  written to the URL; latitude/longitude never are, by construction (the
  mirroring effect only ever sets `view`/`radiusKm` search params).
- The backend's `GET /resources/nearby` necessarily receives coordinates
  as request query parameters (ADR-012 already documents this
  server-side). **This is not perfect privacy** — the request URL,
  including the coordinate, can appear in browser network-request logs,
  proxy/CDN access logs, or backend infrastructure logs, exactly as any
  other query-string value would. HFX Connect does not deliberately
  persist coordinates at the application level (no database column, no
  analytics event, no intentional log statement), but this document does
  not claim the coordinate is invisible to every layer of the stack.
- No analytics or location-tracking integration exists or was added.
- Displayed precision is deliberately low: the UI never renders the
  caller's raw coordinate as text — only derived, low-precision
  statements ("Showing resources near your location.", "506 m away").

## Alternatives Considered

- **A separate `/map` route.** Rejected: it would either duplicate every
  filter control and its URL-state handling, or require lifting filter
  state above both routes anyway — at which point the chosen
  single-route, context-backed design already solves the same problem
  more simply. `/resources` also already owns the filter form and
  category data-fetching this milestone reuses as-is.
- **A commercial mapping SDK (Google Maps, Mapbox GL).** Rejected per the
  milestone's explicit constraint and because Leaflet + OpenStreetMap
  needs no API key for local development, keeping the setup story
  identical to every other part of this project's local dev experience.
- **Storing the map-move-driven "pending centre" or the committed search
  centre in the URL for shareable links.** Rejected for this milestone —
  privacy is prioritized over shareable precise-location state, per the
  milestone brief's own explicit preference. A future milestone could add
  an explicit, user-initiated "share this search" action that rounds
  coordinates deliberately and says so, but nothing does that today.
- **Fetching every matching nearby resource at once instead of paging.**
  Rejected: this is exactly the "download everything and filter in the
  browser" anti-pattern the milestone brief explicitly forbids, and it
  would make marker count unbounded for a popular search area.
- **A custom-written clustering algorithm.** Rejected in favor of the
  maintained `react-leaflet-cluster`/`leaflet.markercluster` combination —
  no reason to hand-roll spatial clustering for a well-solved problem.

## Consequences

- The map is genuinely optional at the code level: `ResourceListView` has
  zero dependency on any map/geolocation code, so a Leaflet failure (a
  tile CDN outage, a client-side exception in the dynamically-loaded
  chunk) cannot break resource discovery — the List view remains fully
  functional regardless.
- `view`/`radiusKm` URL restoration is best-effort, not authoritative:
  because it reads `window.location` inside a `useEffect` (to avoid a
  server/client hydration mismatch — see the code comment in
  `map-search-context.tsx`), there is a one-frame flash of the default
  List view on a fresh load of a bookmarked `?view=map` link before the
  effect corrects it. Accepted as a reasonable trade-off for a first
  implementation rather than a more involved `Suspense`-based
  server-aware restoration.
- Geolocation status (the *hook's* transient `idle/requesting/success/…`
  state) is intentionally **not** preserved across a detail-page round
  trip — only the *durable* outcome (the committed centre, stored in
  context) is. After returning from a resource's detail page, "Use my
  location" correctly reads "Use my location" again, not "Update my
  location", because the underlying request is no longer "in progress."
  This was a deliberate choice once identified during manual
  verification, not an oversight.
- Every marker on the map is a full, real `<Marker>`/`<Popup>` pair from
  the current result page — there is no virtualization. At this
  milestone's real-world scale (a single city, a capped page size), this
  is not a performance concern; a future milestone with materially larger
  per-page marker counts might need to revisit this.

## Honest Limitations Not Solved By This ADR

- No address geocoding or reverse geocoding — "Use my location" and
  "Search this area" are the only ways to move the search origin.
- No route distance, walking/driving/transit time, or turn-by-turn
  directions — `distanceMeters` remains straight-line only (ADR-012).
- No rectangular viewport-bounds search — "Search this area" is
  radius-based, centred on the map's current centre point.
- No production tile-provider contract — local development and this
  milestone's manual verification both use the public OpenStreetMap tile
  server directly; see "Leaflet + React Leaflet + OpenStreetMap Raster
  Tiles" above.
- No saved map preferences, location history, or shareable precise-
  location links.
- No production-scale frontend performance benchmark was run — only a
  small, hand-built manual-verification dataset (see the milestone
  document's Performance Review section).
