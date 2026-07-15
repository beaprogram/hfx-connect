# Database Documentation

## Current Schema

As of Milestone 3A, the schema contains two Flyway migrations:

| Version | File | Purpose |
|---|---|---|
| 1 | `backend/src/main/resources/db/migration/V1__enable_postgis_extension.sql` | Enables the PostGIS extension as a tracked, versioned migration rather than relying on the local Docker image's implicit initialization, so it is guaranteed present in every environment Flyway migrates (local, CI, and the managed production database in Milestone 12) |
| 2 | `backend/src/main/resources/db/migration/V2__create_categories_table.sql` | Creates the `categories` table |

### `categories`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` (identity) | Primary key |
| `name` | `VARCHAR(120)` | `NOT NULL` |
| `normalized_name` | `VARCHAR(120)` | `NOT NULL`, `UNIQUE` — lowercased, whitespace-collapsed form of `name`; enforces case/whitespace-insensitive duplicate prevention (see [ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md)) |
| `slug` | `VARCHAR(160)` | `NOT NULL`, `UNIQUE` — deterministically derived from `name`, independent of `normalized_name` uniqueness (two different names can generate the same slug — see ADR-005) |
| `description` | `VARCHAR(2000)` | nullable |
| `active` | `BOOLEAN` | `NOT NULL`, defaults to `TRUE` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |

Index: `categories_active_name_idx` on `(active, name)`, supporting the
`GET /api/v1/categories` list query (filter by `active`, sort by `name`).

`categories` has no foreign keys yet; `resources.category_id` will reference it once
introduced in Milestone 3B.

The `resources` table (and every other entity anticipated by the product
requirements) is introduced in a later milestone. A full entity-relationship diagram
will be added here once more than one table exists — a single-table diagram would add
no value yet.

## Database Engine

PostgreSQL 17 with the PostGIS 3.5 extension, via the `postgis/postgis:17-3.5` Docker
image locally (see [docker-compose.yml](../../docker-compose.yml) and
[ADR-002](../decisions/ADR-002-postgresql-and-postgis.md)).

## Migration Tooling

Flyway is the sole authority for schema changes (no ORM-driven schema generation is
used or permitted). Because Spring Boot 4.1 does not ship built-in Flyway
auto-configuration, migrations are triggered by an explicit configuration class —
see [ADR-004](../decisions/ADR-004-manual-flyway-configuration.md) for why, and
`backend/README.md` for how to run and inspect migrations locally.

Hibernate/JPA is used for reading and writing rows (`categories` has a JPA entity,
`com.hfxconnect.category.Category`), but never for schema creation or changes —
`spring.jpa.hibernate.ddl-auto=validate` makes Hibernate check that entity mappings
match what Flyway already created, and fail startup if they don't, rather than ever
creating or altering a table itself.

## Planned Entities

The remaining entities anticipated by the product requirements are listed in
[docs/architecture/system-overview.md](../architecture/system-overview.md#data-model-direction):
`users`, `organizations`, `resources`, `operating_hours`, `saved_resources`,
`resource_reports`, `resource_submissions`, `events`, and `resource_history`. These
will be introduced as real Flyway migrations in later milestones.
