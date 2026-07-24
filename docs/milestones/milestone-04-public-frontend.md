# Milestone 4: Public Frontend

## Objective

Build the first recruiter-facing HFX Connect user interface: a homepage, a
filterable/sortable/paginated public resource list, and a resource detail
page, all consuming the real Category (Milestone 3A) and Resource
(Milestone 3C) APIs — no hard-coded resource data as the primary application
data source, and no frontend controls for backend capabilities that don't
exist yet (free-text search, distance, open-now, cost, or
verification-status filtering — all later milestones).

## A Note on This Session's Environment

Partway through this milestone, the development machine's internal disk
filled to within ~1.6GB of capacity, which caused filesystem operations
across the project's `node_modules` tree (`npm test`, `tsc --noEmit`, even a
plain `cat` of a small file) to hang indefinitely rather than fail — the
disk itself, not this project's code, was the constraint. With the project
owner's direction, the entire project (source and `.git` history, verified
byte-for-byte via `diff -rq` after transfer) was moved to an external SSD
mid-session, and all further work — implementation, `npm install`,
`./mvnw`, linting, testing, and the production build — happened from there.
Every command hung on the original disk and completed in seconds once moved;
this confirms it was an environment issue, not a defect in the milestone's
own tooling or configuration. See `docs/development-log/2026-07-24.md` for
the full, exact sequence.

## Product Value

