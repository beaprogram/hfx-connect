# Milestone 8B: Resource Submissions and Correction Reports

## Objective

Give an authenticated account (any role — `USER`/`ORGANIZATION`/
`MODERATOR`/`ADMIN`) two community-contribution workflows: propose a
brand-new resource that isn't listed yet, and report an issue with an
existing active resource. Both create a private, owner-scoped
`PENDING_REVIEW` item — neither creates or modifies a public resource.
Users see only their own submissions and reports, with a clear status,
the details they originally submitted, and (optionally) the ability to
withdraw a still-pending item — while the backend remains the sole
authority for identity, ownership, status, resource visibility,
validation, pagination, and authorization.

## Product Value

The first two-sided contribution features this project has built:
before this milestone, only `ADMIN`/`MODERATOR` accounts could add or
change resource data at all (Milestone 3C's `POST /resources`, kept
role-restricted). This closes the loop the MVP scope always intended —
letting the community that uses the directory also help grow and
correct it — without granting any new write access to the public
dataset itself; every contribution is reviewed later (Milestone 9),
never applied automatically.

## Technical Scope

**Backend** — two new packages:

- `com.hfxconnect.resourcesubmission` — `ResourceSubmission`(+
  repository/service/controller/DTOs), `SubmissionStatus`,
  `ResourceSubmissionValidation` (reusing `ResourceValidation`'s
  field-level helpers).
- `com.hfxconnect.correctionreport` — `CorrectionReport`(+ repository/
  service/controller/DTOs), `CorrectionReportStatus`, `IssueType`,
  `CorrectionReportValidation`.
- `V9__create_resource_submissions_and_correction_reports.sql` — both
  tables, `UUID` primary keys, `RESTRICT`/`SET NULL` deletion policies,
  status/issue-type `CHECK` constraints, partial-unique duplicate-
  pending indexes.
- `InvalidContributionStatusException` (`com.hfxconnect.common.error`)
  — shared withdrawal-guard exception.
- `ResourceValidation`, `CategoryNotFoundException`,
  `InactiveCategoryException` widened (`public`/`public static`) for
  cross-package reuse, matching the precedent
  `ResourceNotFoundException.byId` already set in Milestone 8A.

**Frontend:**

- `lib/api/resource-submissions.ts`, `lib/api/correction-reports.ts`,
  `lib/query/use-resource-submissions.ts`,
  `lib/query/use-correction-reports.ts`,
  `lib/query/keys.ts`'s `resourceSubmissionKeys`/`correctionReportKeys`.
- `components/contributions/` — `submit-resource-form.tsx`,
  `correction-report-form.tsx`, `resource-submissions-section.tsx`,
  `correction-reports-section.tsx`, `resource-submission-detail.tsx`,
  `correction-report-detail.tsx`, `contribution-status-badge.tsx`.
- Routes: `/submit-resource`, `/resources/[slug]/report`,
  `/dashboard/submissions/[id]`, `/dashboard/correction-reports/[id]`.
