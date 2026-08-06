# Frontend Architecture

> Status: Milestone 4 (Public Frontend) established the first real frontend
> architecture — homepage, resource list, resource detail, all backed by the
> real Category (Milestone 3A) and Resource (Milestone 3C) APIs. Milestones 1
> and 2A only established the application shell (layout, header/footer,
> Tailwind, testing setup); this document describes what Milestone 4 built on
> top of that. Updated in Milestone 5C, which added the first authenticated
> routes (`/login`, `/register`, `/dashboard`) and an in-memory session layer —
> see "Authentication Architecture" below. Updated again in Milestone 6A,
> which added keyword search to `/resources` — see "URL State (`/resources`)"
> below. Updated again in Milestone 7B, which added the interactive map,
> browser geolocation, and List/Map presentation switch — see "Interactive
> Map Architecture" below. Updated again in Milestone 8A, which added
> saved resources — the first private, per-account data this frontend
> caches — see "Saved Resources and Private-Data Cache Isolation" below.
> Updated again in Milestone 8B, which added resource submissions and
> correction reports, extending the same private-cache-isolation
> pattern to two more query-key prefixes and adding `returnTo` support
> to `ProtectedRoute` itself.

## Route Structure

```
/                          Homepage — hero, category grid, resource preview, trust copy
/resources                 Paginated, filterable, sortable resource list —
                           or, in Map view (Milestone 7B), the same
                           filters driving an interactive nearby-search
                           map alongside a synchronized list
/resources/[slug]          Full detail for one active resource
/login                     Email/password login
/register                  Account registration (does not log the caller in)
/dashboard                 The current authenticated account — protected client-side
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
resourceKeys.list({ page, size, categoryId, sort, q, costType, verificationStatus, openNow })
categoryKeys.list({ active })
resourceKeys.detail(slug)
resourceKeys.nearby({ latitude, longitude, radiusKm, page, size, categoryId, q, costType, verificationStatus, openNow })
savedResourceKeys.list(userId, { page, size, sort })
savedResourceKeys.status(userId, resourceIds)
```

`savedResourceKeys` (Milestone 8A) is the first key factory rooted in a
value that identifies *who is asking*, not just *what is being asked
for* — every key starts with the authenticated user's stable `id`,
never the access token (which rotates on refresh and would otherwise
fragment one live session's own cache). `status(userId, resourceIds)`
sorts `resourceIds` before building the key, so the same set of ids
requested in a different render order still hits one shared cache entry
— see "Saved Resources and Private-Data Cache Isolation" below.

`resourceKeys.nearby` (Milestone 7B) rounds latitude/longitude to 5 decimal
places purely to keep the cache key stable against floating-point noise —
never for the actual request. It deliberately excludes the selected
resource: selection is UI state, not part of what identifies this
server-state query — see "Interactive Map Architecture" below.

`page`/`size`/`categoryId`/`sort`/`q`/`costType`/`verificationStatus`/
`openNow` (the last three added in Milestone 6B, see
[ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md)) are all
part of the resource-list key, so a filtered/searched view can never be
served from a different filter's cache entry. None of these appear in
`categoryKeys` — filtering/search are resource-only concepts (Milestone 6A,
ADR-010).

## URL State (`/resources`)

`lib/query/resource-list-params.ts`'s `parseResourceListParams` is the single
place raw, untrusted `searchParams` become a safe, typed
`{ page, categoryId, sort, q }`. Every invalid input is corrected to a safe
default rather than thrown:

| Input | Result |
|---|---|
| Missing/negative/non-numeric `page` | `0` |
| Missing/non-numeric/non-positive `categoryId` | `undefined` (no filter) |
| Missing/unrecognized `sort` | `"name"` |
| A repeated query parameter (`?page=1&page=2`) | first value used |
| Blank/whitespace-only `q` | `undefined` (no keyword filter) |
| `q` over 100 characters | truncated to 100 (best-effort only — the backend independently re-validates and is authoritative; see [ADR-010](../decisions/ADR-010-keyword-search-design.md)) |
| Unrecognized `costType`/`verificationStatus` | `undefined` (no filter) — validated against the real `CostType`/`VerificationStatus` enum values, never invented |
| `openNow` other than exactly `"true"` | `undefined` (no filter) — `"false"`, blank, and anything else are all treated identically to "absent," matching the backend's own default |

Milestone 6B's `costType`/`verificationStatus`/`openNow` filters (see
[ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md)) follow the
same "safe fallback, never a crash, never a value the backend didn't
actually receive" rule as `q` above — an invalid value in a hand-edited URL
never renders as if the filter were active.

