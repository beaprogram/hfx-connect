# Task 041: Dashboard Contribution Tracking and Verification

## Objective

Bring Milestone 8B to a fully verified, documented state: the
dashboard's two new contribution sections and detail pages, a
real-browser manual verification pass with genuine two-user isolation
checks, and the security/privacy/performance review the milestone
requires.

## Context

The fourth and final task completing Milestone 8B. Depends on Tasks
038-040 being feature-complete.

## Scope

- `components/contributions/resource-submissions-section.tsx`,
  `correction-reports-section.tsx` — dashboard sections following
  `SavedResourcesSection`'s established loading/empty/error+retry/
  pagination structure exactly.
- `components/contributions/resource-submission-detail.tsx`,
  `correction-report-detail.tsx` — owned-item detail pages with a
  Withdraw action for still-`PENDING_REVIEW` items, and the identical
  generic error state for a nonexistent or not-owned id.
- `components/contributions/contribution-status-badge.tsx` — shared,
  never-colour-alone status presentation.
- `dashboard-content.tsx` wired to render both new sections;
  `AuthProvider`'s private-cache clearing extended to cover both new
  query-key prefixes.
- Real-browser manual verification (headless Chromium via Playwright,
  plus direct API calls for the full role matrix): signed-out
  return-to-login for both new protected routes, full role-matrix
  submission/report creation, genuine two-user list/detail isolation,
  duplicate-pending conflicts, invalid/inactive category and resource
  handling, explanation-only reports, a live confirmation that a
  submitted correction report leaves the real resource unchanged,
  dashboard populated/empty states, account-switch cache isolation,
  browser-storage inspection, and unrelated-feature (saved resources,
  map, login/register/dashboard-guard) regression checks.
- **Two real, pre-existing test-isolation bugs found and fixed during
  this task**, neither in this milestone's own new code: a
  `ResourceApiIntegrationTest` list test and a
  `CategoryApiIntegrationTest` inactive-category-filter test both
  implicitly assumed a pristine shared test database that had already
  stopped being true in earlier milestones; and five `openNow`-filter
  test fixtures across two files broke deterministically only within
  the first hour after midnight America/Halifax. All fixed with
  dedicated regression coverage.
- Security, privacy, and performance review write-ups (see the
  milestone document).

## Out of Scope

New product features — this task is verification and documentation
only.

## Acceptance Criteria

- [x] 406/406 frontend tests pass (327 inherited unchanged + 79 new),
      authoritative per `npm test -- --ci`.
- [x] 639/639 backend tests pass, authoritative per
      `./mvnw clean verify` (554 inherited + 85 new from Tasks 039-040).
- [x] `npm run lint`, `npm run typecheck`, and `npm run build` all pass
      cleanly.
- [x] Manual verification performed against the real running stack —
      41/41 scripted checks, including genuine two-user isolation with
      two independently registered accounts and a live confirmation
      that a correction report never modifies its target resource.
- [x] Two real, pre-existing test bugs (unrelated to this milestone's
      own feature code) were found during this verification pass,
      fixed, and left with more robust regression coverage before the
      branch was pushed.
- [x] `git grep` review confirms no contribution data or access token
      in `localStorage`/`sessionStorage`, no `console.log`, no
      `dangerouslySetInnerHTML`, and no moderator/approval endpoint
      anywhere in the new code.
- [x] OpenAPI document confirmed to describe all seven new operations
      accurately.

## Evidence

Commits on branch `milestone/08b-submissions-corrections`; see
`docs/milestones/milestone-08b-submissions-corrections.md` for the
complete testing, manual-verification, security, privacy, and
performance review sections.
