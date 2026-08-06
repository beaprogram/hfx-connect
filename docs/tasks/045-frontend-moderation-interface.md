# Task 045: Frontend Moderation Interface

## Objective

Build the moderator-facing queue, detail, and decision-form UI, wire it
behind a role-aware route guard, and integrate the resulting outcomes
into the existing owner-facing dashboard.

## Context

Final implementation task of Milestone 9A. `ModerationRoute` is a
distinct component from `ProtectedRoute` (Milestone 5C) — it adds a
role check `ProtectedRoute` deliberately doesn't make, and shows a
genuine "access denied" state (not a redirect) for a signed-in but
insufficient-role account, since that account *is* authenticated; the
problem is its role.

## Scope

- `lib/api/moderation.ts`, `lib/query/use-moderation.ts`,
  `lib/query/keys.ts`'s `moderationKeys` (rooted in a fixed
  `"moderation"` prefix, not `userId` — this is role-gated shared
  state, not per-account data, but still cleared on logout/account-switch
  for the same reason per-account data is).
- `components/moderation/moderation-route.tsx` — role guard.
- `components/moderation/resource-submission-queue.tsx`,
  `correction-report-queue.tsx`, `moderation-tabs.tsx` — filtered,
  sorted, paginated queues behind an accessible ARIA tabs pattern.
- `components/moderation/resource-submission-review-detail.tsx`,
  `correction-report-review-detail.tsx`,
  `moderation-decision-form.tsx`, `correction-approval-form.tsx`,
  `current-resource-state-response`-driven current-vs-proposed table,
  `moderation-audit-history.tsx`.
- Routes: `/moderation`, `/moderation/resource-submissions/[id]`,
  `/moderation/correction-reports/[id]`.
- `AuthNav`/`MobileNav` — role-conditional "Moderation" link.
- `AuthProvider`'s `PRIVATE_QUERY_KEY_PREFIXES` extended with
  `"moderation"`.
- Dashboard detail pages (`resource-submission-detail.tsx`/
  `correction-report-detail.tsx`) — the exact specified outcome copy
  for `APPROVED`/`REJECTED`/`WITHDRAWN`.

## Out of Scope

Backend — Tasks 042-044.

## Design Decisions

The correction-approval form disables (rather than hides) the
"apply proposed changes" checkbox for `OPERATING_HOURS`/
`DUPLICATE_RESOURCE`, with an inline explanation, rather than silently
omitting it — a moderator should see *why* the option isn't available,
not wonder if it's a bug. `deactivateResource` is shown only for
`RESOURCE_CLOSED`, since it is meaningless (and rejected server-side)
for any other issue type.

## Acceptance Criteria

- [x] Signed-out → redirect to `/login` with a validated `returnTo`;
      `USER`/`ORGANIZATION` → access-denied state, not a redirect;
      `MODERATOR`/`ADMIN` → full access; no protected-content flash in
      any case.
- [x] Both queues default to `PENDING_REVIEW`, oldest first; filters/
      sort/pagination all re-query correctly.
- [x] Decision forms validate the reason client-side, surface backend
      field/business errors accessibly, and prevent double-submit.
- [x] A live browser-driven approval (real click-through, not a mocked
      API call) shows the resulting resource link.
- [x] Dashboard shows the exact specified copy for every terminal
      status.

## Evidence

`frontend/src/components/moderation/*.test.tsx`,
`lib/api/moderation.test.ts`, `lib/query/use-moderation.test.tsx`,
`components/auth/auth-nav.test.tsx`; 43-check manual verification
script (browser section) — see
`docs/milestones/milestone-09a-moderation-workflow.md`. Commits on
branch `milestone/09a-moderation-workflow`.