Keyword search (Milestone 6A) reuses this exact pattern: `q` is parsed by
the same function, included in `resourceKeys.list`'s query key (so a
searched view never shares a TanStack Query cache entry with an unsearched
one), and carried through `buildResourcesHref` alongside `categoryId`/`sort`
on every pagination link. The search field lives inside the same
`<form method="get">` `components/resources/resource-filter-form.tsx`
already used for category/sort — submitting it (Enter, or the existing
"Apply" button) is a genuine GET-form submission that works with or without
JavaScript, and always resets to page 1 (the form has no `page` field).

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

## Interactive Map Architecture

Full design rationale:
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md).
Summary of what actually exists in the code:

- **`ResourceExplorer`** renders either `ResourceListView` (unmodified
  since Milestone 6) or the new `MapExplorerView`, based on
  `useMapSearch().view` — never a single component branching internally.
  `ResourceListView` has zero dependency on `useMapSearch()`, so its
  existing tests (rendered with no `MapSearchProvider`) keep passing
  unmodified, and the map can never become the only way to reach a
  resource.
- **`MapSearchProvider`** (`lib/map/map-search-context.tsx`) is mounted at
  `app/resources/layout.tsx` — a layout, not a page, so it survives a
  filter-driven `/resources?...` navigation and a round trip to
  `/resources/[slug]` and back. It owns `view`, `radiusKm`, `centre`,
  `centreSource`, `pendingCentre`, `selectedResourceId`, and
  `nearbyPage`. `view`/`radiusKm` are best-effort mirrored to the URL
  (read once on mount, written on change, scoped to exactly the
  `/resources` pathname); coordinates and selection are never written
  anywhere outside React state.
- **The map itself is client-only.** `components/map/nearby-map.tsx` is
  loaded exclusively via `dynamic(() => import(...), { ssr: false })`
  from inside `NearbyMapView` (already `"use client"`) — Leaflet reads
  `window`/`document` at import time and would otherwise crash server
  rendering. This is confirmed with a real production build
  (`next build --webpack`), not just assumed from the dynamic-import
  option existing.
- **`lib/map/use-geolocation.ts`** wraps
  `navigator.geolocation.getCurrentPosition` behind an explicit
  `requestLocation()` call — nothing invokes it automatically. Its
  status (`idle | requesting | success | permission-denied | unavailable
  | timeout`) is transient, component-local state — it resets on
  remount, unlike the durable centre committed to `MapSearchProvider`.
- **List/map selection** is one shared `selectedResourceId` in
  `MapSearchProvider`, read by both `NearbyResourceCard` and each
  `Marker`. It is deliberately excluded from `resourceKeys.nearby`, so
  selecting a card or marker never triggers a network request.
- **"Search this area"** is driven by Leaflet's `moveend` event only
  (never `move`/`drag`), recording a `pendingCentre`; a button appears
  once that pending centre is at least 50 m from the currently-searched
  one and, on activation, commits it as the new `centre`.

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

Through Milestone 6, verified via the Tailwind breakpoint classes actually
used, since no browser-automation tool was available in this environment
for pixel-level visual verification: card grids collapse `grid-cols-1` →
`sm:grid-cols-2` → `lg:grid-cols-3`, the header's inline nav is `hidden`
below `sm:` in favor of `MobileNav`, and the filter form's controls
(`flex flex-wrap`) stack on narrow widths without any width-specific
overrides needed.

