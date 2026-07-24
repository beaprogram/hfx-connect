# Task 015: Homepage and Resource Browsing Experience

## Objective

Build the homepage and the `/resources` list — the two pages a public
visitor actually browses — on top of Task 014's API/query foundation.

## Context

Part of Milestone 4 (Public Frontend) — see
`docs/milestones/milestone-04-public-frontend.md`.

## Scope

- `app/page.tsx`: hero, category grid, resource preview, verification
  explanation — each data section fetches (and can fail) independently via
  `Promise.allSettled`, per `docs/wireframes/homepage.md`.
- `app/resources/page.tsx` (+ `loading.tsx`, `error.tsx`) and
  `components/resources/resource-list-view.tsx`: server-side prefetch +
  hydration, client-side `useQuery` for filter/sort/pagination changes,
  URL state via `lib/query/resource-list-params.ts`.
- `components/resources/resource-filter-form.tsx`: a progressive-enhancement
  `<form method="get">` — works fully without JavaScript, enhanced with
  client-side navigation when available.
- `components/resources/pagination.tsx`: compact, accessible, 0-based URL /
  1-based display translation.
- `components/categories/`, `components/resources/resource-card.tsx`,
  `resource-grid.tsx`, `status-badges.tsx`, `resource-list-skeleton.tsx`.
- `components/navigation/mobile-nav.tsx` and the `SiteHeader` update it
  plugs into; shared `components/feedback/` (`Badge`, `EmptyState`,
  `InlineError`, `RouteError`).
- `app/not-found.tsx` (root 404).

## Out of Scope

The resource-detail page (Task 016). Any backend filter the API doesn't
support (verification-status, cost, distance, free-text search).

## Acceptance Criteria

- Category and resource-preview sections on the homepage fail independently
  — one down doesn't take out the other.
- `/resources` keeps `page`/`categoryId`/`sort` in the URL; every invalid
  value falls back to a safe default; the filter form works with JavaScript
  disabled.
- Every card has exactly one interactive element (no nested interactive
  controls).
- Mobile nav is a real, keyboard-operable disclosure pattern.

## Technical Approach

Chose server-prefetch + client `useQuery` (rather than pure
searchParams-driven Server Components) specifically because the milestone
requires TanStack Query's own loading/error/success state machine and stable
query keys — documented as a deliberate trade-off, not treated as free, in
`docs/architecture/frontend-architecture.md`.

## Testing Requirements

Component tests for `ResourceCard`, `CategoryCard`, `StatusBadges`,
`MobileNav` (full keyboard/focus behavior), `Pagination`, and
`ResourceListView` (loading/success/empty/error/category-mismatch, via a
real `QueryClientProvider` with mocked API calls) — see Task 018.

## Result

Completed. Caught and fixed one real defect during testing: the empty state
rendered a second, redundant "Reset filters" link when a category filter
produced zero results — see the milestone doc's Testing section.

## Related Commits

`feat: build public homepage and resource browsing experience`
