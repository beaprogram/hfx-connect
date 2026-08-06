# Milestone 9A: Moderation Queue, Review Decisions, Publication, and Audit History

## Objective

Give a current `MODERATOR`/`ADMIN` account the authority to review the
`PENDING_REVIEW` resource submissions and correction reports Milestone
8B introduced: view a queue, inspect full detail, approve or reject
with a required reason, publish an approved submission as a real
public resource, apply an approved correction's supported changes (or
deactivate the target resource for an approved `RESOURCE_CLOSED`
report), and leave an immutable audit trail — all while a moderator can
never review their own contribution, two moderators can never both
"win" a race on the same item, and the contribution's owner sees a
safe outcome without ever learning who reviewed it.

## Product Value

Closes the loop Milestone 8B deliberately left open: contributions
existed, but nothing could ever act on them. This is the first
milestone where community-submitted content can become part of the
public dataset — through a real moderator decision, never
automatically, and always leaving a durable record of who decided what
and why.

## Technical Scope

**Backend** — one new package, plus deliberate extensions to two
existing ones:

- `com.hfxconnect.moderation` — `ModerationAuditEvent` (+ repository),
  `ContributionType`/`ModerationAction`/`ModerationDecision` enums,
  `ModerationSnapshots`, `ModerationAuditRecorder`,
  `ModerationAuditQueryService`, `ModerationValidation`,
  `ResourceSubmissionReviewService`, `CorrectionReportReviewService`,
  `ModerationResourceSubmissionController`,
  `ModerationCorrectionReportController`, `ModerationAuditController`,
  request/response DTOs, and six new exception types
  (`SelfReviewNotAllowedException`, `ContributionAlreadyReviewedException`,
  `ResourcePublicationConflictException`,
  `CorrectionApplicationConflictException`,
  `UnsupportedCorrectionApplicationException`,
  `InvalidDeactivationRequestException`).
- `V10__add_moderation_workflow_and_audit.sql` — review metadata on
  `resource_submissions`/`correction_reports`, `resources.last_verified_at`,
  and the new `moderation_audit_events` table.
- `com.hfxconnect.resource` — `CommunityResource.markVerified`,
  `ResourceService.createVerified`,
  `ResourceRepository.findByIdForUpdate` (row-locking), plus
  `lastVerifiedAt` threaded through `ResourceDetails`/`ResourceResponse`.
- `com.hfxconnect.resourcesubmission`/`correctionreport` — `approve`/
  `reject` entity mutators, `findByIdForReview`/`findForModerationQueue`
  repository methods, owner-facing DTO additions (`reviewedAt`,
  `reviewReason`, `resultingResource`/`changesApplied`).
- `SecurityConfig` — `/api/v1/moderation/**` restricted to
  `ADMIN`/`MODERATOR`.

**Frontend:**

- `lib/api/moderation.ts`, `lib/query/use-moderation.ts`,
  `lib/query/keys.ts`'s `moderationKeys`.
- `components/moderation/` — `moderation-route.tsx` (role guard),
  `resource-submission-queue.tsx`, `correction-report-queue.tsx`,
  `moderation-tabs.tsx`, `resource-submission-review-detail.tsx`,
  `correction-report-review-detail.tsx`, `moderation-decision-form.tsx`,
  `correction-approval-form.tsx`, `moderation-audit-history.tsx`,
  `current-resource-state-response` display helper.
- Routes: `/moderation`, `/moderation/resource-submissions/[id]`,
  `/moderation/correction-reports/[id]`.
- `AuthNav`/`MobileNav` — a "Moderation" link shown only for
  `MODERATOR`/`ADMIN` accounts.
- `AuthProvider`'s private-cache clearing extended to a `"moderation"`
  key prefix.
- Owner-facing dashboard detail pages
  (`resource-submission-detail.tsx`/`correction-report-detail.tsx`) —
  precise outcome copy for `APPROVED`/`REJECTED`/`WITHDRAWN`.

## Out of Scope

