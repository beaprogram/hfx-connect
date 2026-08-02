# Task 033: Frontend Map Testing and Responsive Verification

## Objective

Bring Milestone 7B to a fully verified, documented state: complete
automated test coverage, a real-browser manual verification pass across
viewport widths, and the accessibility/security/privacy/performance
review the milestone requires.

## Context

The fourth and final task completing Milestone 7B. Depends on Tasks
030-032 being feature-complete.

## Scope

- `ResourceExplorer` integration tests (`resource-explorer.test.tsx`).
- `MapSearchProvider` state-machine tests, including the URL-mirroring
  regression test for the detail-page leak bug found during manual
  verification.
- `formatDistanceAway` unit tests.
- Real-browser manual verification (headless Chromium via Playwright,
  with real geolocation permission/position mocking): default centre,
  attribution, hydration cleanliness, geolocation success/denial,
  clustering/spiderfy/selection, filter combination, detail-page
  navigation and back-navigation state preservation, storage inspection,
  four-viewport-width responsive screenshots, and unrelated-feature
  (`/login`, `/register`, `/dashboard`) regression checks.
- Security, privacy, and performance review write-ups (see the milestone
  document).

## Out of Scope

New product features — this task is verification and documentation only.

## Acceptance Criteria

- [x] 262/262 frontend tests pass (177 inherited unchanged + 85 new),
      authoritative per `npm test -- --ci`.
- [x] 482/482 backend tests pass unchanged, authoritative per
      `./mvnw clean verify`; zero backend files modified.
- [x] `npm run lint`, `npm run typecheck`, and `npm run build` all pass
      cleanly.
- [x] Manual verification performed against the real running stack, not
      simulated — see `docs/milestones/milestone-07b-interactive-map.md`'s
      "Manual Verification" section for the full list.
- [x] A real bug (the `view`/`radiusKm` URL leak onto the resource-detail
      page) was found during this verification pass, fixed, and covered
      by a dedicated regression test before the branch was pushed.
- [x] Responsive layout confirmed at 375px, 768px, 1024px, and 1440px.
- [x] `git grep` review confirms no coordinate/token storage or logging
      anywhere in the new frontend code.

## Evidence

Commits on branch `milestone/07b-interactive-map`; see
`docs/milestones/milestone-07b-interactive-map.md` for the complete
testing, manual-verification, security, privacy, and performance review
sections.
