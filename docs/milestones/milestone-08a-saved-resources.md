# Milestone 8A: Saved Resources and Authenticated Dashboard Integration

## Objective

Give an authenticated user (any role — `USER`/`ORGANIZATION`/
`MODERATOR`/`ADMIN`) a private, per-account list of resources they've
saved for later: save/remove an active resource idempotently from a
resource card or its detail page, see saved status on every visible
card without one request per card, and browse a paginated Saved
Resources section on the protected dashboard — while the backend
remains the sole authority for identity, ownership, visibility,
uniqueness, pagination, and authorization.

## Product Value

The first genuinely private, per-account feature this project has
built beyond authentication itself (Milestone 5). Before this
milestone, an authenticated account existed but had nothing to do with
that authentication besides seeing its own email/role on the
dashboard. This closes that gap with the single most requested
"account" feature for a resource directory: come back to a resource you
found earlier without re-searching for it.

## Technical Scope

**Backend** — new package `com.hfxconnect.savedresource`:

- `SavedResource` — focused entity (`id` `BIGINT` identity, plain
  `userId` `UUID`, lazy unidirectional `@ManyToOne CommunityResource
  resource`, `createdAt`). No bidirectional collection on `User` or
  `CommunityResource`, no generic bookmark framework — see
  [ADR-014](../decisions/ADR-014-saved-resources-design.md).
- `SavedResourceRepository` — `existsByUserIdAndResource_Id`,
  `deleteByUserIdAndResource_Id`, `findSavedResourceIds` (batch status),
  and two `JOIN FETCH` paginated queries (`savedAt` descending / `name`
  ascending), each with an explicit `countQuery`.
- `SavedResourceService` — `save`/`remove`/`list`/`status`, allowlisted
  sort (`savedAt`, `name`), page size capped at 100, race-safe save
  (catches `DataIntegrityViolationException` from a concurrent-insert
  race and treats it as success).
- `SavedResourceController` — `PUT`/`DELETE
  /api/v1/users/me/saved-resources/{resourceId}`, `GET
  /api/v1/users/me/saved-resources`, `POST
  /api/v1/users/me/saved-resources/status`. Identity always comes from
  `@AuthenticationPrincipal CurrentUserPrincipal` — no endpoint accepts
  a client-supplied user id.
- `V8__create_saved_resources_table.sql` — `saved_resources` table,
  `FK ... ON DELETE CASCADE` on both `user_id` and `resource_id`,
  `UNIQUE (user_id, resource_id)`, two supporting indexes.
- **A pre-existing CORS gap fixed as part of this milestone**: `PUT`/
  `DELETE` were never in `WebCorsConfig`'s `allowedMethods` list (only
  `GET`/`POST`) — invisible until this milestone's save/remove
  endpoints became the first browser-called routes to actually need
  them (see "Known Limitations").

**Frontend:**

- `lib/api/saved-resources.ts`, `lib/query/use-saved-resources.ts`
  (`useSavedResourceStatusMap`, `useSaveResourceMutation`,
  `useRemoveSavedResourceMutation`), `lib/query/keys.ts`'s
  `savedResourceKeys`.
- `lib/auth/return-to.ts` — `isSafeReturnPath`/`resolveReturnPath`/
  `buildLoginHref`, open-redirect-safe.
- `AuthProvider` — clears every cached saved-resource query on logout
  and on detecting an account switch.
- `components/resources/save-resource-button.tsx`,
  `resource-detail-save-control.tsx` — wired into `ResourceCard`,
  `NearbyResourceCard`, and the resource detail page.
- `components/auth/saved-resources-section.tsx` — the dashboard's new
  Saved Resources section (loading/empty/error+retry/pagination/remove).

## Out of Scope

Organization ownership/claiming, resource submissions, correction
reports, moderation, events, notes/collections/folders on saved
resources, saved-resource sharing, a generic bookmark framework for
non-resource entities, rate limiting, CI/CD, deployment, Milestone 8B.

## Ownership and Authorization Model

Every saved-resource endpoint requires a Bearer access token for any
authenticated, `ACTIVE`-status role — there is no role restriction
beyond "authenticated" (`USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` all
behave identically here). The user id is never accepted from a path,
query, or body parameter anywhere in this feature — it comes solely
from `CurrentUserPrincipal`, resolved from the validated Bearer token
(ADR-009). Saved resources are fully isolated per account: there is no
endpoint, parameter, or admin capability that reveals or modifies
another account's saved resources.