Organization ownership, reviewer assignment, email notifications,
automated moderation/spam scoring, bulk moderation, role-management UI,
decision reversal, audit export, private moderator notes, appeal
workflow, automated duplicate-listing merge, automated operating-hours
correction (no structured proposed-hours data exists to apply — see
ADR-016), Milestone 9B, Milestone 10.

## Moderation Principles

- A contribution begins `PENDING_REVIEW`; `WITHDRAWN`/`APPROVED`/
  `REJECTED` items cannot be reviewed again.
- `MODERATOR` and `ADMIN` may review; `USER`/`ORGANIZATION` may not —
  enforced by `SecurityConfig`, backed by the current database role
  (`JwtAuthenticationFilter` already re-loads the account every
  request — see ADR-009), never a JWT claim alone.
- A moderator/admin can never review their own contribution — no
  exception for `ADMIN`.
- One review decision is atomic: a resource submission never creates
  more than one public resource; a correction report never applies its
  changes more than once; a failed publication/application leaves the
  contribution `PENDING_REVIEW`, never partially applied.
- Every final decision creates at least one audit record; a rejection
  never changes the public dataset.

Full concurrency/audit/verification/correction-application design:
[ADR-016](../decisions/ADR-016-moderation-workflow-design.md).

## Database Migration

`V10__add_moderation_workflow_and_audit.sql` — earlier migrations
unchanged:

- `resources.last_verified_at` (nullable `TIMESTAMPTZ`).
- `resource_submissions`: `reviewed_by_user_id` (`RESTRICT`),
  `reviewed_at`, `review_reason`, `resulting_resource_id` (`SET NULL`,
  partial-unique — at most one resulting resource per submission),
  `resulting_resource_name`/`resulting_resource_slug` (snapshot).
  `CHECK` constraints enforce review metadata present together on
  `APPROVED`/`REJECTED` and absent on `PENDING_REVIEW`/`WITHDRAWN`; a
  resulting resource can only exist on an `APPROVED` row.
- `correction_reports`: the same four review-metadata columns, plus
  `applied_to_resource_at` (distinct from `reviewed_at` — set only when
  a scalar field was actually written to the target resource).
- `moderation_audit_events`: append-only, `contribution_type`/
  `action`/`decision` `CHECK`-constrained, `actor_user_id`
  (`RESTRICT`) + a snapshot `actor_email`, nullable JSONB
  `before_snapshot`/`after_snapshot`, indexed on
  `(contribution_type, contribution_id, created_at)`.

## Concurrency Control

Row-level `PESSIMISTIC_WRITE` locking (`SELECT ... FOR UPDATE`), not
`@Version` — see ADR-016 for the full rationale. A second moderator's
concurrent review call blocks until the first transaction ends, then
observes the now-final status and fails with `409
CONTRIBUTION_ALREADY_REVIEWED`. Correction application additionally
locks the *target resource* row (`ResourceRepository.findByIdForUpdate`),
since two different pending reports can target the same resource.

## Moderation Queue and Detail API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/moderation/resource-submissions` | Queue. `status` (default `PENDING_REVIEW`), `categoryId`, `sort=submittedAt\|updatedAt`. |
| `GET` | `/api/v1/moderation/resource-submissions/{id}` | Full detail, not owner-scoped. |
| `POST` | `/api/v1/moderation/resource-submissions/{id}/approve` | `{reason}` — publishes a `VERIFIED` public resource. |
| `POST` | `/api/v1/moderation/resource-submissions/{id}/reject` | `{reason}` — never creates a resource. |
| `GET` | `/api/v1/moderation/resource-submissions/{id}/audit-events` | This submission's audit history. |
| `GET` | `/api/v1/moderation/correction-reports` | Queue. `status`, `issueType`, `sort=submittedAt\|updatedAt`. |
| `GET` | `/api/v1/moderation/correction-reports/{id}` | Full detail, including the target resource's current live state. |
| `POST` | `/api/v1/moderation/correction-reports/{id}/approve` | `{reason, applyProposedChanges, deactivateResource}`. |
| `POST` | `/api/v1/moderation/correction-reports/{id}/reject` | `{reason}` — never modifies the target resource. |
| `GET` | `/api/v1/moderation/correction-reports/{id}/audit-events` | This report's audit history. |
| `GET` | `/api/v1/moderation/audit-events` | Global audit list. `contributionType`, `decision` filters. |

