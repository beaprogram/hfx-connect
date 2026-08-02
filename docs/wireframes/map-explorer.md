# Wireframe — Interactive Map (`/resources`, Map View)

Milestone 7B. Activated via the **List | Map** toggle on `/resources` — see
[resource-list.md](resource-list.md)'s "Deviation" note. Not a separate route; see
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md) for why.

## Content Hierarchy (Desktop, `lg` and above)

```
┌──────────────────────────────────────────────────────────────────┐
│ Header / skip link (site-wide)                                       │
├──────────────────────────────────────────────────────────────────┤
│ H1  Browse resources                          [ List | Map ]         │
│     One-line explanation (map-mode wording)                          │
├──────────────────────────────────────────────────────────────────┤
│ <form> Search | Category | Cost | Verification | Open now | Apply    │
│         (Sort hidden — nearby results are always distance-ordered)   │
├──────────────────────────────────────────────────────────────────┤
│ [ Use my location ]   [ Search radius: <select> ]                    │
│ "Showing resources near central Halifax." / "…near your location."   │
├──────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────┐  ┌───────────────────────────────────┐ │
│ │ N resources within X km  │  │  [Search this area] (conditional)  │ │
│ │                           │  │  ┌───────────────────────────┐    │ │
│ │ ┌───────────────────────┐│  │  │                             │    │ │
│ │ │ Resource card          ││  │  │    Leaflet map              │    │ │
│ │ │ name · category · dist ││  │  │    (OSM tiles, clustered    │    │ │
│ │ │ badges · [View details]││  │  │     markers, zoom controls) │    │ │
│ │ └───────────────────────┘│  │  │                             │    │ │
│ │ (repeated per result)    │  │  └───────────────────────────┘    │ │
│ │ [Previous] Page X [Next] │  │  Leaflet | © OpenStreetMap...      │ │
│ └─────────────────────────┘  └───────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────┤
│ Footer (site-wide)                                                    │
└──────────────────────────────────────────────────────────────────┘
```

List panel and map panel are shown side by side, both always visible.

## Content Hierarchy (Mobile/Tablet, below `lg`)

Identical header/filter/location/radius rows, full width. Below them, a compact
**Map | List** sub-toggle switches which single panel is visible (map panel shown by
default) — both panels stay mounted in the DOM (visibility toggled via CSS only) so
the Leaflet map instance is never destroyed/recreated by switching panels.

## Main Actions

1. **List | Map** toggle → switches the whole results section; preserves filters.
2. **Use my location** → requests browser geolocation (only on click — never
   automatic); success recentres the map and updates the status line.
3. **Search radius** `<select>` → 1/2/5/10/25/50 km, resets pagination.
4. Pan/zoom the map → records a pending centre on `moveend`; **Search this area**
   appears once the move is meaningful (≥ 50 m) and, on activation, becomes the new
   search origin.
5. Click a list card / a map marker → selects it in both places (highlighted card +
   open marker popup); does not refetch.
6. Click a marker cluster → Leaflet's own spiderfy/zoom-in behavior reveals
   individual markers.
7. "View details" (card or popup) → `/resources/[slug]`.
8. Previous / Next (list panel) → client-side nearby-page state, not a URL
   navigation (search centre is never in the URL — see ADR-013).

## URL State

`view=map`, `radiusKm` — mirrored best-effort for shareability, never authoritative
(see ADR-013's "State Architecture"). Filters (`q`, `categoryId`, `costType`,
`verificationStatus`, `openNow`) reuse the exact same URL parameters as List mode.
**Latitude/longitude are never in the URL**, in any form, at any point.

## Progressive Enhancement

The map is loaded client-side only (`next/dynamic({ ssr: false })`) and is never the
only way to reach a resource — every card in the list panel links to the resource's
detail page through a standard `<a>`. If the map fails to load (a tile outage, a
client-side exception in the dynamically-loaded chunk), the list panel and its own
data fetch are entirely unaffected, since `NearbyMapView`'s query and
`NearbyResourceCard` list have no dependency on the map component rendering
successfully.

## Accessibility Considerations

- The map container has an accessible name (`aria-label="Map of nearby resources"`).
- "Use my location" and "Search this area" are standard, labelled, keyboard-focusable
  buttons.
- The radius `<select>` has a visible `<label>`.
- Geolocation status text uses `role="status"` (polite) — permission denial/timeout/
  unavailable are calm, recoverable states, never an alarming presentation.
- Selected-state (card or marker) is communicated through a visible "Selected" badge
  and a thicker border, never colour alone.
- The List/Map and Map/List sub-toggle both use `aria-pressed`, not colour alone.
- The list panel is the complete accessible alternative to the map — no information
  exists only inside a marker popup that isn't also on its matching card.

## Loading / Empty / Error States

See `states.md` for the shared patterns this page reuses. Map-specific cases:

- Nearby request pending → "Loading nearby resources…" text in the list panel; the
  map itself shows a lightweight "Loading map…" placeholder only during the initial
  dynamic-import fetch, not on every re-query.
- No results within the radius/filters → empty state suggesting a larger radius,
  fewer filters, or moving the map — never a bare "No data."
- Nearby request fails → an inline error in the list panel; the map keeps showing
  its last-known markers rather than clearing to a blank state.
- Geolocation denied/unavailable/timed out → a calm status message; the map and list
  remain fully usable with the current (default or manually chosen) centre.
