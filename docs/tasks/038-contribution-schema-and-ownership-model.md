# Task 038: Contribution Schema and Ownership Model

## Objective

Establish the persistence foundation for community contributions: the
`V9` Flyway migration and two focused entities (`ResourceSubmission`,
`CorrectionReport`), with a clearly documented ownership, deletion, and
duplicate-pending design.

## Context

The first task of Milestone 8B. Verified the next available Flyway
version (V9, following V8's saved-resources migration) and the next
available ADR/task-doc numbers before writing anything. Also widened
`ResourceValidation`, `CategoryNotFoundException`, and
`InactiveCategoryException` for cross-package reuse — the same pattern
`ResourceNotFoundException.byId` established in Milestone 8A.

## Scope

- `backend/src/main/resources/db/migration/V9__create_resource_submissions_and_correction_reports.sql`
  — both tables, `UUID` primary keys, `RESTRICT`/`SET NULL` deletion
  policies, status/issue-type `CHECK` constraints, partial-unique
  duplicate-pending indexes.
- `com.hfxconnect.resourcesubmission.ResourceSubmission`,
  `SubmissionStatus` — plain `submittedByUserId` column, lazy
  unidirectional `@ManyToOne Category`, `withdraw()` mutator.
- `com.hfxconnect.correctionreport.CorrectionReport`,
  `CorrectionReportStatus`, `IssueType` — plain `reportedByUserId`
  column, lazy unidirectional nullable `@ManyToOne CommunityResource`,
  resource-name/slug snapshot columns, `withdraw()` mutator.
- `InvalidContributionStatusException`
  (`com.hfxconnect.common.error`) — shared withdrawal-guard exception.
- `ResourceValidation` (and its length constants), `CategoryNotFoundException.forId`,
  `InactiveCategoryException.forId` widened to `public`/`public static`.

## Out of Scope

Repositories, services, controllers, and DTOs — see Tasks 039-040.

## Design Decisions

See [ADR-015](../decisions/ADR-015-community-contribution-workflows-design.md)
for the full rationale: two focused tables vs. one generic contribution
table, `UUID` vs. `BIGINT` primary keys, the `RESTRICT`/`SET NULL`
deletion-policy split, and the partial-unique duplicate-pending index
design.

## Acceptance Criteria

- [x] `V9` migration applies cleanly against a fresh database.
- [x] Hibernate's `ddl-auto=validate` accepts both entity mappings with
      no schema mismatch.
- [x] Both `RESTRICT` foreign keys, the `SET NULL` foreign key, and
      both partial-unique duplicate-pending indexes verified directly
      against the real database (repository integration tests — see
      Task 039).
- [x] `FlywayMigrationIntegrationTest` updated and passing.

## Evidence

Commits on branch `milestone/08b-submissions-corrections`; see
`docs/database/README.md`'s `resource_submissions`/`correction_reports`
sections and `docs/milestones/milestone-08b-submissions-corrections.md`.
