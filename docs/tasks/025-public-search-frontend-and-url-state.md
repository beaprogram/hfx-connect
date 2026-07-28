# Task 025: Public Search Frontend and URL State

## Objective

Add a keyword search field to the public `/resources` page, fully
integrated with the existing category/sort filters, URL state, and TanStack
Query cache keys — with an honest, keyword-aware result summary and
no-results messaging, and no relevance claim.

## Context

The second of two tasks completing Milestone 6A. Depends on Task 024's
`GET /api/v1/resources?q=...` backend contract.

## Scope

- `lib/query/resource-list-params.ts` — `q` added to `ResourceListParams`
  and `RawSearchParams` parsing (trim/collapse/blank-becomes-undefined,
  best-effort length truncation); `buildResourcesHref` includes `q`.
- `lib/query/keys.ts` — `q` added to the resource-list query key.
- `lib/api/resources.ts` — `q` added to `GetResourcesParams`.
- `components/resources/resource-filter-form.tsx` — a labelled `q` field in
  the existing GET form; submit-based (not per-keystroke); a "Clear search"
  action.
- `components/resources/resource-list-view.tsx` — keyword-aware result
  summary and no-results messaging (keyword-only, category-only, and
  combined cases each distinct).
- `components/resources/pagination.tsx` — preserves `q` in page links.
- `app/resources/page.tsx` — server-side prefetch includes `q`.

## Out of Scope

Backend search implementation (Task 024), autocomplete, debounced/
per-keystroke search, cost-type/verification-status filters.

## Acceptance Criteria

- [x] The search field has a visible label, works with the keyboard (Enter
      submits), and remains usable without JavaScript (a real
      `method="get"` form).
- [x] Submitting a search updates `q` in the URL, resets to page 1, and
      preserves `categoryId`/`sort`.
- [x] "Clear search" removes only `q`; "Reset filters" (existing) clears
      everything.
- [x] The result summary states the search phrase and/or category honestly
      (`"N resources matching '...'"`) — never a relevance claim.
- [x] No-results messaging distinguishes keyword-only, category-only, and
      combined cases.
- [x] `q` is part of the TanStack Query cache key, so a searched view never
      shares a cache entry with an unsearched one.
- [x] All 120 inherited frontend tests still pass, alongside 23 new ones
      (143 total).

## Technical Approach

The search input is a named field (`q`) inside the same `<form
method="get" action="/resources">` the category/sort selects already use —
submitting it (Enter, or the existing "Apply" button) naturally carries
every filter along via the browser's own GET-form serialization, with or
without JavaScript, consistent with the page's existing progressive-
enhancement design. It deliberately does not auto-submit on every keystroke
(no debounce mechanism was built) — a submit-based search is simpler to
reason about and test, per the milestone's own guidance.

## Testing Requirements

`npm test`. `resources.test.ts`/`resource-list-params.test.ts` (API-client
encoding and URL-parsing edge cases), a new `resource-filter-form.test.tsx`
(label, current-value display, submit behavior, page-reset, filter
preservation, no-per-keystroke-submission, Clear search), extended
`pagination.test.tsx` and `resource-list-view.test.tsx` (result summary and
no-results wording for every keyword/category combination, and an explicit
assertion that no "relevance" language ever appears).

## Result

Completed. 23 new tests pass alongside the 120 from Milestones 4-5C (143
total, authoritative per `npm test`). `npm run build` succeeds.

## Related Commits

`feat: add searchable resource browsing UI`,
`test: add keyword search and URL-state coverage`.
