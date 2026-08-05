# Task 039: Resource Submission API and Form

## Objective

Implement the full resource-submission backend surface on top of Task
038's schema and entity, plus the frontend form and dashboard
integration that consume it.

## Context

The second task of Milestone 8B. Depends on Task 038's
`ResourceSubmission` entity and migration being in place.

## Scope

**Backend:**

- `ResourceSubmissionRepository` — owner-scoped, category-`JOIN
  FETCH`-ing list queries (three sort orders) and an owner-scoped
  single-item lookup.
- `ResourceSubmissionValidation` — reuses `ResourceValidation`'s
  field-level helpers; normalizes the name for duplicate-pending
  matching.
- `ResourceSubmissionService` — `create` (category active/exists
  checks, race-safe duplicate-pending handling via
  `DataIntegrityViolationException`), `list`, `get`, `withdraw`
  (explicit `saveAndFlush`).
- `ResourceSubmissionController` — `POST`/`GET`/`GET {id}`/
  `POST {id}/withdraw` under `/api/v1/users/me/resource-submissions`.
- `ResourceSubmissionNotFoundException`,
  `ResourceSubmissionConflictException`.
- Full test suite: `ResourceSubmissionRepositoryIntegrationTest` (11),
  `ResourceSubmissionServiceIntegrationTest` (20),
  `ResourceSubmissionApiIntegrationTest` (17) — including the full role
  matrix and a genuine duplicate-pending conflict test.

**Frontend:**

- `lib/api/resource-submissions.ts`, `lib/query/use-resource-submissions.ts`,
  `lib/query/keys.ts`'s `resourceSubmissionKeys`.
- `components/contributions/submit-resource-form.tsx` —
  category-loading, client + server validation, accessible field-error
  mapping, "sent for review" success state (never a publish claim).
- `app/submit-resource/page.tsx` (protected).
- `ProtectedRoute` extended to redirect through a validated `returnTo`.

## Out of Scope

Correction reports — see Task 040.

## Acceptance Criteria

- [x] `POST` returns `201` with `status: PENDING_REVIEW`; every
      privileged field (status, owner, verification, active, location)
      is server-assigned only.
- [x] Invalid/inactive category return the established `404`/`400`
      error shapes.
- [x] Duplicate-pending submission returns `409`, verified by a real
      concurrent-request test.
- [x] Owner list/detail work; another account's item returns `404`.
- [x] Form shows category options, validates client-side, maps server
      field errors accessibly, and never claims immediate publication.
- [x] 48 new backend tests and the new frontend tests all pass.

## Evidence

Commits on branch `milestone/08b-submissions-corrections`; see
`docs/milestones/milestone-08b-submissions-corrections.md` for full
test results.
