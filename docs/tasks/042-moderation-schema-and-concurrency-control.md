# Task 042: Moderation Schema, Audit Table, and Concurrency Control

## Objective

Establish the persistence foundation for Milestone 9A: the `V10`
migration (review metadata on both contribution tables, `resources
.last_verified_at`, the new `moderation_audit_events` table), and the
row-level locking mechanism every review decision relies on.

## Context

First task of Milestone 9A. Verified V9 was the latest migration
before writing V10, and that ADR-016/task-042 were the next available
numbers. Extended `CommunityResource`/`ResourceService`/
`ResourceRepository` rather than duplicating resource-creation logic in
the new `moderation` package — see ADR-016's "Verification Policy" for
why `createVerified` exists as an overload of the same internal
`createInternal` method `create` already uses.

## Scope

- `V10__add_moderation_workflow_and_audit.sql` — `resources
  .last_verified_at`; `resource_submissions`/`correction_reports`
  review-metadata columns with present-together/absent-together
  `CHECK` constraints; `resulting_resource_id`'s partial-unique index;
  the append-only `moderation_audit_events` table.
- `CommunityResource.markVerified`, `ResourceService.createVerified`
  (shares `createInternal` with the existing `create`),
  `ResourceRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`).
- `ResourceSubmission`/`CorrectionReport` — `approve`/`reject`
  mutators, review-metadata fields, `findByIdForReview` (locking) and
  `findForModerationQueue` repository methods.
- `com.hfxconnect.moderation` package scaffolding: `ContributionType`,
  `ModerationAction`, `ModerationDecision`, `ModerationAuditEvent` (+
  repository, using `@JdbcTypeCode(SqlTypes.JSON)` for the snapshot
  columns), `ModerationSnapshots`, `ModerationAuditRecorder`.
- `ResourceSubmissionNotFoundException.byId`,
  `CorrectionReportNotFoundException.byId`,
  `CorrectionReportTargetResponse.from` widened to `public`/`public
  static` for cross-package reuse by `moderation`.

## Out of Scope

Review services, controllers, DTOs — see Tasks 043-044. Frontend — see
Task 045.

## Design Decisions

See [ADR-016](../decisions/ADR-016-moderation-workflow-design.md) for
the full rationale: `PESSIMISTIC_WRITE` locking over `@Version`, why
the target *resource* row is locked separately from the *report* row
for correction application, the audit table's one-row-per-effect model,
and the explicit-field-map snapshot policy.

## Acceptance Criteria

- [x] `V10` applies cleanly against a fresh database; `V1`-`V9`
      unchanged.
- [x] Hibernate validates cleanly against the new/extended schema.
- [x] Review-metadata `CHECK` constraints verified directly (repository/
      migration-level tests — see Task 043).
- [x] `FlywayMigrationIntegrationTest` updated for `V10`.
- [x] `ResourceRepositoryIntegrationTest`'s exact-column-list assertion
      updated for `last_verified_at`.

## Evidence

Commits on branch `milestone/09a-moderation-workflow`; see
`docs/database/README.md`'s `moderation_audit_events` section and
`docs/milestones/milestone-09a-moderation-workflow.md`.