**Milestone 7B** had a headless-Chromium instance available (Playwright,
already cached in the development environment) and used it for genuine
pixel-level verification — real screenshots captured and reviewed at
375px/768px/1024px/1440px, not inferred from class names alone — see the
milestone doc's "Manual Verification" section. The map's own desktop
split (`grid-cols-1` → `lg:grid-cols-2`) and mobile Map/List sub-toggle
were confirmed visually this way, catching one real bug (a URL-mirroring
leak onto the resource-detail page, described in ADR-013) that a
class-name-only review would not have surfaced.

## Authentication Architecture

Full design rationale:
[ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md).
Summary of what actually exists in the code:

- **`lib/auth/auth-provider.tsx`** — a `"use client"` React Context provider
  (`AuthProvider`/`useAuth`), following the exact pattern
  `node_modules/next/dist/docs/01-app/02-guides/single-page-applications.md`
  documents for this Next.js version ("SPAs with React Query" — a plain
  client-side context provider works fine alongside TanStack Query; nothing
  here needs Server Component-side session data, because every backend call
  this frontend makes is client-side to a separate origin, not a Next.js
  server reading its own cookies). `AuthState` is a closed union
  (`"loading" | "authenticated" | "unauthenticated"`) so a consumer can never
  read a token from a state that doesn't have one.
- **The access token lives only in that provider's React state — never
  `localStorage`, `sessionStorage`, or a cookie set from JavaScript.** A full
  page reload always starts at `"loading"` and discards it; there is no
  code path that persists it anywhere else. This is a deliberate consequence
  of ADR-008's original refresh-token design (Milestone 5B), not a new
  decision: an access token in any JavaScript-readable storage is exactly as
  exfiltrable by an XSS payload as a cookie without `HttpOnly` would be.
- **Session restoration** happens exactly once per page load, in
  `AuthProvider`'s mount effect: it calls `POST /api/v1/auth/refresh` (via
  `lib/api/auth.ts`'s `refreshSession`, `credentials: "include"`) to try
  exchanging the `HttpOnly` `hfx_refresh_token` cookie — which this frontend's
  own JavaScript can never read directly — for a fresh access token. Failure
  (no cookie, or an expired/reused one) resolves to `"unauthenticated"`, not
  an error; a fresh visitor with no session is an entirely ordinary case.
- **Single-flight refresh.** Refresh tokens rotate on every use (ADR-008);
  two concurrent refresh attempts from the same tab could each present the
  same soon-to-be-rotated cookie and trigger a false-positive family-wide
  revocation. `AuthProvider` coalesces concurrent refresh attempts (the
  mount-time restoration and any later `getValidAccessToken()` call that
  finds the token close to expiry) into one shared in-flight promise via a
  `useRef`, rather than each caller issuing its own request.
- **Token expiry** is tracked from the login/refresh response's own
  `expiresIn` (seconds, converted to an absolute `expiresAt` with a 10-second
  clock-skew buffer) — nothing decodes or trusts the JWT's own claims
  client-side; the response already hands over the one number that matters.
