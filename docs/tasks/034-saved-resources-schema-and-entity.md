# Task 034: Saved Resources Schema and Entity

## Objective

Establish the persistence foundation for saved resources: the `V8`
Flyway migration and a focused `SavedResource` JPA entity, with no
premature bidirectional collections or generic bookmark abstraction.

## Context

The first task of Milestone 8A. Verified the next available Flyway
version (V8, following V7's resource-location migration) and the next
available ADR/task-doc numbers before writing anything.

## Scope

- `backend/src/main/resources/db/migration/V8__create_saved_resources_table.sql`
  — `saved_resources` table: `BIGINT GENERATED ALWAYS AS IDENTITY`
  primary key, `user_id`/`resource_id` `UUID NOT NULL` with
  `ON DELETE CASCADE` on both, `UNIQUE (user_id, resource_id)`,
  `created_at`. Two indexes: `(user_id, created_at DESC)` for the
  primary list-read path, `(resource_id)` since Postgres does not
  automatically index a foreign key's referencing column.
- `com.hfxconnect.savedresource.SavedResource` — `id`, plain `userId`
  (`UUID`, no `@ManyToOne` to `User`), lazy unidirectional
  `@ManyToOne CommunityResource resource`, `createdAt` via
  `@PrePersist`. No `equals`/`hashCode` override (matches the existing
  entity convention in this codebase).
- Widened `ResourceNotFoundException.byId` from package-private
  `static` to `public static` for cross-package reuse from
  `SavedResourceService`.
- `FlywayMigrationIntegrationTest` updated to assert 8 migrations
  applied in order.

## Out of Scope

Repository, service, controller, and DTOs — see Task 035.

## Design Decisions

See [ADR-014](../decisions/ADR-014-saved-resources-design.md) for full
rationale: focused entity vs. generic bookmark framework,
unidirectional-only references, and why the relation outlives a
resource's active flag but not its row.

## Acceptance Criteria

- [x] `V8` migration applies cleanly against a fresh database.
- [x] Hibernate's `ddl-auto=validate` accepts the `SavedResource`
      mapping with no schema mismatch.
- [x] `UNIQUE (user_id, resource_id)` and both `ON DELETE CASCADE`
      clauses verified directly against the real database (repository
      integration tests — see Task 035).
- [x] `FlywayMigrationIntegrationTest` updated and passing.

## Evidence

Commits on branch `milestone/08a-saved-resources`; see
`docs/database/README.md`'s `saved_resources` section and
`docs/milestones/milestone-08a-saved-resources.md`.
