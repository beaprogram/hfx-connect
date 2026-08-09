# Task 048: Frontend Organization and Admin Management Interface

## Objective

Give an `ORGANIZATION` account a protected dashboard to create/edit its
profile, view resources it owns, and manage its ownership claims; give
an `ADMIN` account queue/detail pages to verify organizations and
decide ownership claims; and let any visitor see safe organization
attribution and a working "Claim this listing" control on an eligible
public resource's detail page.

## Context

Third and final task of Milestone 10A, consuming Tasks 046-047's
backend contract. `/admin/organizations`/`/admin/resource-claims` are
the first `/admin`-prefixed frontend routes in this codebase — built a
new `AdminRoute` guard (`ADMIN`-only) rather than extending
`ModerationRoute` (`ADMIN`/`MODERATOR`), matching the backend's own
`ADMIN`-only authorization for these routes. Reused
`ModerationDecisionForm` directly for every verify/reject/suspend/
approve/reject reason form — it was already a fully generic,
presentation-only reason form with no moderation-specific coupling, so
writing a second, identically-shaped component would have duplicated
it for no reason.

## Scope

- `lib/api/client.ts` — new `patchJson` (this codebase's first `PATCH`
  request), used by the organization-profile update contract.
- `lib/api/organization.ts`, `lib/query/use-organization.ts`,
  `lib/query/keys.ts`'s `organizationKeys` (rooted in `userId` — private,
  per-account data), `publicOrganizationKeys` (not cleared on logout —
  public data), `adminOrganizationKeys`/`adminOwnershipClaimKeys`
  (not rooted in `userId` — role-gated shared admin state, the same
  posture `moderationKeys` already established).
- `lib/validation/schemas.ts` — organization/claim/audit schemas, plus
  an optional `organization` summary added to `resourceResponseSchema`
  and `nearbyResourceSummaryResponseSchema` only (matching the
  backend's own response shapes exactly — never on the compact list
  card).
- `components/organization/` — `organization-route.tsx`
  (`ORGANIZATION`-only guard), `admin-route.tsx` (`ADMIN`-only guard),
  `organization-dashboard.tsx` (create-profile form or status overview,
  depending on whether a profile exists yet), `organization-profile-form.tsx`,
  `organization-profile-page.tsx`, `organization-resources-list.tsx`,
  `organization-claims-list.tsx` (with withdraw), `organization-nav.tsx`,
  `organization-status-badge.tsx`, `organization-audit-history.tsx`,
  `claim-resource-control.tsx`, `admin-organization-queue.tsx`,
  `admin-organization-detail.tsx`, `admin-ownership-claim-queue.tsx`,
  `admin-ownership-claim-detail.tsx`.
- Routes: `/organization`, `/organization/profile`,
  `/organization/resources`, `/organization/claims`,
  `/admin/organizations`, `/admin/organizations/[id]`,
  `/admin/resource-claims`, `/admin/resource-claims/[id]`.
- `AuthNav`/`MobileNav` — "Organization" link for `ORGANIZATION`
  accounts, "Administration" link for `ADMIN` accounts.
- `AuthProvider`'s private-cache clearing extended to `"organization"`,
  `"admin-organizations"`, `"admin-ownership-claims"` — `"public-
  organization"` deliberately excluded.
- `components/resources/resource-detail.tsx` — inline safe attribution
  when `resource.organization` is present; `ClaimResourceControl`
  (client-boundary component, mirroring `ResourceDetailSaveControl`'s
  "thin client boundary so the server-rendered page stays a Server
  Component" pattern) when the resource is unowned and active.

## Out of Scope

Backend — see Tasks 046-047. Direct organization-managed resource
editing, event management UI.

## Design Decisions

`ClaimResourceControl` fetches the current account's own organization
profile client-side and only ever renders anything for a signed-in
`ORGANIZATION` account whose own profile is currently `VERIFIED` — a
UX convenience, never the authorization boundary; the backend
independently re-checks role, verification, and resource ownership on
the actual claim request. `OrganizationDashboard` never renders a
"verified" claim before the backend's own `verificationStatus` field
says so — no premature or optimistic status display.

## Acceptance Criteria

- [x] `OrganizationRoute`/`AdminRoute` both show a loading state during
      session restoration, redirect only when genuinely signed out,
      and show a distinct "access denied" (not a redirect) for a
      signed-in wrong-role account — no content flash, confirmed by
      component tests for every role.
- [x] Creating a profile never submits a verification status, owner
      id, or any field beyond the editable contact/name set — confirmed
      by an automated test inspecting the actual submitted payload.
- [x] The claim control only ever appears for a `VERIFIED` organization
      account on an unowned, active resource — confirmed by component
      tests covering every other role/state combination.
- [x] Cache invalidation confirmed: admin decisions invalidate the
      admin queue/detail, the owner's own profile, the public profile,
      and public resource responses as applicable; logout/account
      switch clears every private organization/admin prefix while
      sparing the public organization cache.
- [x] `npm run lint`, `npx tsc --noEmit`, and `npm run build` all clean.

## Evidence

Commits on branch `milestone/10a-organization-management`; see
`docs/milestones/milestone-10a-organization-management.md`.
