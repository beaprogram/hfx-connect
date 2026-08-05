# Task 040: Correction Report API and Form

## Objective

Implement the full correction-report backend surface on top of Task
038's schema and entity, plus the frontend form and detail-page
integration that consume it.

## Context

The third task of Milestone 8B. Depends on Task 038's `CorrectionReport`
entity and migration being in place.

## Scope

**Backend:**

- `CorrectionReportRepository` — owner-scoped list queries (three sort
  orders, no association fetch needed since every display field lives
  on the report row itself) and an owner-scoped single-item lookup.
- `CorrectionReportValidation` — every proposed field optional; reuses
  `ResourceValidation`'s field-level helpers only when a proposed value
  is actually supplied.
- `CorrectionReportService` — `create` (active-resource check,
  resource-name/slug snapshot capture, race-safe duplicate-pending
  handling), `list`, `get`, `withdraw` (explicit `saveAndFlush`).
- `CorrectionReportController` — `POST
  /api/v1/resources/{resourceId}/correction-reports` (target in the
  path, not the body) plus `GET`/`GET {id}`/`POST {id}/withdraw` under
  `/api/v1/users/me/correction-reports`.
- `CorrectionReportNotFoundException`, `CorrectionReportConflictException`.
- Full test suite: `CorrectionReportRepositoryIntegrationTest` (9,
  including a real resource-deletion `SET NULL`-and-snapshot test),
  `CorrectionReportServiceIntegrationTest` (15),
  `CorrectionReportApiIntegrationTest` (13).

**Frontend:**

- `lib/api/correction-reports.ts`, `lib/query/use-correction-reports.ts`,
  `lib/query/keys.ts`'s `correctionReportKeys`.
- `components/contributions/correction-report-form.tsx` — issue-type
  selector, required explanation, optional "Suggested correction" field
  group (explanation-only reports fully valid).
- `app/resources/[slug]/report/page.tsx` (protected, server-resolves
  the resource by slug before rendering the form).
- A "Report incorrect information" link added to `resource-detail.tsx`.

## Out of Scope

Resource submissions — see Task 039.

## Acceptance Criteria

- [x] `POST` returns `201` with `status: PENDING_REVIEW`; the target
      resource's own fields are provably unchanged afterward (verified
      live).
- [x] Missing/inactive target resource both return the established
      `404` error shape.
- [x] Duplicate-pending report (same resource + issue type) returns
      `409`; a different issue type on the same resource is never
      blocked.
- [x] An explanation-only report (e.g. `RESOURCE_CLOSED`) succeeds with
      no proposed fields.
- [x] A report survives its target resource being deleted
      (`resourceId` becomes `null`, name/slug preserved via snapshot).
- [x] Owner list/detail work; another account's item returns `404`.
- [x] Form shows the target resource's name, never claims to modify it
      directly, and maps server field errors accessibly.
- [x] 37 new backend tests and the new frontend tests all pass.

## Evidence

Commits on branch `milestone/08b-submissions-corrections`; see
`docs/milestones/milestone-08b-submissions-corrections.md` for full
test results.
