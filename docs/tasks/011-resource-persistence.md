# Task 011: Resource Persistence

## Objective

Create the `resources` table and the `CommunityResource` JPA domain (entity, enums,
repository) it maps to, with database-enforced integrity for every rule that can be
expressed at the database level.

## Context

Part of Milestone 3B (Resource Persistence and Business Layer). Numbered 011, not 010
— `010-category-api-and-tests.md` already exists from Milestone 3A.

## Scope

- `backend/src/main/resources/db/migration/V3__create_resources_table.sql`.
- `com.hfxconnect.resource`: `CommunityResource`, `CostType`, `VerificationStatus`,
  `ResourceRepository`.
- Extracted `com.hfxconnect.common.text.SlugGenerator` from
  `category.CategorySlugGenerator` (moved, not duplicated) since the resource domain
  needs byte-for-byte the same slug algorithm.
- Added `Category.deactivate()` (Milestone 3A entity) — needed by Milestone 3B's own
  tests to produce an inactive category, which nothing in the codebase could do before.

## Out of Scope

Business-layer validation/orchestration (Task 012); any HTTP surface (Milestone 3C).

## Acceptance Criteria

- `V3` applies cleanly after `V1`/`V2`, on both a fresh Testcontainers database and the
  real docker-compose database.
- Hibernate schema validation (`ddl-auto=validate`) passes against the new entity.
- Database constraints proven with real `DataIntegrityViolationException`s in
  integration tests: not-null/not-blank fields, slug uniqueness and format, province
  allowlist, postal code format, cost-type/verification-status allowlists, category
  foreign key with `ON DELETE RESTRICT`.
- `category_id` is `BIGINT`, matching `categories.id` exactly — corrected from the
  sub-milestone brief's suggested `UUID`, which would be a type mismatch against the
  already-merged `V2` migration.

## Technical Approach

Followed the exact structural pattern `category.Category`/`CategoryRepository`
established in Milestone 3A (see `docs/architecture/backend-architecture.md`):
constructor-based creation, no public setters beyond focused mutation methods,
`@PrePersist`/`@PreUpdate` timestamps, a repository limited to the queries actually
needed. Deviated only where the resource domain's own requirements demanded it: a
Hibernate-generated `UUID` id instead of `BIGINT IDENTITY` (see
[ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md), which
already anticipated this), and a `LAZY`, non-cascading `@ManyToOne` to `Category`.

## Testing Requirements

`ResourceRepositoryIntegrationTest` (Testcontainers, real `postgis/postgis:17-3.5`):
table structure, persistence with category, active/inactive slug lookup, active
pagination, category-filtered pagination, timestamp population, enum round-tripping,
slug uniqueness, category delete-restriction, and four distinct constraint-violation
scenarios (missing name, invalid cost type, invalid province, invalid slug format).

## Result

Completed. 15 tests pass (14 in `ResourceRepositoryIntegrationTest`, plus one new
check in the existing `FlywayMigrationIntegrationTest` confirming all three
migrations are recorded exactly once, in order), all against the real pinned PostGIS
image, not H2 or mocks.

## Related Commit

`feat: add resource persistence model and migration`
