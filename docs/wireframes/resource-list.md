# Wireframe — Resource List (`/resources`)

## Content Hierarchy

```
┌──────────────────────────────────────────────────────────┐
│ Header / skip link (site-wide)                               │
├──────────────────────────────────────────────────────────┤
│ H1  Browse resources                                          │
│     One-line explanation of what's listed (active, verified   │
│     or not, community resources)                              │
├──────────────────────────────────────────────────────────┤
│ <form method="get" action="/resources">                       │
│   [ Category: <select> ]  [ Sort: <select> ]  [ Apply ]        │
│   (Apply button is the real submit; selects auto-submit via    │
│    JS when available — see "Progressive Enhancement" below)    │
│ </form>                                                        │
│ Active filters: "Food Assistance" [Reset]   (only when set)    │
│ "N resources found" (only once the count is known)             │
├──────────────────────────────────────────────────────────┤
│ Resource cards, one per result, in a responsive grid/list:      │
│   H2 (per-card) resource name -> /resources/[slug]              │
│   category name · city, province                                │
│   cost-type badge · verification-status badge                   │
├──────────────────────────────────────────────────────────┤
│ Pagination: [Previous] Page X of Y [Next]                       │
├──────────────────────────────────────────────────────────┤
│ Footer (site-wide)                                              │
└──────────────────────────────────────────────────────────┘
```

## Main Actions

1. Change category filter → new `GET /resources?categoryId=..&sort=..&page=0`
   (page always resets to `0` on a filter/sort change).
2. Change sort → same, page resets to `0`.
3. Reset → `GET /resources` (no query params).
4. Previous / Next → `GET /resources?...&page=N-1` / `page=N+1`, disabled at the
   boundaries rather than linking to an invalid page.
5. Resource card → `/resources/[slug]`.

## Progressive Enhancement (No-JavaScript Behavior)

The filter/sort form is a plain HTML `<form method="get">`. With JavaScript disabled,
the visible "Apply" button submits it and the browser navigates normally — every
control still works. With JavaScript enabled, the `<select>` elements submit the form
automatically on `change` (a small client "enhancement" component), and pagination
links are plain `<Link>`s so they always work regardless of JavaScript. No control
requires JavaScript to function; JavaScript only removes one extra click.

## URL State

`page` (0-based, matches the backend), `categoryId`, `sort`. Missing/invalid values
fall back to safe defaults (page `0`, no category filter, `sort=name`) rather than
erroring — see `states.md` for the invalid-parameter and unknown-category cases.

## Mobile Layout

Filter form controls stack vertically below ~640px. Cards become a single column.
Pagination controls remain on one row (compact — no page-number list, just
Previous/Next + "Page X of Y").

## Accessibility Considerations

- `<select>` elements have visible `<label>`s (not placeholder-only).
- Each card is an `<article>` with a heading (`<h2>`) containing the only link — the
  whole card is not a nested/duplicated interactive region (buttons/links are not
  nested inside the card's own link).
- Cost-type and verification-status are shown as text badges, not colour alone.
- "N resources found" is in a live region so screen-reader users get the same
  feedback sighted users get from the count changing after a filter/sort/page action.
- Previous/Next have accessible names ("Previous page", "Next page"), not just
  arrow glyphs, and `aria-disabled` at the boundaries.

## Loading / Empty / Error States

See `states.md`. Distinguished cases specific to this page:

- No resources exist at all → generic "no active resources yet" empty state.
- A category filter matches zero resources → empty state names the selected
  category and offers "Reset filters".
- `categoryId` in the URL doesn't match any known category (shared link to a since-
  removed category) → the filter UI falls back to "All categories" and a small note
  explains the requested category couldn't be found, rather than crashing or
  silently ignoring the parameter.
