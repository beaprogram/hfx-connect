# Task 036: Frontend Saved Resources Integration

## Objective

Build the frontend saved-resources experience on top of Task 035's
API: batched saved-status lookups, save/remove controls, the dashboard
section, and secure sign-in return-path handling.

## Context

The third task of Milestone 8A. Depends on Task 035's API contract
being stable.

## Scope

- `lib/api/saved-resources.ts` — `saveResource`/`removeSavedResource`/
  `getSavedResources`/`getSavedResourceStatus`, reusing the existing
  `client.ts` (extended with `putNoContent`/`deleteNoContent`).
- `lib/validation/schemas.ts` — response schemas for all three
  saved-resource response shapes.
- `lib/query/keys.ts` — `savedResourceKeys` factory, rooted in
  `userId`, with `status` sorting the id list for cache-key stability.
- `lib/query/use-saved-resources.ts` — `useSavedResourceStatusMap`
  (one batched request per listing, `enabled` guard for signed-out/
  empty input), `useSaveResourceMutation`/`useRemoveSavedResourceMutation`
  (patch other cached status queries on success via
  `setQueriesData`, invalidate the saved-list query).
- `lib/auth/return-to.ts` — `isSafeReturnPath`/`resolveReturnPath`/
  `buildLoginHref`.
- `AuthProvider` — clears cached saved-resource queries on `logout()`
  and on detecting an account switch in `applySession()`.
- `components/resources/save-resource-button.tsx` — the three-state
  (loading/signed-out/signed-in) control, wired into `ResourceCard`,
  `NearbyResourceCard`, and a new `resource-detail-save-control.tsx`
  client wrapper for the server-component detail page.
- `components/auth/saved-resources-section.tsx` — the dashboard
  section, wired into `dashboard-content.tsx`.
- `login-form.tsx`/`login/page.tsx` — `returnTo`-aware redirect after
  login, wrapped in `<Suspense>` for `useSearchParams()`.

## Out of Scope

New backend endpoints — this task consumes Task 035's API as-is.

## Design Decisions

See [ADR-014](../decisions/ADR-014-saved-resources-design.md) for the
batched-status, query-key-isolation, cache-clearing, and
return-path-security rationale.

## Acceptance Criteria

- [x] Exactly one saved-status request per listing regardless of card
      count (batched, never per-card).
- [x] Save/remove controls present and functional on list cards,
      nearby/map cards, and the detail page.
- [x] Logout and account switch clear the private saved-resource
      cache.
- [x] `returnTo` rejects absolute URLs, protocol-relative URLs, and any
      non-relative-path input.
- [x] Dashboard Saved Resources section renders loading/empty/error/
      content/pagination states correctly.
- [x] `npm run lint`/`typecheck`/`build` all pass.

## Evidence

Commits on branch `milestone/08a-saved-resources`; see
`docs/milestones/milestone-08a-saved-resources.md`'s "Frontend
Saved-State Architecture" and "Save and Remove Controls" sections.
