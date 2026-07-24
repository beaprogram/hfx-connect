# Frontend Architecture

> Status: Milestone 4 (Public Frontend) established the first real frontend
> architecture — homepage, resource list, resource detail, all backed by the
> real Category (Milestone 3A) and Resource (Milestone 3C) APIs. Milestones 1
> and 2A only established the application shell (layout, header/footer,
> Tailwind, testing setup); this document describes what Milestone 4 built on
> top of that.

## Route Structure

```
/                          Homepage — hero, category grid, resource preview, trust copy
/resources                 Paginated, filterable, sortable resource list
/resources/[slug]          Full detail for one active resource
```

Every route is a Server Component by default; only components that need
browser-side interactivity (the mobile nav toggle, the filter form's
auto-submit enhancement, the resource list's client-managed query state) are
marked `"use client"`. This follows the pattern
`docs/architecture/backend-architecture.md` already established for the
backend (thin orchestration at the edge, real logic behind it) — here, "thin
edge" is the route segment, and "real logic" is `lib/` and `components/`.

## Server/Client Split for `/resources`

`app/resources/page.tsx` is a Server Component. It parses and sanitizes
`searchParams` (see "URL State" below), calls `queryClient.prefetchQuery` for
both the resource list and the category list, and renders a `<HydrationBoundary>`
wrapping the actual UI (`ResourceListView`, a Client Component). This is the
standard TanStack-Query-with-the-Next.js-App-Router pattern: the server does
the real data fetch (so the first response already contains real content —
verified directly against a running backend, see `Manual Verification` in the
milestone doc), and the client component's own `useQuery` calls pick up that
prefetched cache instantly on hydration, then re-fetch normally as the user
changes filters/sort/page (each of which is a full Next.js navigation to a new
URL, which reruns the server prefetch too — see "URL State").

**Why not pure Server Components with no TanStack Query at all?** A
searchParams-driven Server Component alone would work and would be simpler.
TanStack Query is used anyway because the milestone's own requirements call
for it explicitly (stable query keys, retry behavior, cancellation), and
because it gives the resource-list UI a single, consistent way to represent
loading/error/success state (`useQuery`'s `isPending`/`isError`/`isSuccess`)
that the loading/error/empty-state requirements map onto directly. This is a
documented trade-off, not treated as free — see the "no infinite retry
loops" note under Query Configuration.

**Why not client-side-only fetching with no server prefetch?** That would
mean every `/resources` visit starts blank and pops in content, which is a
strictly worse first paint (see the "resource preview loads real API results
on the initial HTML" verification in the milestone doc — this only holds
because of the server prefetch).

## Typed API Client

`lib/api/client.ts` exports one function, `getJson`, that every API call goes
through. It centralizes:

- URL/query-string construction (`URLSearchParams`, so encoding is always
  correct — verified in `lib/api/resources.test.ts`/`categories.test.ts`);
- non-2xx response handling — maps the backend's `ApiError` shape
  (`docs/api/README.md`) to a typed `ApiRequestError` (`status`, `code`,
  `message`, `fieldErrors`), or a generic one if the body doesn't match;
- runtime response-shape validation via Zod (`lib/validation/schemas.ts`) —
  a `safeParse` failure throws `ApiResponseShapeError` rather than handing
  malformed data to a component; the raw Zod issues are only logged
  (`NODE_ENV !== "production"`), never surfaced to the user;
- `AbortSignal` support, threaded through from TanStack Query's `queryFn`
  context so an abandoned filter change cancels its in-flight request instead
  of racing a stale one to completion.

`lib/api/categories.ts` and `lib/api/resources.ts` expose only the specific
operations this frontend actually calls (`getCategories`, `getResources`,
`getResourceBySlug`) — no speculative generic CRUD surface.

### Why Zod Schemas Have No `accessibility` or `lastVerifiedAt` Field

Checked directly against the running backend's `/v3/api-docs` before writing
any schema (not from memory or from the milestone brief's description of a
richer schema) — `ResourceResponse` has neither field. Inventing them would
violate this milestone's own "no fabricated values" rule; see
`docs/wireframes/resource-detail.md`'s "A Note on Scope."

## Query Configuration

`lib/query/get-query-client.ts` creates one `QueryClient` per server request
(never shared across requests/users) and a single persistent one in the
browser — the documented pattern for TanStack Query under the App Router.
Defaults: `staleTime: 30s` (a public directory doesn't need to refetch on
every focus event) and `retry: (count, error) => !isAbortError(error) && count < 1`
— exactly one retry for a genuine failure, zero for a cancelled request, so a
persistently-down backend fails fast into the error UI instead of retrying
forever.

### Query Keys

`lib/query/keys.ts`:

```ts
resourceKeys.list({ page, size, categoryId, sort })
categoryKeys.list({ active })
resourceKeys.detail(slug)
```

`page`/`size`/`categoryId`/`sort` are all part of the resource-list key, so a
filtered view can never be served from a different filter's cache entry.

## URL State (`/resources`)

`lib/query/resource-list-params.ts`'s `parseResourceListParams` is the single
place raw, untrusted `searchParams` become a safe, typed
`{ page, categoryId, sort }`. Every invalid input is corrected to a safe
default rather than thrown:

| Input | Result |
|---|---|
| Missing/negative/non-numeric `page` | `0` |
| Missing/non-numeric/non-positive `categoryId` | `undefined` (no filter) |
| Missing/unrecognized `sort` | `"name"` |
| A repeated query parameter (`?page=1&page=2`) | first value used |

`page` stays 0-based in the URL, matching the backend exactly — see
`docs/api/README.md`'s pagination convention — but is never shown to a user
as a raw number: `components/resources/pagination.tsx` always displays
`page + 1` ("Page 2 of 5"). An out-of-range `page` isn't special-cased at
all: the backend just returns an empty `content` array, which renders through
the normal empty-state path.

A `categoryId` that doesn't match any category currently returned by
`GET /api/v1/categories?active=true` (removed, deactivated, or just wrong)
is handled explicitly in `ResourceListView`: the filter UI falls back to "All
categories" and a note explains the requested category couldn't be found,
rather than silently ignoring the parameter or crashing.

## Progressive Enhancement: The Filter Form

`components/resources/resource-filter-form.tsx` is a real
`<form method="get" action="/resources">` with named `<select>`s — with
JavaScript disabled, the visible "Apply" button submits it and the browser
navigates normally; every control still works. With JavaScript enabled, an
`onChange` handler intercepts the submit and calls `router.push` for an
instant client-side transition instead — the visible submit button stays in
place as a working fallback either way. Pagination links are plain `<Link>`s,
which work with or without JavaScript by construction.

## Loading, Empty, Error, and Not-Found States

See `docs/wireframes/states.md` for the full pattern catalogue. Two
Next.js-specific implementation notes:

- **`error.tsx` uses `unstable_retry`, not `reset`.** This Next.js version
  (16.2) added `unstable_retry` as the recommended recovery callback — it
  re-fetches and re-renders the segment, where `reset` only clears the error
  boundary's local state. Both still work; `unstable_retry` is what the
  installed version's own docs recommend. See
  `components/feedback/route-error.tsx`.
- **A route's own data-fetch failures don't reach `error.tsx` at all** when
  using `queryClient.prefetchQuery` — TanStack Query's `prefetchQuery`
  deliberately never throws (it stores the failure in the query cache
  instead), which is what lets `/resources`'s own `ResourceListView` show a
  scoped "Resources couldn't be loaded" message without taking down the
  whole page (the filter form, heading, etc. stay usable).
  `app/resources/error.tsx` exists for genuinely unexpected render-time
  exceptions, not for an expected backend-unavailable case.
- **`notFound()` yields an HTTP `200`, not `404`, for a streamed response.**
  Documented Next.js behavior (`next/dist/docs/.../not-found.md`): "Next.js
  will return a 200 HTTP status code for streamed responses, and 404 for
  non-streamed responses." `/resources/[slug]` is a dynamic (streamed) route,
  so a deactivated/unknown slug still renders the correct not-found *content*
  at a `200` status in dev; this is a platform-level SEO/streaming trade-off,
  not a bug in this app — see the milestone doc's manual verification notes.

## Accessibility Decisions

- Every card (`ResourceCard`, `CategoryCard`) is an `<article>` with exactly
  one interactive element: the heading's own link, extended to cover the
  whole card visually via `after:absolute after:inset-0` rather than wrapping
  the entire card in a second, redundant link/button — avoids the "nested
  interactive controls" problem entirely.
- `MobileNav` is a real disclosure pattern: `aria-expanded`, `aria-controls`,
  focus moves into the panel on open, `Escape` closes and returns focus to
  the toggle, and it isn't present in the DOM above the desktop breakpoint at
  all (`sm:hidden` wrapper).
- Cost/verification status are always icon-and-text `Badge`s
  (`components/feedback/badge.tsx`), never colour alone.
- The `/resources` result area is `aria-live="polite"` so a filter/sort/page
  change's new result count is announced, matching what a sighted user sees
  from the count changing.

## Responsive Verification

Verified via the Tailwind breakpoint classes actually used, since no
browser-automation tool was available in this environment for pixel-level
visual verification (see the milestone doc's manual verification section for
the full, honest account of what was and wasn't verified visually): card
grids collapse `grid-cols-1` → `sm:grid-cols-2` → `lg:grid-cols-3`, the
header's inline nav is `hidden` below `sm:` in favor of `MobileNav`, and the
filter form's controls (`flex flex-wrap`) stack on narrow widths without any
width-specific overrides needed.

## Backend Connectivity

The browser calls the backend directly — see
[ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md) for why this
was chosen over a Next.js proxy layer, and `backend/README.md`'s CORS section
for the resulting `WebCorsConfig`.

## Known Limitations

- No `verificationStatus` or `active` filter on `/resources` — the backend
  doesn't support them publicly yet (`docs/api/README.md`).
- No free-text search, distance/geospatial filtering, or map — later
  milestones (6-7).
- Responsive/visual verification in this milestone was code-review-based
  (Tailwind breakpoint classes) and curl-based (rendered HTML content), not a
  live graphical browser session — no browser-automation tool was available
  in the development environment used for this milestone. A real browser
  should still be used before this ships publicly.