All eleven routes: `ADMIN`/`MODERATOR` only. Submitter/reporter identity
is never exposed on any queue or detail response.

## Publication Contract (Resource Submission Approval)

Locks the submission row → self-review check → confirms
`PENDING_REVIEW` → maps proposed fields to `CreateResourceCommand`
(`fullDescription` preferred over `shortDescription` when present — see
ADR-016) → `ResourceService.createVerified` (reuses the exact same
category/field/slug-uniqueness validation real resource creation uses)
→ marks the submission `APPROVED` with the resulting resource's id/
name/slug → records a `RESOURCE_CREATED` audit event → commits
atomically. A slug/name collision returns `409
RESOURCE_PUBLICATION_CONFLICT`; the submission stays `PENDING_REVIEW`.

## Correction Application Contract

Locks the report row → self-review check → confirms `PENDING_REVIEW` →
validates `deactivateResource` (`RESOURCE_CLOSED` only) and
`applyProposedChanges` (rejected for `OPERATING_HOURS`/
`DUPLICATE_RESOURCE` — no automated field mapping exists for either)
→ locks the target resource row → merges present `proposed*` fields
onto the resource's current values, revalidates, applies via
`updateDetails`, marks `VERIFIED` → optionally deactivates → marks the
report `APPROVED` (`applied_to_resource_at` set only if a field
changed) → records `RESOURCE_UPDATED`/`RESOURCE_DEACTIVATED`/bare
`REVIEW_DECISION` audit events as applicable → commits atomically. Only
the 13 scalar fields the report's actual V9 schema supports are ever
applied; category is never touched (no `proposedCategory` field
exists).

## Audit Schema