- `ProtectedRoute` now redirects with a validated `returnTo`
  (`buildLoginHref`, Milestone 8A's return-path security), so a
  signed-out visit to any protected route returns there after login.
- `AuthProvider`'s private-cache clearing extended to
  `resource-submissions`/`correction-reports` key prefixes alongside
  `saved-resources`.
- `dashboard-content.tsx` — two new sections; `resource-detail.tsx` —
  a "Report incorrect information" link.

## Out of Scope

Moderation queue, approval/rejection endpoints, reviewer notes,
moderator assignment, automatic resource creation from an approved
submission, automatic correction application, admin contribution
dashboard, organization ownership, email notifications, attachments/
images, draft submissions, resource editing by the submitter, rate
limiting, spam detection, CI/CD, deployment, Milestone 9.

## Domain Separation

Two focused entities/tables, not one generic contribution table — see
[ADR-015](../decisions/ADR-015-community-contribution-workflows-design.md)
for the full rationale.

- **Resource submission**: proposes a new resource that doesn't exist
  publicly; contains a complete proposed record; begins
  `PENDING_REVIEW`; never creates a `CommunityResource` row; never
  appears in public search/list/map/nearby results.
- **Correction report**: refers to an existing active resource;
  identifies an issue plus an optional proposed correction; begins
  `PENDING_REVIEW`; never modifies the target resource; never appears
  in public results.

## Contribution Status Model

`PENDING_REVIEW` / `APPROVED` / `REJECTED` / `WITHDRAWN`, identical
shape for both domains. Milestone 8B only ever writes
`PENDING_REVIEW` (on creation) and `WITHDRAWN` (via the owner's own
withdrawal, implemented for both domains). `APPROVED`/`REJECTED` exist
in the schema and API contract for Milestone 9 compatibility, but no
endpoint in this milestone can assign them — there is no
status-changing request field anywhere, and no moderator endpoint
exists at all.

## Ownership and Authorization Model

Every endpoint derives the owner exclusively from
`@AuthenticationPrincipal CurrentUserPrincipal` — no endpoint accepts a
`userId` from a path, query, or body parameter. Any active
authenticated role may submit and report (no role restriction beyond
"authenticated"). A request for another account's item — list, detail,
or withdraw — returns the identical `404` a genuinely nonexistent id
would, never revealing that the id belongs to someone else.

## Resource Submission Contract

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/users/me/resource-submissions` | Create a `PENDING_REVIEW` submission. |
| `GET` | `/api/v1/users/me/resource-submissions` | Paginated, owner-scoped list. `sort=submittedAt\|updatedAt\|status`. |
| `GET` | `/api/v1/users/me/resource-submissions/{submissionId}` | One owned submission. |
| `POST` | `/api/v1/users/me/resource-submissions/{submissionId}/withdraw` | Withdraws a still-`PENDING_REVIEW` submission. |

## Correction Report Contract

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/resources/{resourceId}/correction-reports` | Create a `PENDING_REVIEW` report against an active resource. |
| `GET` | `/api/v1/users/me/correction-reports` | Paginated, owner-scoped list. `sort=submittedAt\|updatedAt\|status`. |
| `GET` | `/api/v1/users/me/correction-reports/{reportId}` | One owned report. |
| `POST` | `/api/v1/users/me/correction-reports/{reportId}/withdraw` | Withdraws a still-`PENDING_REVIEW` report. |

Full request/response shapes: `docs/api/README.md`.

## Duplicate-Pending Policy

A partial unique database index (`WHERE status = 'PENDING_REVIEW'`)
backs each domain's guard, not an application-level check alone:

- Submissions: at most one pending submission per
  `(user, category, normalized name)` at a time.
- Correction reports: at most one pending report per
  `(user, resource, issue type)` at a time.

Neither index blocks a later resubmission/re-report of the same shape
once the earlier one is withdrawn/approved/rejected, and neither blocks
reporting a genuinely different issue on the same resource. A
concurrent double-submit race resolves to `409 Conflict` (caught from
the constraint violation), not a duplicate row or an unhandled `500`.

## Resource Visibility Behavior

- Submissions: the referenced category must exist and be active —
  identical error behavior to real resource creation
  (`CATEGORY_NOT_FOUND` / `INACTIVE_CATEGORY`).
- Correction reports: the target resource must currently be active — a
  missing or inactive resource returns the identical `404` (
  `RESOURCE_NOT_FOUND`), the same "missing and inactive look alike"
  posture used throughout this API.
- Removal (withdrawal) works regardless of the referenced category's or
  resource's current state.
- A correction report survives its target resource being deleted
  (`resource_id` set `NULL`, name/slug preserved via a snapshot
  captured at creation) — see ADR-015.

## Query and Pagination Strategy

Owner-scoped list queries default to `submittedAt` descending, capped
at 100 per page (matching every other paginated list in this API); an
out-of-range page/size or unrecognized `sort` returns `400`, never a
silent clamp.

## Frontend Submission Flow

`/submit-resource` (protected) loads active categories via the
existing public `getCategories` API, validates client-side for
immediate feedback, and maps server field errors accessibly
(`aria-invalid`/`aria-describedby`, matching `register-form.tsx`'s
established pattern). Success shows "Your submission has been sent for
review" — never a publish claim — with a link to the new item's
dashboard detail page. No privileged field (status, verification,
active, location) is ever rendered.

## Frontend Correction Flow

A "Report incorrect information" link on every active resource's
detail page opens `/resources/[slug]/report` (protected — a signed-out
visitor is redirected through `/login` with a validated `returnTo` back
to the exact report page). The form shows the target resource's name,
an issue-type selector, a required explanation, and an optional
"Suggested correction" field group — an explanation-only report (e.g.
`RESOURCE_CLOSED`) is fully valid. Success never claims the resource
was modified.

## Dashboard Integration

Two new sections, `My Resource Submissions` and `My Correction
Reports`, following `SavedResourcesSection`'s established loading/
empty/error+retry/pagination structure exactly. Each card shows a
readable status (never colour alone), the submitted date, and a link
to the item's own detail page, where a `PENDING_REVIEW` item can be
withdrawn. No fabricated review-time estimates, counts, or other
invented features.

## Authentication and Cache Isolation

`AuthProvider`'s private-data cache clearing (Milestone 8A) now covers
three query-key prefixes — `saved-resources`, `resource-submissions`,
`correction-reports` — cleared unconditionally on logout and on a
detected account switch. Every query key is rooted in the
authenticated user's `id`, never the access token.

## Return-to Login Security

`ProtectedRoute` (Milestone 5C) now builds its redirect through
`buildLoginHref(pathname)` (Milestone 8A's `isSafeReturnPath`), so a
signed-out visit to `/submit-resource` or `/resources/[slug]/report`
returns there after login — reusing the exact same open-redirect-safe
validation the "Sign in to save" flow already established, not a new
implementation.

## Tests Executed

`./mvnw clean verify` (backend), `npm test -- --ci` / `npm run lint` /
`npm run typecheck` / `npm run build` (frontend).

## Authoritative Backend Test Results

**639/639 tests pass** (554 inherited unchanged + 85 new: 48
`resourcesubmission` — 11 repository, 20 service, 17 API; 37
`correctionreport` — 9 repository, 15 service, 13 API), 0 failures, 0
errors, 0 skipped, per the Maven Surefire summary. Two pre-existing
tests (`ResourceApiIntegrationTest.listReturnsAPageContainingACreatedResource`,
`CategoryApiIntegrationTest`'s inactive-category-filter test) were
found to be order-/pollution-dependent on the shared, never-rolled-back
API-integration-test database once this milestone's own tests started
creating resources/categories in it — both fixed to scope themselves by
a unique marker instead of an unscoped global assumption (see "Known
Limitations").

## Authoritative Frontend Test Results

**406/406 tests pass** (327 inherited unchanged + 79 new) across 54
suites, 0 failures, per `npm test -- --ci`.

## Build, CI, and Quality Results

`npm run lint` / `npm run typecheck` / `npm run build` (production,
clean `.next` rebuild) — all clean. No new dependencies (`npm audit`
unchanged from Milestone 8A).

## Manual Verification

Performed against the real running backend/frontend/database, using a
genuine headless Chromium session (Playwright) plus direct API calls
for the full role matrix — **41/41 scripted checks passed**, including:

- Signed-out `/submit-resource` and `/resources/[slug]/report` both
  redirect to `/login` with a validated `returnTo`; logging in returns
  to the original page.
- `USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` can all submit a resource
  proposal; every submission starts `PENDING_REVIEW` and never appears
  in public resource search.
- Genuine two-user isolation: another account's submission/report
  detail returns `404`; dashboard lists never show another account's
  items.
- Duplicate-pending submission and duplicate-pending report both
  correctly return `409`.
- Invalid category (`404`) and inactive category (`400`) both handled
  safely; inactive and missing target resources both return `404` for
  correction reports.
- An explanation-only `RESOURCE_CLOSED` report succeeds with no
  proposed fields.
- A correction report submitted live through the browser is confirmed,
  via a direct API read immediately afterward, to have left the target
  resource's own fields completely unchanged.
- Dashboard sections render correctly in both populated and empty
  states; account-switch clears the previous account's cached
  submissions.
- Browser storage inspected directly — no access token, no
  `PENDING_REVIEW` or other private contribution payload present.
- Saved resources (Milestone 8A) and the interactive map (Milestone 7B)
  continue to work unaffected; `/login`/`/register`/`/dashboard`
  authentication regressions reconfirmed; responsive layout checked at
  375px and 1440px.

## OpenAPI Verification

`GET /v3/api-docs`, parsed directly, confirms all seven new operations
(`POST`/`GET`/`GET {id}`/`POST {id}/withdraw` × 2 domains, plus the
resource-nested report-creation route) are documented with correct
paths, methods, security requirements, and response schemas.

## Security Review

- Every endpoint requires a valid Bearer token; identity always comes
  from `CurrentUserPrincipal`, never a client-supplied id (`git grep
  userId` across both new packages confirms every call site is
  `principal.userId()`).
- `git grep` across the new frontend files
  (`localStorage`/`sessionStorage`/`console.log`/
  `dangerouslySetInnerHTML`) returns zero matches.
- No moderator/approve/reject endpoint exists anywhere in either new
  controller — confirmed by direct inspection of every `@PostMapping`/
  `@GetMapping` in both files.
- `returnTo` reuses Milestone 8A's already-tested `isSafeReturnPath`/
  `buildLoginHref` — no new redirect-validation logic was written, so
  no new attack surface was introduced there.
- A genuine concurrent-request race on both duplicate-pending
  constraints resolves to `409`, never a `500` or a duplicate row
  (verified by direct database-level constraint tests, not just
  application-level reasoning).

## Privacy Review

Resource submissions and correction reports are private, account-linked
data:

- Never included in any public/anonymous API response.
- Never written to `localStorage`/`sessionStorage` — confirmed by
  direct browser-storage inspection during manual verification.
- Fully isolated per account at the database level (owner-id-scoped
  queries only) and at the frontend cache level (query keys rooted in
  `userId`, cleared on logout/account switch).
- No analytics or tracking integration reads or reports contribution
  activity.

## Performance Review

- Both list endpoints paginate server-side (default 20, capped at 100)
  — no unbounded "fetch everything" behavior anywhere.
- Detail and list queries are simple owner-scoped lookups on indexed
  columns (`(owner_id, submitted_at DESC)` on both tables); no N+1
  pattern was introduced (submissions eagerly `JOIN FETCH` their
  category; reports need no association fetch at all, since every
  display field already lives on the report row itself, including the
  target-resource-name snapshot).
- No production-scale load test was run — the manual-verification
  dataset was small by design, stated honestly rather than
  extrapolated.

## Acceptance Criteria

**Database**

- [x] `V9` creates both tables; earlier migrations unchanged.
- [x] Owner foreign keys enforced (`RESTRICT`); category/resource
      relationships enforced (`RESTRICT`/`SET NULL`).
- [x] Status and issue-type values database-`CHECK`-constrained.
- [x] Duplicate-pending policy enforced via partial unique indexes.
- [x] Hibernate validates cleanly against the real schema.

**Ownership and security**

- [x] Every endpoint requires authentication; owner always from the
      authenticated principal; no client-supplied user id anywhere.
- [x] Every role may contribute; another account's item is
      indistinguishable from a nonexistent one (`404`).
- [x] No public-resource publication or modification occurs anywhere.
- [x] Private caches clear on logout/account switch; no private data in
      browser storage.

**Resource submissions / Correction reports**

- [x] Valid creation returns `201`, defaults to `PENDING_REVIEW`.
- [x] Category/resource visibility rules enforced with the established
      error shapes.
- [x] Duplicate-pending policy verified at both the constraint and API
      layers.
- [x] Owner list/detail work; pagination and allowlisted sorting work.
- [x] Public resource results remain completely unaffected.

**Frontend**

- [x] Both forms work, including an explanation-only correction report.
- [x] Signed-out return-to login is safe and tested.
- [x] Server validation surfaces accessibly; double-submit prevented.
- [x] Dashboard sections cover loading/empty/error/pagination/detail
      navigation with a readable, non-colour-only status.
- [x] Account-switch isolation and logout cache-clearing verified.

**Testing** — 639/639 backend, 406/406 frontend, all quality gates
clean (see above).

**Manual verification** — 41/41 scripted checks against the real
running stack, including genuine two-user isolation (see above).

**Documentation** — this document, ADR-015, tasks 038-041, and every
file listed under "Documentation Updated" below.

## Known Limitations (as of Milestone 8B)

- No moderator queue, approval/rejection API, reviewer notes, or
  assignment — entirely Milestone 9.
- No publishing from an approved submission and no automatic resource
  update from an approved correction — both Milestone 9.
- No email or status-change notifications.
- No attachments, images, or bulk submissions.
- No draft saving — a submission/report is created complete or not at
  all.
- No editing after submission (beyond withdrawal).
- No public contribution history or feed.
- No rate limiting or spam detection on either endpoint (consistent
  with the rest of this API today — see ADR-008's own honest
  limitations section).
- A real, pre-existing test-isolation fragility was found and fixed
  during this milestone's own test run, not introduced by it: two
  pre-existing API-integration tests
  (`ResourceApiIntegrationTest.listReturnsAPageContainingACreatedResource`,
  a `CategoryApiIntegrationTest` inactive-category-filter test)
  implicitly assumed either "fewer than N resources exist across the
  whole shared, never-rolled-back test database" or "no inactive
  category has ever been created" — both assumptions had already been
  false in principle since earlier milestones (a pre-existing
  `ResourceApiIntegrationTest.inactiveCategoryIsRejected` test already
  created a permanently-inactive category), but only started actually
  failing once this milestone's own tests added enough additional rows.
  Both were fixed to scope themselves with a unique marker instead of
  an implicit global assumption, matching every other test in those
  files.
- A second real, pre-existing bug (unrelated to this milestone's own
  code but found while validating the fix above) was also present:
  five `openNow`-filter test fixtures across
  `ResourceServiceIntegrationTest`/`ResourceApiIntegrationTest` built an
  operating-hours window using "today's real day of week" even when the
  window's own start time rolled back into the previous calendar day —
  breaking deterministically only within the first hour after midnight
  America/Halifax. The production `OpenNowCalculator` was never
  affected (it already derives each entry's day independently); only
  the test fixtures were wrong. Fixed by deriving the entry's
  day-of-week from the window's own start instant instead of "today,"
  correct in both directions.

## Risks

| Risk | Mitigation |
|---|---|
| A concurrent double-submit (double-click, two tabs) could create two pending items or a `500` | The database's own partial unique index is the authoritative guard; the service catches the losing insert's constraint violation and returns `409`, verified by direct constraint tests |
| A correction report could accidentally imply the resource was actually corrected | Every success message on both the correction form and its detail page explicitly states the resource has not been changed and is pending review; verified in both automated tests and live manual verification |
| Withdrawing an item and immediately resubmitting the identical shape could race against Hibernate's own insert-before-update flush ordering | `withdraw()` calls `saveAndFlush` explicitly rather than relying on implicit auto-flush timing — found and fixed during this milestone's own service-layer testing, documented in ADR-015 |
| A shared, never-rolled-back API-integration-test database could let one milestone's new tests silently break another's pre-existing assertions | Found exactly this happening to two pre-existing tests during this milestone's own verification pass (not by any automated regression alone — a full-suite run surfaced it); fixed by scoping both to a unique marker, the same convention every other test in those files already follows |

## Completion Summary

All planned Milestone 8B deliverables were completed and verified three
ways: 639 automated backend tests (554 inherited unchanged, 85 new)
plus 406 automated frontend tests (327 inherited unchanged, 79 new), a
full manual pass against the real running stack using a genuine
headless-Chromium session with direct role-matrix API verification and
genuine two-user isolation checks, and a full regression pass
confirming Milestones 5-8A remain unaffected. Two real, pre-existing
test-isolation bugs (unrelated to this milestone's own feature code,
but only surfaced once this milestone's tests added enough additional
rows to the shared test database) were found during this milestone's
own verification, fixed, and left with more robust, order-independent
test scoping before this branch was pushed.
