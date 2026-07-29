# Task 027: Operating-Hours and Filters Frontend

## Objective

Surface Task 026's cost/verification/open-now filters and weekly-schedule
data in the `/resources` list and `/resources/[slug]` detail pages, with
safe URL state and no browser-side open-now recalculation.

## Context

The second of two tasks completing Milestone 6B. Builds directly on
Milestone 6A's `resource-filter-form.tsx`/`resource-list-view.tsx`/
`pagination.tsx` progressive-enhancement pattern and URL-state module.

## Scope

- `lib/validation/schemas.ts` — `hoursStatusSchema`, `dayOfWeekSchema`,
  `operatingHoursEntrySchema`, `operatingHoursSchema`; `hoursStatus`/
  `openNow` on `resourceSummaryResponseSchema`; `hours` on
  `resourceResponseSchema`.
- `lib/constants/resources.ts` — `COST_TYPE_FILTER_OPTIONS`,
  `VERIFICATION_STATUS_FILTER_OPTIONS`, `WEEKLY_DAY_ORDER`.
- `lib/api/resources.ts` — `costType`/`verificationStatus`/`openNow`
  request params.
- `lib/query/resource-list-params.ts` — the three filters parsed with safe
  fallbacks (invalid values become "no filter," never a crash or a
  falsely-active filter); `lib/query/keys.ts` — added to the resource-list
  query key.
- `lib/formatting/labels.ts` — `hoursStatusLabel`, `dayOfWeekLabel`,
  `formatLocalTime` (12-hour display of the backend's ISO local-time
  strings, no timezone reinterpretation).
- `components/resources/status-badges.tsx` — `HoursStatusBadge`.
- `components/resources/resource-filter-form.tsx` — labelled cost/
  verification `<select>`s, an "Open now" checkbox, a "Reset all filters"
  link.
- `components/resources/resource-card.tsx` — open-status badge.
- `components/resources/resource-detail.tsx` — "Hours" section (current
  status, weekly schedule Monday-Sunday, closed vs. unavailable
  distinguished, overnight intervals marked).
- `components/resources/pagination.tsx`, `app/resources/page.tsx` —
  preserve/prefetch the new filters.

## Out of Scope

Backend filter/schedule logic (Task 026), a public hours-editing UI,
next-opening-time display, geospatial search (Milestone 7).

## Acceptance Criteria

- [x] Cost/verification selects and the open-now checkbox are labelled,
      keyboard-operable, and reflect existing URL values.
- [x] Changing any filter updates the URL, resets to page 1, and preserves
      the other active filters (`q`/`categoryId`/`sort` and the other new
      filters).
- [x] An invalid `costType`/`verificationStatus`/`openNow` value in a
      hand-edited URL falls back to "no filter" safely — never crashes,
      never renders as active, never sent to the backend as-is.
- [x] "Reset all filters" appears only when a filter is active and clears
      every one of them.
- [x] Resource cards show a readable open-status label ("Open now" /
      "Closed" / "Hours unavailable") — never colour alone, never "Open
      now" when the status is `UNKNOWN`.
- [x] The detail page's weekly schedule is ordered Monday-Sunday, shows
      "Closed" for an explicit closed day and "Hours unavailable" for a
      day with no schedule entry (distinct messages), and marks an
      overnight interval clearly.
- [x] The frontend never calculates open-now itself — every status
      rendered comes directly from the backend's `hoursStatus`/`openNow`.
- [x] All 143 inherited frontend tests still pass, alongside 34 new ones
      (177 total).

## Technical Approach

Follows Milestone 6A's exact pattern: the filter controls live inside the
same progressive-enhancement `<form method="get">`, each `onChange` also
calls `router.push` for an instant client-side transition, and
`buildResourcesHref` is the single place that decides which query
parameters actually appear in a URL (a "no filter" value is always
omitted, never sent as an empty/false parameter). `formatLocalTime` is
pure display formatting — it parses the backend's already-Halifax-resolved
`"HH:mm:ss"` string and re-renders it as `"9:00 AM"` without touching what
the value means, matching ADR-011's "backend status is authoritative"
requirement literally: no component in this task computes `hoursStatus`/
`openNow` from a schedule itself.

## Testing Requirements

`npm test`, `npm run typecheck`, `npm run lint`, `npm run build`. Extended
`resources.test.ts` (API-client encoding, response parsing, and a
malformed-hours-shape safety test), `resource-list-params.test.ts`
(parsing/building the three new filters, invalid-value fallback),
`resource-filter-form.test.tsx` (labelled controls, existing-value
rendering, per-control navigation, combined submission, reset-all),
`resource-list-view.test.tsx` (filter passthrough, combined-filter
summary, filters-aware no-results messaging), `resource-card.test.tsx` and
`status-badges.test.tsx` (open/closed/unknown display), and
`resource-detail.test.tsx` (weekly order, closed vs. unavailable,
overnight marking, current-status badge, no-schedule handling).

## Result

Completed. 34 new tests pass alongside the 143 from Milestones 3A-6A (177
total, authoritative per `npm test`). `npm run typecheck`, `npm run lint`,
and `npm run build` all pass with no new warnings.

## Related Commits

`feat: add operating-hours and filter frontend experience`,
`test: add operating-hours and filter frontend coverage`.