## Saved-Resource API Contract

| Method | Path | Purpose |
|---|---|---|
| `PUT` | `/api/v1/users/me/saved-resources/{resourceId}` | Idempotently save an active resource. `204` whether this call created the relation or it already existed. `404` if no active resource exists with that id. |
| `DELETE` | `/api/v1/users/me/saved-resources/{resourceId}` | Idempotently remove a saved resource. `204` whether this call removed a row or none existed — works regardless of the resource's current active state. |
| `GET` | `/api/v1/users/me/saved-resources` | Paginated list of the caller's saved **active** resources. `sort=savedAt` (default, newest first) or `sort=name` (ascending). Page size capped at 100. |
| `POST` | `/api/v1/users/me/saved-resources/status` | Batch lookup: given up to 100 resource ids, returns which are saved for the caller. Normalizes duplicates; rejects a null entry or more than 100 distinct ids with `400`. |

Full detail: `docs/api/README.md`'s "Saved Resources" section.

## Idempotency and Race Safety

`PUT`/`DELETE` are true idempotent operations, not "succeeds once, then
errors on repeat": saving an already-saved resource, or removing an
already-absent one, both return the same `204` as the first call. The
database's `UNIQUE (user_id, resource_id)` constraint is the
authoritative race guard — a concurrent double-save (two near-
simultaneous requests, e.g. a double-click or two tabs) resolves to one
row and two `204` responses, never a `500` or an unhandled `409`; the
losing insert's `DataIntegrityViolationException` is caught and treated
as success by `SavedResourceService.save`. Verified directly by an
integration test issuing two real concurrent save requests for the
same user/resource pair and asserting exactly one row exists afterward.

## Resource Visibility Behavior

- An active resource can be saved. A missing or inactive resource
  returns the same `404`-equivalent response as everywhere else in this
  API that treats "doesn't exist" and "not currently active" alike.
- Removal is unconditional on the resource's current state — a saved
  resource that has since gone inactive (or, hypothetically, been
  deleted) can always still be removed by its id.
