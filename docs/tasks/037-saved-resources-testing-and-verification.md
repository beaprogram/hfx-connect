# Task 037: Saved Resources Testing and Verification

## Objective

Bring Milestone 8A to a fully verified, documented state: complete
frontend test coverage for Task 036's work, a real-browser manual
verification pass with genuine two-user isolation checks, and the
security/privacy/performance review the milestone requires.

## Context

The fourth and final task completing Milestone 8A. Depends on Tasks
034-036 being feature-complete.

## Scope

- Frontend test files: `save-resource-button.test.tsx` (10),
  `saved-resources-section.test.tsx` (9), `use-saved-resources.test.tsx`
  (7), `saved-resources.test.ts` (12), `return-to.test.ts` (10),
  `keys.test.ts` (4), plus 2 new `auth-provider.test.tsx` tests and
  updates to 11 pre-existing test files that needed an auth mock and/or
  `QueryClientProvider` wrapper after `SaveResourceButton`/
  `SavedResourcesSection` introduced `useAuth()`/`useQueryClient()`
  calls into previously hook-free component trees.
- Real-browser manual verification (headless Chromium via Playwright):
  signed-out save-link and `returnTo` round trip, save/remove
  idempotency (including a duplicate-row check via direct database
  query), the full role matrix, active/inactive/missing-resource
  `404` behavior, dashboard save/remove/pagination, **genuine two-user
  isolation** (a second real account's session never shows the first
  account's saved state), account-switch cache clearing within one
  browser tab, logout clearing UI state, browser-storage inspection,
  and unrelated-feature (map, `/login`, `/register`, `/dashboard`)
  regression checks.
- OpenAPI verification: fetched and parsed the live
  `/v3/api-docs` document, confirming all four saved-resource
  operations are documented with correct schemas, parameters, and
  security requirements.
- **A real bug found and fixed during this task**: a `WebCorsConfig`
  `allowedMethods` gap (missing `PUT`/`DELETE`) that predates this
  milestone, surfaced by the first browser-driven `PUT`/`DELETE` calls
  this codebase has ever made. Fixed, with a new
  `CorsConfigurationIntegrationTest` regression test.
- Security, privacy, and performance review write-ups (see the
  milestone document).

## Out of Scope

New product features — this task is verification and documentation
only.

## Acceptance Criteria

- [x] 327/327 frontend tests pass (262 inherited unchanged + 65 new),
      authoritative per `npm test -- --ci`.
- [x] 554/554 backend tests pass, authoritative per
      `./mvnw clean verify` (482 inherited + 71 new from Task 035 + 1
      new CORS regression test from this task).
- [x] `npm run lint`, `npm run typecheck`, and `npm run build` all pass
      cleanly.
- [x] Manual verification performed against the real running stack,
      not simulated — 27/27 scripted checks, including genuine
      two-user isolation with two independently registered accounts.
- [x] A real bug (the CORS `allowedMethods` gap) was found during this
      verification pass, fixed, and covered by a dedicated regression
      test before the branch was pushed.
- [x] `git grep` review confirms no saved-resource data or access token
      in `localStorage`/`sessionStorage`, no `console.log`, and no
      `dangerouslySetInnerHTML` anywhere in the new code.
- [x] OpenAPI document confirmed to describe all four endpoints
      accurately.

## Evidence

Commits on branch `milestone/08a-saved-resources`; see
`docs/milestones/milestone-08a-saved-resources.md` for the complete
testing, manual-verification, security, privacy, and performance review
sections.
