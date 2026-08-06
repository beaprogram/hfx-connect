# Task 044: Correction Report Review, Application, and Audit History API

## Objective

Let a `MODERATOR`/`ADMIN` review a correction report — approving with
`applyProposedChanges`/`deactivateResource`, or rejecting — and expose
the moderation audit trail via a moderator-only read API.

## Context

Third task of Milestone 9A. The most complex service in this
milestone: two independent decision flags, two issue types with no
supported automated application, a second row lock (the target
resource, on top of the report's own), and a field-merge-then-revalidate
step that reuses `ResourceValidation`'s public static field helpers
directly (not its package-private `validate()`/`Normalized`, which
Milestone 8B deliberately did not widen — see the service's own
Javadoc).

## Scope

- `CorrectionReportReviewService` — `queue`, `detail`, `approve`,
  `reject`; self-review + already-reviewed checks;
  `deactivateResource`/`applyProposedChanges` validation;
  field-merge/revalidate/apply; before/after field-snapshot builders.
- `ModerationCorrectionReportController` — all five routes.
- `ModerationAuditQueryService`, `ModerationAuditController` — the
  global audit list and both per-contribution `/audit-events` routes.
- DTOs: `CorrectionReportQueueItemResponse`/`PageResponse`,
  `CorrectionReportModerationDetailResponse`,
  `CurrentResourceStateResponse`, `CorrectionApprovalRequest`,
  `ModerationAuditEventResponse`/`PageResponse`.
- `CorrectionReportResponse` (owner-facing, Milestone 8B) — extended
  with `reviewedAt`/`reviewReason`/`changesApplied`.

## Out of Scope

Resource-submission review — Task 043.

## Design Decisions

See [ADR-016](../decisions/ADR-016-moderation-workflow-design.md)'s
"Correction Application Policy" and "Audit Model" sections: only the
report's actual 13 supported scalar fields are ever applied;
`OPERATING_HOURS`/`DUPLICATE_RESOURCE` honestly reject automated
application; the target resource row is locked independently of the
report row to prevent a lost-update race between two different reports
on the same resource.

## Acceptance Criteria

- [x] Only present `proposed*` fields are applied; absent fields
      preserve the resource's current value; category never touched.
- [x] `RESOURCE_CLOSED` + `deactivateResource=true` deactivates the
      resource; any other issue type with that flag returns `400
      INVALID_DEACTIVATION_REQUEST`.
- [x] `applyProposedChanges=true` for `OPERATING_HOURS`/
      `DUPLICATE_RESOURCE` returns `400
      UNSUPPORTED_CORRECTION_APPLICATION`.
- [x] Approving with neither flag still marks `APPROVED`, applies
      nothing, records a bare `REVIEW_DECISION` audit row.
- [x] A real two-thread concurrent-approval test on two reports
      targeting the same resource confirms exactly one succeeds and the
      field is applied exactly once.
- [x] `/audit-events` routes return `403` for a non-moderator, `200`
      with real audit rows for a moderator.

## Evidence

`backend/src/test/java/com/hfxconnect/moderation/
CorrectionReportReviewServiceIntegrationTest.java`,
`ModerationCorrectionReportApiIntegrationTest.java`,
`ModerationAuditEventRepositoryIntegrationTest.java`,
`ModerationConcurrencyIntegrationTest.java` (correction half). Commits
on branch `milestone/09a-moderation-workflow`.