`moderation_audit_events` — one row per concrete effect (see ADR-016's
"Audit Model"): `REVIEW_DECISION` when a decision has no direct
resource-table effect (every rejection, every no-op approval);
`RESOURCE_CREATED`/`RESOURCE_UPDATED`/`RESOURCE_DEACTIVATED` when it
does, each row carrying its own decision/reason/actor. Snapshots are
small, explicitly-built field maps — never a serialized entity (see
ADR-016's "Snapshot Policy"). Append-only; no API exists to edit or
delete a row.

## Owner-Visible Outcome

Owner-facing `ResourceSubmissionResponse`/`CorrectionReportResponse`
gain `reviewedAt`, `reviewReason`, and (submissions) `resultingResource`
/ (corrections) `changesApplied` — never the reviewer's identity.
Dashboard detail pages show the exact outcome copy: "Approved and
published" (with a link), "Approved. The reported information was
reviewed[ and applied]." , "Rejected", "Withdrawn".

## Cache Invalidation

Approval invalidates: `["moderation", "resource-submissions" |
"correction-reports"]` (queue/detail/audit for that domain),
`["resources"]` (public list/search/nearby/map/detail), the owner-facing
`["resource-submissions"|"correction-reports"]` prefix, and
(corrections) `["saved-resources"]`. Rejection invalidates only the
moderation-side and owner-facing prefixes — no public data changed.
Access tokens never appear in any query key.

## Accessibility and Responsive Behavior

ARIA `tablist`/`tab`/`tabpanel` queue switcher; labelled status/
issue-type/sort filters; keyboard-operable pagination; decision forms
with labelled fields, an error summary, and double-submit prevention
(`disabled` while `isPending`); status always paired with text, never
colour alone; current-vs-proposed comparison rendered as a semantic
`<table>` with a horizontally-scrollable wrapper. Verified at 375px,
768px, 1024px, and 1440px during manual verification.

## Tests Executed

`./mvnw clean verify` (backend), `npm test` / `npm run lint` /
`npx tsc --noEmit` / `npm run build` (frontend).

## Authoritative Backend Test Results

**684/684 tests pass** (639 inherited unchanged + 45 new: 12
`ResourceSubmissionReviewServiceIntegrationTest`, 12
`CorrectionReportReviewServiceIntegrationTest`, 2
`ModerationConcurrencyIntegrationTest` (real two-thread races), 3
`ModerationAuditEventRepositoryIntegrationTest`, 8
`ModerationResourceSubmissionApiIntegrationTest` (including a
regression test for a bug this milestone's own manual verification
caught — see "Known Limitations"), 6
`ModerationCorrectionReportApiIntegrationTest`, plus the `resource`
package's column-list test updated for `last_verified_at` and the
Flyway version-count test updated for `V10`), 0 failures, 0 errors, 0
skipped.

## Authoritative Frontend Test Results

**478/478 tests pass** (406 inherited unchanged + 72 new) across 62
suites, 0 failures, per `npm test`.

## Build, CI, and Quality Results

`npm run lint`, `npx tsc --noEmit`, and `npm run build` (production) —
all clean. All eleven `/api/v1/moderation/**` routes and their expected
`200/400/401/403/404/409` response codes confirmed present in
`GET /v3/api-docs` against the live running application.

## Manual Verification

A 43-check script run against the real backend (real PostgreSQL, real
JWT issuance, `docker exec psql` used only to promote three test
accounts to `MODERATOR`/`ADMIN`/`ORGANIZATION`, since no
role-assignment endpoint exists) and a real headless-Chromium session
(Playwright) against the real frontend dev server — **43/43 passed**,
including:

- Full authorization matrix: unauthenticated → `401`; `USER`/
  `ORGANIZATION` → `403`; `MODERATOR`/`ADMIN` → `200`, on the live
  moderation queue endpoint.
- A real submission approved through the API is immediately visible at
  its public slug, `VERIFIED` with a real `lastVerifiedAt`, and
  appears in public keyword search.
- The owner's own view of the approved submission shows the resulting
  resource link and never contains the reviewing moderator's account id
  anywhere in the response body.
- A repeated approval attempt on an already-decided submission returns
  `409 CONTRIBUTION_ALREADY_REVIEWED`.
- `MODERATOR` and, separately, `ADMIN` both blocked with `403
  SELF_REVIEW_NOT_ALLOWED` attempting to review their own submission —
  confirming `ADMIN` has no bypass.
- A rejected submission never creates a resource, confirmed by a direct
  public-search query immediately after.
- A publication conflict (two submissions producing the same slug)
  returns `409 RESOURCE_PUBLICATION_CONFLICT` and leaves the second
  submission `PENDING_REVIEW`.
- A correction report's approved address change is reflected on the
  live public resource immediately.
- `OPERATING_HOURS`/`DUPLICATE_RESOURCE` correction approval with
  `applyProposedChanges=true` returns `400
  UNSUPPORTED_CORRECTION_APPLICATION`.
- A `RESOURCE_CLOSED` approval with `deactivateResource=true`
  immediately removes the resource from both its public detail route
  (`404`) and public search.
- `deactivateResource=true` for a non-`RESOURCE_CLOSED` report returns
  `400 INVALID_DEACTIVATION_REQUEST`.
- A suspended moderator's still-technically-valid JWT loses access
  immediately (`401`) on the very next request — the current database
  account status is authoritative, not the token's claims at issuance.
- Two genuinely concurrent HTTP requests (one approve, one reject) on
  the same submission: exactly one succeeds (`200`), the other receives
  `409`, and at most one public resource is ever created.
- In the browser: a `USER` visiting `/moderation` sees an "Access
  denied" message (not a redirect) and no "Moderation" nav link; a
  `MODERATOR` sees the nav link, the queue tabs, and can approve a
  submission end-to-end through the actual UI, seeing the resulting
  resource link rendered.
- No access-token or refresh-token key present in `localStorage`/
  `sessionStorage` after a full moderation session.
- No unexpected browser console errors during the entire flow (the
  ordinary anonymous-session-check `401` on initial page load is
  expected per ADR-009 and excluded from this check).

A real defect was found and fixed during this pass: the public resource
detail API never exposed the new `lastVerifiedAt` field (see "Known
Limitations" and ADR-016) — caught only by reading the live response
body, not by any of the 684 automated tests written before the fix. A
permanent regression test was added afterward.

## OpenAPI Verification

`GET /v3/api-docs` against the live running application confirms all
eleven `/api/v1/moderation/**` operations, each with a `bearerAuth`
security requirement and its full documented response-code set.

## Security Review

- `/api/v1/moderation/**` is the single `SecurityConfig` matcher
  covering all eleven routes — `hasAnyRole("ADMIN", "MODERATOR")`.
- Current database role/status is authoritative for every request
  (`JwtAuthenticationFilter` re-loads the account every time — ADR-009);
  a suspended moderator loses access on the very next request,
  confirmed live.
- Self-review is enforced against the current database owner column,
  never a client-supplied value; confirmed for both `MODERATOR` and
  `ADMIN`.
- No endpoint accepts a client-supplied reviewer identity, `reviewedAt`,
  `resultingResourceId`, or final status — every one of those is
  server-assigned inside the review transaction.
- Concurrency is enforced by a real database row lock, not an
  application-level pre-check alone — proven by an actual two-thread
  concurrent-transaction test, not just reasoning about it.
- A failed publication or correction application never leaves a partial
  mutation — the whole review transaction rolls back on any exception.
- `git grep` across the new backend/frontend files for
  `localStorage`/`sessionStorage`/`console.log`/
  `dangerouslySetInnerHTML` returns zero matches.

## Privacy Review

- `moderation_audit_events` and every `/audit-events` route are
  moderator/admin-only — never reachable from a public or owner-facing
  route; confirmed live (`403` for the owner attempting the audit
  route).
- Owner-facing contribution responses never include the reviewing
  moderator's identity — confirmed by direct string search of the live
  response body, not just DTO field inspection.
- Audit snapshots contain only explicitly-listed reviewed-content
  fields — never password hashes, tokens, refresh-session data, or
  unrelated account fields (see ADR-016's "Snapshot Policy").
- The frontend's private-cache clearing (logout/account-switch) now
  also covers the `"moderation"` key prefix.
- No new analytics or tracking integration reads or reports moderation
  activity.

## Performance Review

- Queue rows are compact DTOs (no full submission/report payload
  returned per row); both queue queries are simple, filtered,
  paginated lookups with no N+1 pattern (submission queue eagerly
  `JOIN FETCH`es category; correction queue needs no association fetch
  at all).
- The audit-contribution index (`contribution_type, contribution_id,
  created_at`) supports both per-contribution history and the leading
  column of the global filtered list.
- JSONB snapshots are small, explicitly-bounded field maps, never a
  full entity serialization — bounded size by construction.
- No production-scale benchmark was run — stated honestly, matching
  Milestone 8B's own precedent.

## Acceptance Criteria

**Database**

- [x] `V10` adds review metadata + `moderation_audit_events`; `V1`-`V9`
      unchanged.
- [x] Review metadata `CHECK`-constrained present-together/absent-together.
- [x] `resulting_resource_id` unique per submission, `APPROVED`-only.
- [x] Reviewer/actor foreign keys `RESTRICT`; audit table append-only.

**Authorization**

- [x] `/api/v1/moderation/**` restricted to `ADMIN`/`MODERATOR`; full
      401/403/200 matrix confirmed live.
- [x] Self-review blocked for both `MODERATOR` and `ADMIN`.
- [x] Current database role/status authoritative — confirmed via live
      suspension test.

**Submission moderation**

- [x] Approval publishes exactly one `VERIFIED` public resource with a
      real `lastVerifiedAt`.
- [x] Rejection never creates a resource.
- [x] Publication conflict returns `409`, leaves submission pending.
- [x] Concurrent decisions: exactly one wins.

**Correction moderation**

- [x] Only the report's actual supported proposed fields are ever
      applied; category never touched.
- [x] `RESOURCE_CLOSED` deactivation works and only for that issue type.
- [x] `OPERATING_HOURS`/`DUPLICATE_RESOURCE` auto-apply rejected
      honestly.
- [x] Rejection never modifies the target resource.

**Audit**

- [x] Every final decision produces at least one audit row; no edit/
      delete API exists.
- [x] Snapshots contain only explicitly-listed safe fields.
- [x] Audit history moderator/admin-only, confirmed live.

**Frontend**

- [x] Role guard: loading/access-denied/redirect states all correct,
      no content flash.
- [x] Queue/detail/decision-form UI complete for both domains,
      accessible, responsive.
- [x] Dashboard owner-outcome integration with the exact specified copy.
- [x] Cache invalidation confirmed to cover public + moderation +
      owner-facing + saved-resource queries as applicable.

**Testing** — 684/684 backend, 478/478 frontend, all quality gates
clean (see above).

**Manual verification** — 43/43 scripted checks against the real
running stack, including a real two-thread concurrency race and a
real defect found and fixed (see above).

**Documentation** — this document, ADR-016, tasks 042-045, and every
file listed under "Documentation Updated" in the completion report.

## Known Limitations (as of Milestone 9A)

- No reviewer assignment, private moderator notes, bulk review, appeal
  workflow, email notifications, automated spam scoring, or automated
  duplicate-listing merge.
- No automated operating-hours correction — V9's correction-report
  schema never captured structured proposed hours; a moderator must
  reject an `OPERATING_HOURS` report or update hours manually through
  the existing endpoint.
- No attachment/image review, organization ownership, role-management
  UI, decision reversal, or audit export.
- No production-scale concurrency/load benchmark — the concurrency
  guarantee is proven correct by a real two-thread test, not load-tested
  at scale.
- **A real defect found by this milestone's own manual verification,
  not by any automated test**: `CommunityResource.lastVerifiedAt` was
  added and correctly threaded through the moderation-facing audit
  snapshots and entity layer, but the *public* `ResourceResponse` DTO
  (`GET /api/v1/resources/{id}` and `.../slug/{slug}`) was never
  updated to expose it — a change that compiled cleanly and passed
  every automated test written against the entity/service/moderation
  layers, because none of those tests asserted the public API's actual
  response shape end-to-end. Fixed, and a permanent regression test
  added (`ModerationResourceSubmissionApiIntegrationTest
  .publishedResourceExposesVerificationStatusAndLastVerifiedAtPublicly`)
  asserting the field is present in a live HTTP response body. See
  ADR-016's "Verification Policy" section for the full account.

## Risks

| Risk | Mitigation |
|---|---|
| Two moderators reviewing the same item concurrently could both succeed, double-publishing or double-applying | A real `PESSIMISTIC_WRITE` row lock serializes the entire review transaction, not just the final write; proven with an actual two-thread concurrent test, not reasoning alone |
| A moderator could review (and effectively approve) their own contribution | Self-review is checked against the current database owner column on every approve/reject call, for every role including `ADMIN`, with a dedicated test per role |
| A partially-applied correction could leave the public resource in an inconsistent state if application fails partway | The entire review transaction (lock, validate, apply, mark reviewed, record audit) is one atomic unit — any exception rolls back all of it |
| An audit snapshot could accidentally leak a password hash or token if built by reflecting over an entity | Every snapshot is built from an explicit, hand-written field allowlist (`ModerationSnapshots`, or inline field-by-field code) — never a serialized entity |
| A schema/entity change (like `lastVerifiedAt`) could compile and pass entity-layer tests while never reaching a separate response DTO that also claims to expose "full resource detail" | Found exactly this happening during this milestone's own manual verification (see Known Limitations); fixed, and a permanent live-response regression test added so it cannot silently regress again |

## Completion Summary

All planned Milestone 9A deliverables were completed and verified three
ways: 684 automated backend tests (639 inherited unchanged, 45 new,
including a real two-thread concurrency race) plus 478 automated
frontend tests (406 inherited unchanged, 72 new), a 43-check manual
verification pass against the real running stack combining direct API
calls and a genuine headless-Chromium browser session, and a full
regression pass confirming Milestones 5-8B remain unaffected. One real
defect — a new field that reached the entity and moderation layers but
never the public API response DTO — was found during manual
verification, fixed, and permanently regression-tested before this
branch was pushed.