- The saved list excludes inactive resources but does not delete the
  underlying relation — if a saved resource is later reactivated, it
  reappears in the list automatically, with no re-saving required (see
  ADR-014's "Deactivation vs. Deletion").

## Query and Pagination Strategy

Both list-ordering queries (`savedAt` descending, `name` ascending) use
`JOIN FETCH` on `resource` and `resource.category` to avoid N+1 queries
when building each summary row, with an explicit `countQuery` since
Spring Data cannot infer a correct count from a fetch-joined query
automatically. Page size defaults to 20, capped at 100 (matching the
existing resource-list convention); an out-of-range page/size or an
unrecognized `sort` value returns `400`, not a silent clamp.

## Frontend Saved-State Architecture

`useSavedResourceStatusMap(resourceIds)` is called once per listing
container (the resource grid's parent, the nearby/map view, or a
batch-of-one on the detail page) — **never inside an individual card
component** — issuing exactly one `POST .../status` request per page of
results regardless of how many cards are visible. Every saved-resource
query key is rooted in the authenticated user's stable `id`, never the
access token (which rotates on refresh). `AuthProvider` clears every
cached saved-resource query, unconditionally, on `logout()` and on
detecting the authenticated user's id changed (an account switch) —
see [ADR-014](../decisions/ADR-014-saved-resources-design.md).

## Save and Remove Controls

`SaveResourceButton` renders one of three states based on `useAuth()`:
nothing while authentication is still restoring (no flash of the wrong
state), a "Sign in to save {name}" link when signed out, or a labelled
`Save`/`Remove {name} from saved resources` button (`aria-pressed`)
when signed in — disabled and showing "Saving…" while its mutation is
in flight, preventing a duplicate request from a fast double-click.
Present on resource-list cards, nearby/map cards, and the resource
detail page, reusing one shared component rather than three separate
implementations.

## Dashboard Integration

`SavedResourcesSection` on the protected dashboard shows: a loading
state (`role="status"`), an empty state with a "Browse resources" link,
a recoverable error state (`role="alert"` plus "Try again"), paginated
cards (name linking to the detail page, category, cost/verification/
hours badges, saved date, a Remove button), and Previous/Next pagination
controls shown only when more than one page exists. No fabricated
features — no recommendations, no "recent activity," no fake counts;
every element reflects a real API response.

## Authentication and Cache Isolation

Access tokens remain memory-only (unchanged from Milestone 5B) — saved
resources introduce no new token storage. Saved-resource data itself is
never written to `localStorage` or `sessionStorage` at any point,
verified both by dedicated tests and by direct browser-storage
inspection during manual verification. Logging out, or logging in as a
different account in the same browser tab, immediately clears the
previous account's cached saved-resource queries — verified live with
two real accounts (see "Manual Verification").

## Return-to Login Security

The signed-out "Sign in to save" link carries a `returnTo` query
parameter so login returns the visitor to the page they were trying to
save from. `isSafeReturnPath` rejects anything that isn't a genuine
same-origin relative path: absolute URLs (`https://evil.example.com`),
protocol-relative URLs (`//evil.example.com`), and any non-relative-path
input are all rejected before ever being used in a redirect; an invalid
or missing `returnTo` falls back to `/dashboard`. Verified by 10
dedicated unit tests and a live manual check.

## Testing

**Backend** — `./mvnw clean verify`, **554/554 tests pass** (482
inherited unchanged + 1 new CORS-preflight regression test + 71 new
saved-resource tests), authoritative per the Maven Surefire summary:

- `SavedResourceRepositoryIntegrationTest` (17 tests): schema/FK/
  uniqueness/cascade behavior (including a genuine concurrent-insert
  race resolving to one row), every repository query method.
- `SavedResourceServiceIntegrationTest` (25 tests): idempotent save/
  remove, active/inactive/missing-resource handling, pagination/sort
  validation, the batch status endpoint's normalization and limits.
- `SavedResourceApiIntegrationTest` (29 tests): the full role matrix
  (`USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` can all save), per-account
  isolation, status codes for every documented case.
- `CorsConfigurationIntegrationTest` — one new test confirming a real
  `PUT`/`DELETE` preflight against the saved-resources route now
  succeeds (see "Known Limitations").
- `FlywayMigrationIntegrationTest` — updated to assert all 8 migrations.

**Frontend** — `npm test -- --ci`, **327/327 tests pass** (262 inherited
+ 65 new), 0 failures:

- `save-resource-button.test.tsx` (10 tests): all three auth-derived
  states, `aria-pressed` semantics, in-flight disabling/no-duplicate-
  request, error recovery, keyboard operability.
- `saved-resources-section.test.tsx` (9 tests): loading/empty/error/
  content/pagination/remove.
- `use-saved-resources.test.tsx` (7 tests): batch status querying,
  `enabled` guard while signed out, cache patching on save/remove.
- `saved-resources.test.ts` (12 tests) — API client.
- `return-to.test.ts` (10 tests) — every open-redirect rejection case.
- `keys.test.ts` (4 tests) — query-key isolation/stability.
- `auth-provider.test.tsx` — 2 new tests (logout clears cache; account
  switch clears the previous account's cache).
- Updated: `resource-card.test.tsx`, `resource-detail.test.tsx`,
  `resource-list-view.test.tsx`, `nearby-map-view.test.tsx`,
  `resource-explorer.test.tsx`, `nearby-resource-card.test.tsx`,
  `dashboard-content.test.tsx`, `login-form.test.tsx`,
  `site-header.test.tsx`, `mobile-nav.test.tsx`, `client.test.ts`.

`npm run lint` / `npm run typecheck` / `npm run build` (production) —
all clean. `npm audit` — unchanged (4 pre-existing advisories, no
dependency changes in this milestone).

## Manual Verification

Performed against the real running backend (`./mvnw spring-boot:run`,
`DB_PORT=55432`), the real docker-compose PostGIS database, and the
real Next.js dev server, using a genuine headless Chromium session
(Playwright) for reproducible, scripted checks — three real accounts
(an `ADMIN` and two independent `USER` accounts, "userA"/"userB") and
two real resources (one active, one deliberately deactivated) created
through the live API for this pass.

Confirmed live (27/27 scripted checks):

- Public resource browsing works fully signed out; the "Sign in to
  save" link carries a `returnTo` and is present on both list cards and
  the detail page.
- Logging in via that link returns the visitor to the exact page they
  started from.
- An authenticated user can save an active resource; a repeated save
  (via direct API replay) remains successful and creates exactly **one**
  database row.
- Saved state persists across a page reload (session restoration).
- `ORGANIZATION`, `MODERATOR`, and `ADMIN` accounts can all save a
  resource (role matrix, not `USER`-only).
- Saving a nonexistent resource id returns `404`; saving the
  deliberately deactivated test resource also returns `404`.
- The dashboard's Saved Resources section shows the saved resource,
  links to its detail page, and Remove works from the dashboard.
- Save and Remove both work from the detail page and from a resource-
  list card.
- Logging in as a second user (userB) does not show the first user's
  (userA's) saved state — genuine two-user isolation.
- Switching accounts in the same browser tab (logout, then log back in
  as a different user) shows that account's own saved state, not a
  stale cached view of the previous account's.
- Browser `localStorage`/`sessionStorage` inspected directly while
  authenticated with an active saved resource — no access token, no
  saved-resource payload present in either.
- Logging out immediately reverts the resource detail page back to the
  "Sign in to save" state.
- The interactive map (Milestone 7B) continues to work unaffected; no
  unexpected browser console errors on `/resources` in Map view (a
  pre-existing, expected `401` from the unauthenticated session-
  restoration check on page load is not a regression — see below).
- `/login`, `/register` still load; `/dashboard` still redirects an
  unauthenticated caller — Milestone 5 regressions reconfirmed.

## OpenAPI Verification

`GET http://localhost:8080/v3/api-docs`, parsed directly, confirms all
four saved-resource operations are documented with correct summaries,
descriptions, path/body parameters, `security: [{"bearerAuth": []}]`,
and response schemas (`204`/`400`/`401`/`404` referencing the shared
`ApiError` schema where applicable).

## Documentation

- This milestone document and
  [ADR-014](../decisions/ADR-014-saved-resources-design.md).
- `docs/tasks/034` through `037`.
- `docs/api/README.md`, `docs/database/README.md` — new endpoint/table
  sections.
- `docs/architecture/backend-architecture.md`,
  `docs/architecture/frontend-architecture.md`,
  `docs/architecture/security-architecture.md`,
  `docs/architecture/system-overview.md` — updated for the new package,
  data flow, and private-data-isolation model.
- `docs/development-workflow.md`, `docs/development-log/2026-08-03.md`.
- `docs/career/resume-evidence.md`, `docs/career/interview-notes.md`.
- `backend/README.md`, `frontend/README.md`, root `README.md`.

## Security Review

- Every saved-resource endpoint requires a valid Bearer token for an
  `ACTIVE`-status account; identity is always
  `@AuthenticationPrincipal CurrentUserPrincipal`, never a client-
  supplied id (`git grep userId` across the new package confirms no
  endpoint reads a user id from a path/query/body parameter).
- `git grep` across the new frontend files
  (`localStorage`/`sessionStorage`/`console.log`/
  `dangerouslySetInnerHTML`) returns zero matches — saved-resource data
  is never persisted client-side or logged, and no raw HTML injection
  exists anywhere in the new code.
- `returnTo` is validated against absolute-URL, protocol-relative, and
  non-relative-path input before ever being used in a redirect (10
  dedicated tests plus a live check).
- **A genuine pre-existing gap was found and fixed**: `WebCorsConfig`
  only allowed `GET`/`POST` cross-origin methods; this milestone's
  browser-driven `PUT`/`DELETE` saved-resource calls were the first to
  actually exercise it from a real browser, surfacing a CORS preflight
  failure during manual verification. Fixed by adding `PUT`/`DELETE` to
  the allowlist (still no wildcard origin, `allowCredentials` unchanged)
  and covered by a new regression test. See "Known Limitations."

## Privacy Review

Saved resources are classified as private account data:

- Never written to `localStorage`/`sessionStorage`, confirmed by
  dedicated tests and live browser-storage inspection.
- Never included in any public/anonymous API response — every saved-
  resource endpoint requires authentication.
- Fully isolated per account at the database level (`user_id` on every
  row, no cross-account query path exists) and at the frontend cache
  level (query keys rooted in `userId`, cleared on logout/account
  switch).
- No analytics or tracking integration reads or reports saved-resource
  activity.

## Performance Review

- The batch status endpoint means a page of resource cards issues
  exactly one status request, not one per card — verified directly by
  a dedicated test asserting the status-lookup mock is called once
  regardless of how many resource ids a listing contains.
- Both paginated list queries use `JOIN FETCH` to avoid N+1 queries when
  building each summary row (resource + category loaded in the same
  query), with hours batch-loaded the same way the existing resource
  list already does (Milestone 6B's established pattern).
- Page size is capped at 100 server-side regardless of what a caller
  requests.
- No production-scale load test was run — the manual-verification
  dataset was two resources and three accounts, stated honestly rather
  than extrapolated.

## Acceptance Criteria

**Backend**

- [x] `PUT`/`DELETE`/`GET`/`POST status` all implemented exactly as
      specified, identity always from `CurrentUserPrincipal`.
- [x] `V8` migration applied; Hibernate validates, never creates,
      schema (`ddl-auto=validate` unchanged).
- [x] Database uniqueness constraint resolves a concurrent-save race to
      success, not an error (verified by a real concurrent-request
      test).
- [x] Active-only save, unconditional removal, active-only list with
      relation-preserving exclusion of inactive resources.
- [x] 554/554 backend tests pass (`./mvnw clean verify`).

**Frontend**

- [x] Batched saved-status lookup — one request per listing, never per
      card.
- [x] Save/remove controls on list cards, nearby/map cards, and the
      detail page.
- [x] Signed-out "Sign in to save" link with a validated,
      open-redirect-safe `returnTo`.
- [x] Dashboard Saved Resources section: loading/empty/error+retry/
      pagination/remove, no fabricated features.
- [x] Logout and account-switch clear the private saved-resource cache.
- [x] 327/327 frontend tests pass (`npm test -- --ci`); lint/typecheck/
      build clean.

**Manual verification** — see "Manual Verification" above; 27/27
scripted checks against the real running stack, including genuine
two-user isolation and account-switch cache verification.

**Documentation** — see "Documentation" above; all listed files
updated or added.

## Known Limitations (as of Milestone 8A)

- No notes, folders, or collections on saved resources — a flat,
  unorganized list only.
- No saved-resource sharing or export.
- No rate limiting on any saved-resource endpoint (consistent with the
  rest of the API today — see ADR-008's own honest limitations
  section).
- No production-scale load test.
- A real, pre-existing gap was found and fixed during this milestone's
  own manual verification, not by the automated backend/frontend suites
  alone: `WebCorsConfig` had never allowed `PUT`/`DELETE` cross-origin
  methods (only `GET`/`POST`) since it was first introduced (Milestone
  5C) — invisible until this milestone's save/remove endpoints became
  the first browser-driven routes to actually need them. Fixed by
  adding `PUT`/`DELETE` to the allowlist, with a new regression test
  (`CorsConfigurationIntegrationTest`) added before this branch was
  pushed.

## Risks

| Risk | Mitigation |
|---|---|
| A concurrent double-save (double-click, two tabs) could produce a duplicate row or a visible error | The database's `UNIQUE (user_id, resource_id)` constraint is the authoritative guard; the service catches the losing insert's `DataIntegrityViolationException` and treats it as success, verified by a real concurrent-request integration test |
| Saved-resource private data could leak across accounts in the shared frontend `QueryClient` cache after a logout or account switch | `AuthProvider` unconditionally clears every `saved-resources`-rooted query on both events, verified by dedicated tests and a live two-account manual check |
| A batched-status implementation could regress into one request per card as the codebase grows | `useSavedResourceStatusMap` is called once per listing container by convention (documented in ADR-014); a dedicated test asserts the lookup mock's call count is unchanged regardless of card count |
| The "Sign in to save" return-path could become an open-redirect vector if validation is ever loosened | `isSafeReturnPath` is unit-tested against every rejection category (absolute URL, protocol-relative, non-relative-path) independently of any single call site |
| A cross-origin browser call using a method the CORS policy doesn't allow fails silently from the app's perspective (a generic network error, not a clear backend error response) | Found exactly this failure mode during this milestone's own manual verification (not caught by any automated test, since backend integration tests call the API directly rather than through a browser's CORS layer); fixed and covered by a new preflight regression test |

## Completion Summary

All planned Milestone 8A deliverables were completed and verified three
ways: 554 automated backend tests (482 inherited unchanged, 1 new CORS
regression test, 71 new saved-resource tests) plus 327 automated
frontend tests (262 inherited unchanged, 65 new), a full manual pass
against the real running stack using a genuine headless-Chromium
session with three real accounts and genuine two-user isolation
verification, and a full regression pass confirming Milestones 5-7B
remain unaffected. One real, pre-existing bug (a CORS `allowedMethods`
gap that predates this milestone but was only now exercised by a
browser-driven `PUT`/`DELETE` call) was found during manual
verification, fixed, and covered by a new regression test before this
branch was pushed.
