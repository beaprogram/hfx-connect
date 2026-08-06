# Task 043: Resource Submission Review Service and Publication

## Objective

Let a `MODERATOR`/`ADMIN` view the resource-submission queue, inspect
detail, and approve or reject — approval publishing a real, `VERIFIED`
public resource in the same transaction.

## Context

Second task of Milestone 9A, built directly on Task 042's locking
primitives and `ResourceService.createVerified`. The description
mapping (`fullDescription` preferred over `shortDescription` when
present) is this task's one genuinely new field-mapping decision — see
ADR-016.

## Scope

- `ResourceSubmissionReviewService` — `queue`, `detail`, `approve`,
  `reject`; self-review + already-reviewed checks; publication-conflict
  handling (`ResourceConflictException` caught and re-thrown as
  `ResourcePublicationConflictException`).
- `ModerationResourceSubmissionController` — all five routes.
- DTOs: `ResourceSubmissionQueueItemResponse`/`PageResponse`,
  `ResourceSubmissionModerationDetailResponse`,
  `ModerationDecisionRequest`.
- `ResourceSubmissionResponse` (owner-facing, Milestone 8B) — extended
  with `reviewedAt`/`reviewReason`/`resultingResource`.
- `ResultingResourceSummaryResponse` (`resourcesubmission` package —
  not `moderation`, to keep the cross-package dependency one-directional;
  see its own Javadoc).

## Out of Scope

Correction-report review, audit-history API — Task 044.

## Design Decisions

Publication reuses `ResourceService.createVerified` end to end (same
validation, same slug-uniqueness conflict detection) rather than a
second, parallel resource-creation code path — see ADR-016's own
"do not create a second resource-validation... architecture" framing,
carried forward from ADR-015.

## Acceptance Criteria

- [x] Approval creates exactly one `VERIFIED` public resource with a
      real `lastVerifiedAt`; rejection never creates one.
- [x] Self-review blocked for `MODERATOR` and `ADMIN`.
- [x] A second review attempt on an already-decided submission returns
      `409 CONTRIBUTION_ALREADY_REVIEWED`.
- [x] A publication conflict returns `409
      RESOURCE_PUBLICATION_CONFLICT`, leaves the submission pending.
- [x] A real two-thread concurrent-approval test confirms exactly one
      decision succeeds and exactly one resource is created.
- [x] Owner-facing response never includes the reviewer's identity.

## Evidence

`backend/src/test/java/com/hfxconnect/moderation/
ResourceSubmissionReviewServiceIntegrationTest.java`,
`ModerationResourceSubmissionApiIntegrationTest.java`,
`ModerationConcurrencyIntegrationTest.java` (submission half). Commits
on branch `milestone/09a-moderation-workflow`.
