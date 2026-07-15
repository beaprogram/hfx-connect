# Task 008: Category Database Schema

## Objective

Design and implement the `categories` table as a Flyway migration, with a
deliberate, documented strategy for case/whitespace-insensitive name uniqueness and
deterministic slug generation.

## Context

Part of Milestone 3A (Category Domain and API). First task of the milestone because
the entity, repository, and service all depend on the schema being settled — including
the normalization strategy, since that determines what columns exist.

## Scope

- `backend/src/main/resources/db/migration/V2__create_categories_table.sql`.
- [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md): primary
  key type, `normalized_name` uniqueness strategy, slug generation rules.

## Out of Scope

The `Category` entity, repository, service, and API (Task 009); any other domain's
schema.

## Acceptance Criteria

- Migration creates `categories` with `id`, `name`, `normalized_name`, `slug`,
  `description`, `active`, `created_at`, `updated_at`.
- `name` and `slug` are `NOT NULL`.
- `normalized_name` and `slug` each have independent `UNIQUE` constraints (see
  ADR-005 for why they must be independent, not one shared constraint).
- `active` defaults to `TRUE`.
- `created_at`/`updated_at` are `NOT NULL` with a `DEFAULT now()` safety net.
- A justified index exists supporting the list endpoint's actual query pattern
  (`active`, sorted by `name`) — no index added without a concrete query to justify
  it.
- Migration applies cleanly against a fresh database and against the existing
  V1-only database from Milestone 2B.

## Technical Approach

Chose a `BIGINT GENERATED ALWAYS AS IDENTITY` primary key rather than UUID, since
categories are a small, admin-managed reference list — consistent with the project's
own original schema sketch, which already implied this (`category_id BIGINT` on the
future `resources` table). Chose a stored `normalized_name` column over a PostgreSQL
expression index for case-insensitive uniqueness, so the exact normalization rule
(lowercase, whitespace-collapsed) lives in one place — Java, where it's unit-tested —
rather than being duplicated as SQL. Full reasoning, including why slug uniqueness
must be a second, independent constraint rather than reusing `normalized_name`'s:
[ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md).

## Testing Requirements

Covered by Task 010's repository/database tests
(`CategoryRepositoryIntegrationTest`): table/column existence, `NOT NULL`
enforcement, both unique constraints, `active` default, timestamp population.
Migration application itself is exercised every time any Testcontainers-backed test
runs, plus manually against the real docker-compose database.

## Result

Completed. The migration applies cleanly in both a fresh and an upgrading database,
and every constraint it defines is exercised by a dedicated test in Task 010.

## Related Commit

`build: add category schema migration`