The first version of HFX Connect a non-technical visitor, recruiter, or
interviewer can actually browse: real Halifax community-resource listings,
filterable by category, with honest verification-status labeling (most
listings currently say "Not yet verified," never implying verification that
hasn't happened) and full keyboard/screen-reader accessibility. It completes
the public-facing half of the "browse resources" journey from
`docs/product/user-journeys-and-stories.md` that Milestone 3C's API made
possible but had no interface for yet.

## Technical Scope

- **Routes:** `/` (hero, category grid, resource preview, verification
  explanation), `/resources` (paginated/filterable/sortable list),
  `/resources/[slug]` (full detail), plus `not-found.tsx`/`error.tsx`/
  `loading.tsx` at the appropriate route segments.
- **Typed API client** (`lib/api/`): a single `getJson` function centralizing
  query encoding, non-2xx → `ApiRequestError` mapping, and Zod
  response-shape validation → `ApiResponseShapeError`; `categories.ts` and
  `resources.ts` expose only the operations actually used.
- **Runtime validation** (`lib/validation/schemas.ts`): Zod schemas mirroring
  the real backend contract, checked against a running backend's
  `/v3/api-docs` before writing any schema — not from memory, and not from
  the richer schema this milestone's own brief described (see "A Note on
  Scope" below).
- **TanStack Query** (`lib/query/`): server-side `prefetchQuery` +
  `<HydrationBoundary>` for the initial `/resources` load, client-side
  `useQuery` for filter/sort/pagination changes; query keys include every
  parameter that changes the result set; one retry for a genuine failure,
  zero for a cancelled request.
- **URL state**: `page` (0-based, matching the backend), `categoryId`,
  `sort` — every invalid value falls back to a safe default rather than
  erroring (see `docs/architecture/frontend-architecture.md`).
- **Backend CORS** (`WebCorsConfig`, `CORS_ALLOWED_ORIGINS`): a minimal,
  environment-configured allowlist so the browser can call the backend
  directly — see [ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md).
- **Accessibility**: skip link (pre-existing, verified still works), one
  `<h1>` per page, labelled filter controls, a real keyboard-operable mobile
  disclosure nav, `aria-live` result announcements, icon+text status badges
  (never colour alone), and a "no nested interactive controls" card pattern
  (`after:absolute after:inset-0` rather than a second wrapping link).
- **Wireframes**: `docs/wireframes/` — homepage, resource list, resource
  detail, mobile navigation, and the shared loading/empty/error/not-found
  state catalogue, written before implementation.

## A Note on Scope: No Accessibility-Information Section

The task brief asked for an "accessibility information" section on the
resource detail page. The actual `ResourceResponse` (checked against the live
backend, not assumed) has no such field — `docs/database/README.md` and
`docs/milestones/milestone-03c-public-resource-api.md` confirm the resource
schema was deliberately built without one. Inventing a section for data the
API cannot supply would violate this same milestone's own "no fabricated
values" requirement, so it's omitted; see
`docs/wireframes/resource-detail.md`.

## Out of Scope

Resource/category creation UI, update/delete UI, authentication, saved
resources, submissions, correction reports, moderation, organizations,
events, maps/geospatial search, free-text search, open-now/distance/cost/
verification-status filtering, CI/CD changes, and production deployment —
all later milestones, per the brief.

## Acceptance Criteria

- [x] Homepage explains the product, links to `/resources`, shows real
      categories and a real resource preview (or an honest error/empty
      state per section, independently — one section's failure doesn't
      block the other).
- [x] `/resources` lists active resources, supports category filtering and
      allowlisted sorting, keeps state in the URL, paginates, and has
      distinct loading/empty/error states.
- [x] `/resources/[slug]` shows full detail, uses `notFound()` for an
      unknown/deactivated slug, formats enums/dates/addresses/contact links
      safely, and never renders an empty section for an absent optional
      field.
- [x] No frontend control exists for a backend filter that doesn't exist.
- [x] No hard-coded resource data is used as the primary data source (a
      handful of category/resource records were created through the real
      local API during earlier milestones' manual verification, and appear
      here only because this milestone reads real data — see the Data
      Integrity note below).
- [x] Every interactive control is keyboard-operable; mobile nav is a real
      disclosure pattern; no nested interactive controls anywhere.
- [x] `./mvnw clean verify` (backend): 149/149 tests, 0 failures/errors,
      including 2 new CORS tests — no backend regression.
- [x] Frontend: 18/18 test suites, 82/82 tests passing; `npm run lint`,
      `npm run typecheck`, and `npm run build` all clean.
- [x] CORS allows the frontend's configured origin and rejects an arbitrary
      one — verified with a real preflight request, not just code review.

## Data Integrity

No fabricated production records were added. The category ("Study Spaces 3C
Test") and resource ("Halifax Central Library") visible in this milestone's
manual verification were created through the real `POST` endpoints during
Milestone 3C's own manual verification, are clearly synthetic/development
values, and are the same kind of local-only test data every prior milestone
has used — not new fictional Halifax listings introduced by this milestone.

## Testing

82 Jest/React Testing Library tests across 18 suites: the typed API client
(success/error/shape-mismatch/query-encoding/`AbortSignal`), URL-param
parsing and defaulting, formatting helpers (labels, dates, addresses,
city/province), every shared component (`ResourceCard`, `CategoryCard`,
status badges, `MobileNav`'s full keyboard/focus behavior, `Pagination`'s
boundary states), the resource-list experience (loading/success/empty/
error/category-mismatch, wired through a real `QueryClientProvider` with
mocked API calls), and the resource-detail component (full rendering,
missing-optional-field omission, safe contact links, the deliberate absence
of an accessibility section). Two real defects were caught and fixed by
these tests, not just by writing them:

1. `ResourceListView` rendered **two** identical "Reset filters" links
   simultaneously when a category filter produced zero results (one in the
   filter-summary line, one inside the empty state) — a real duplicate-link
   accessibility/UX issue, not a test artifact. Fixed by removing the
   redundant one from the empty state.
2. A test itself (not app code) used an ambiguous regex
   (`/example\.org/`) that matched both the mailto link
   (`info@example.org`) and the website link (`https://example.org`) —
   tightened to `/^https:\/\/example\.org/`.

## Manual Verification

No browser-automation tool was available in this environment, so manual
verification was HTTP-level (`curl` against the real running dev server and
backend) plus code review, not a graphical browser session — recorded
honestly here rather than claimed as a full visual walkthrough:

- Homepage, `/resources`, and `/resources/[slug]` all return `200` with the
  real category/resource content server-rendered into the initial HTML
  (verified by inspecting the raw response body, not just the status code).
- Category filtering (`?categoryId=6`) renders the correct "Filtering by
  Study Spaces 3C Test" summary and the matching resource.
- An unknown `categoryId` renders the "couldn't be found" fallback and still
  shows all resources rather than erroring.
- Invalid `page`/`sort` values (`page=-5`, `sort=bogus`) render normally
  against corrected defaults rather than erroring.
- An out-of-range page (`page=50`) renders the empty state, not an error.
- A nonexistent resource slug renders the not-found UI content (at
  streamed-response HTTP `200`, per documented Next.js behavior — see
  `docs/architecture/frontend-architecture.md`).
- Stopping the backend process: the homepage's two independent sections each
  show their own "couldn't be loaded" message (verified directly); the
  `/resources` page's own client-side error transition happens after
  browser-side hydration/JS execution, which `curl` cannot observe — that
  exact path is instead covered by an automated test
  (`resource-list-view.test.tsx`'s "shows an inline error when the resource
  request fails").
- Restarting the backend: both pages recover immediately (`cache: "no-store"`
  fetches, no stale caching) — verified directly.
- CORS: a real preflight and a real `GET` from `Origin: http://localhost:3000`
  both succeed with the correct `Access-Control-Allow-Origin` header.
- OpenAPI docs, health, and the Category API all still respond correctly
  (backend regression, from the running server, not just the test suite).

Responsive behavior was verified through the Tailwind breakpoint classes
actually used (`grid-cols-1 sm:grid-cols-2 lg:grid-cols-3`, `hidden sm:block`
for desktop nav, `flex flex-wrap` for the filter form) rather than pixel
measurements at each width — see
`docs/architecture/frontend-architecture.md`'s Known Limitations.

## Documentation Updated

`docs/wireframes/` (new: `README.md`, `homepage.md`, `resource-list.md`,
`resource-detail.md`, `mobile-navigation.md`, `states.md`),
`docs/architecture/frontend-architecture.md` (new),
[ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md) (new),
`docs/development-log/2026-07-24.md`, `docs/tasks/014-...` through
`docs/tasks/018-...`, `frontend/README.md`, `backend/README.md` (CORS
section), root `README.md`, `frontend/.env.example` (new),
`backend/.env.example` (`CORS_ALLOWED_ORIGINS`), `docs/career/resume-evidence.md`,
`docs/career/interview-notes.md`.

## Completion Summary

All planned Milestone 4 deliverables were completed: a real, tested,
accessible public browsing experience over the real Category and Resource
APIs, with wireframes written before implementation, CORS handled as a
deliberate, minimal, documented backend decision (ADR-006), and two genuine
defects (not just test-writing busywork) caught by the test suite and fixed
before commit. A significant, undocumented-in-advance environmental problem
— the development disk filling to near-capacity mid-session — was resolved
by relocating the entire project to external storage with the project
owner's explicit direction, verified for integrity, and is documented above
rather than silently worked around. No authentication, search, maps, or
resource-creation UI was introduced, consistent with scope.