- **`components/auth/protected-route.tsx`** guards `/dashboard` and, as of
  Milestone 8B, `/submit-resource` and `/resources/[slug]/report`: it
  renders an accessible loading state for both `"loading"` and the
  brief instant `"unauthenticated"` is true before its redirect effect
  fires, so protected content is never painted even momentarily, then
  redirects to `/login`. This is explicitly a UX convenience, not a
  security boundary — see the component's own Javadoc-equivalent
  comment and ADR-009's "Frontend Route Guard Is UX-Layer Only" section
  for why Next.js middleware/Proxy cannot fill this role in this
  project's direct-frontend-to-backend architecture (the refresh cookie
  belongs to the *backend's* origin — a Next.js Proxy reading
  `cookies()` per
  `node_modules/next/dist/docs/01-app/02-guides/authentication.md`'s own
  "Optimistic checks with Proxy" section would need a cookie set for the
  Next.js app's own domain, which this one isn't, per ADR-006). As of
  Milestone 8B, the redirect target is built through
  `buildLoginHref(usePathname())` (Milestone 8A's `isSafeReturnPath`),
  not a bare `/login` — so a signed-out visit to any route this guard
  protects returns there after login, reusing the exact same
  open-redirect-safe validation the "Sign in to save" flow already
  established rather than a second implementation.
- **`lib/api/auth.ts`** extends the typed API client
  (`register`/`login`/`refreshSession`/`logout`/`getCurrentUser`), reusing
  `lib/api/client.ts`'s existing `getJson` plus two new counterparts,
  `postJson`/`postNoContent`, added specifically for this milestone (POST
  requests with and without a meaningful JSON response body, respectively).
  `login`/`refreshSession`/`logout` pass `credentials: "include"`;
  `getCurrentUser` passes an `accessToken` that becomes an `Authorization:
  Bearer` header — never a cookie.
- **Role-based visibility exists as of Milestone 9A**, in exactly one
  place: the "Moderation" nav link (`AuthNav`/`MobileNav`) and the
  `/moderation` routes, both gated on `MODERATOR`/`ADMIN` — see
  "Role-Based Route Guarding" below. There is still no category/
  resource creation UI or role-management UI; `/dashboard` shows the
  current role as plain text. Backend enforcement (`SecurityConfig`)
  remains authoritative regardless of anything the frontend renders or
  hides, in every case.

## Role-Based Route Guarding (Milestone 9A)

`components/moderation/moderation-route.tsx`'s `ModerationRoute` is a
distinct component from `ProtectedRoute`, not a role-parameterized
variant of it — it makes one additional check `ProtectedRoute`
deliberately never makes ("does this signed-in account have a
sufficient role"), and the two failure modes need different UI: a
signed-out visitor is redirected to `/login` (the same
`buildLoginHref(usePathname())` pattern `ProtectedRoute` uses), but a
signed-in `USER`/`ORGANIZATION` account is not redirected anywhere —
they *are* authenticated, so redirecting to login would be both wrong
and confusing. Instead they see an in-place "Access denied" message,
with `role="alert"` for assistive-technology announcement. Both
components share the identical loading-state flash-prevention
(`state.status === "loading"` and the brief `"unauthenticated"` instant
before the redirect effect fires both render a loading placeholder,
never the protected content).

Every check `ModerationRoute` makes is one more UX-layer convenience
on top of the same non-negotiable rule `ProtectedRoute` already
documents: the backend's own `/api/v1/moderation/**` authorization
(`SecurityConfig`, backed by the account's *current* database role —
ADR-009/ADR-016) is the actual security boundary. A `USER` account
that somehow reached `/moderation`'s rendered HTML directly would still
get `403` from every API call the page makes; the guard exists so that
never has to happen in the first place, not because it's load-bearing.

## Saved Resources and Private-Data Cache Isolation

Full design rationale:
[ADR-014](../decisions/ADR-014-saved-resources-design.md) (saved
resources) and
[ADR-015](../decisions/ADR-015-community-contribution-workflows-design.md)
(resource submissions and correction reports, Milestone 8B, which
extend every pattern below to two more private query-key prefixes
rather than introducing new ones). Summary:

- **`lib/query/use-saved-resources.ts`'s `useSavedResourceStatusMap(resourceIds)`**
  is called exactly once per listing container (the resource grid's
  parent, the nearby/map view, or a batch-of-one on the detail page) —
  never inside an individual card component — so a page of resource
  cards issues one `POST .../status` request regardless of how many
  cards it renders, not one per card. `enabled: userId !== null &&
  sortedIds.length > 0` guards it from ever firing while signed out or
  with an empty id list.
- **Every saved-resource query key is rooted in `userId`** (see "Query
  Keys" above) — never the access token, which rotates.
- **`AuthProvider` clears every private-data cache on two events**:
  unconditionally on `logout()`, and the moment `applySession()`
  detects the newly-authenticated user's id differs from the
  previously-authenticated one (an account switch within the same
  browser tab). `clearPrivateContributionCaches` (Milestone 8B) loops
  `queryClient.removeQueries` over a small, explicit list of key
  prefixes — `saved-resources`, `resource-submissions`,
  `correction-reports`, and (Milestone 9A) `moderation` — rather than a
  full `queryClient.clear()`, which would also discard unrelated,
  harmless public caches (the category list, public resource pages)
  for no benefit. `moderation` is the first prefix in that list *not*
  rooted in `userId` (`moderationKeys` in `lib/query/keys.ts` — the
  underlying data is role-gated shared moderator state, not any one
  account's own data), but it is cleared for the identical reason: it
  must never sit in one browser's cache across a session boundary.
  Adding a future private- or session-scoped domain means appending one
  string to that list, not writing new clearing logic. This is why
  `AuthProvider` depends on
  `useQueryClient()`, which in turn means every render of `AuthProvider`
  (including in tests) needs a `QueryClientProvider` ancestor — a
  dependency that cascaded into several pre-existing test files across
  this codebase that had previously rendered `AuthProvider` (or a
  component consuming private-account data) without one.
- **Save/remove mutations optimistically patch other cached status
  queries** on success (`patchStatusCaches`, via
  `queryClient.setQueriesData` with a predicate matching every
  `status`-suffixed key for the current user) so every currently-
  mounted save control across the page updates immediately, not just
  the one that was clicked; the saved-list query is invalidated
  separately rather than patched in place, since patching a paginated,
  sorted list's exact membership/ordering client-side is more failure-
  prone than letting it refetch next time it's actually viewed.
- **Never written to `localStorage`/`sessionStorage`** — saved-resource
  state exists only in the in-memory `QueryClient` cache, exactly like
  every other piece of server state this frontend caches, and is
  discarded on a full page reload the same way the access token is.

## Backend Connectivity

The browser calls the backend directly — see
[ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md) for why this
was chosen over a Next.js proxy layer, and `backend/README.md`'s CORS section
for the resulting `WebCorsConfig`.

## Known Limitations

- No `verificationStatus`, `active`, or cost-type filter on `/resources` —
  the backend doesn't support them publicly yet (`docs/api/README.md`).
- Keyword search (Milestone 6A) has no autocomplete, typo tolerance, or
  relevance ranking — a submit-based, exact-substring, case-insensitive
  match only. No debounced/per-keystroke search either — see ADR-010.
- No category/resource creation forms and no role-management UI —
  `/dashboard` shows the current authenticated account's safe fields, a
  logout control, a Saved Resources section (Milestone 8A), and My
  Resource Submissions/My Correction Reports sections (Milestone 8B,
  now including the review outcome once decided — Milestone 9A). A
  `MODERATOR`/`ADMIN` account additionally sees a "Moderation" nav link
  and the `/moderation` queue/detail/decision UI (Milestone 9A) — see
  "Role-Based Route Guarding" below.
- No notes, folders, or collections on saved resources (Milestone 8A) —
  a flat, unorganized list only; no saved-resource sharing or export.
- No editing a submission or report after it's created (beyond
  withdrawal), no attachments/images, no draft saving, and no way to
  see another user's contributions or any public contribution feed
  (Milestone 8B).
- No reviewer assignment, private moderator notes, bulk review, appeal
  workflow, or audit export in the moderation UI (Milestone 9A) — see
  `docs/milestones/milestone-09a-moderation-workflow.md`'s Known
  Limitations.
- The protected-route guard is UX-layer only; a direct request to
  `/dashboard`'s HTML bypasses nothing real, because the backend never
  trusted the frontend's routing in the first place — see "Authentication
  Architecture" above.
- No proactive-retry-after-401 request wrapper exists yet — the frontend
  tracks expiry proactively (refreshing before a known `expiresIn` elapses)
  rather than reacting to a 401 and replaying the original request. This was
  a deliberate scope trade per ADR-009, not an oversight.
- Responsive/visual verification through Milestone 6 was code-review-based
  (Tailwind breakpoint classes) and curl-based (rendered HTML content), not a
  live graphical browser session — no browser-automation tool was available
  in the development environment used for those milestones. **Resolved in
  Milestone 7B**: a headless-Chromium instance became available and was used
  for genuine pixel-level verification — see "Responsive Verification" above.
- No proactive resource-coordinate administration UI, address geocoding, or
  reverse geocoding — a resource's location is entered only through the
  protected `PUT /resources/{id}/location` API (Milestone 7A). No route
  distance, walking/driving time, or turn-by-turn directions — the map's
  distance display is always straight-line (ADR-012/ADR-013).
