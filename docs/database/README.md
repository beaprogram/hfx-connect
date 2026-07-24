# Database Documentation

## Current Schema

As of Milestone 5A, the schema contains four Flyway migrations:

| Version | File | Purpose |
|---|---|---|
| 1 | `backend/src/main/resources/db/migration/V1__enable_postgis_extension.sql` | Enables the PostGIS extension as a tracked, versioned migration rather than relying on the local Docker image's implicit initialization, so it is guaranteed present in every environment Flyway migrates (local, CI, and the managed production database in Milestone 12) |
| 2 | `backend/src/main/resources/db/migration/V2__create_categories_table.sql` | Creates the `categories` table |
| 3 | `backend/src/main/resources/db/migration/V3__create_resources_table.sql` | Creates the `resources` table |
| 4 | `backend/src/main/resources/db/migration/V4__create_users_table.sql` | Creates the `users` table |

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

### `resources`

A real Halifax community service, classified by a category. `id` is `UUID`
(app-generated via Hibernate) rather than `categories`' `BIGINT` identity — resources
are numerous, created over time, and may be referenced in public URLs; see
[ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md), which
anticipated this when categories' ID type was decided.

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | Primary key |
| `category_id` | `BIGINT` | `NOT NULL`, `REFERENCES categories(id) ON DELETE RESTRICT` |
| `name` | `VARCHAR(180)` | `NOT NULL`, non-blank |
| `slug` | `VARCHAR(220)` | `NOT NULL`, `UNIQUE`, format-checked (same rule as categories' slugs — lowercase alphanumeric groups separated by single hyphens); **stable after creation**, never changed by an update |
| `description` | `VARCHAR(4000)` | `NOT NULL`, non-blank |
| `address_line_1` | `VARCHAR(200)` | `NOT NULL`, non-blank |
| `address_line_2` | `VARCHAR(200)` | nullable |
| `city` | `VARCHAR(100)` | `NOT NULL`, non-blank |
| `province` | `VARCHAR(2)` | `NOT NULL`, must be one of the 13 real Canadian province/territory codes |
| `postal_code` | `VARCHAR(7)` | `NOT NULL`, normalized Canadian format `"A1A 1A1"`, first letter excludes D/F/I/O/Q/U per Canada Post |
| `phone` | `VARCHAR(40)` | nullable |
| `email` | `VARCHAR(180)` | nullable |
| `website_url` | `VARCHAR(500)` | nullable |
| `cost_type` | `VARCHAR(20)` | `NOT NULL`, one of `FREE`/`LOW_COST`/`PAID`/`UNKNOWN`, defaults to `UNKNOWN` |
| `cost_details` | `VARCHAR(500)` | nullable |
| `eligibility` | `VARCHAR(1000)` | nullable |
| `verification_status` | `VARCHAR(20)` | `NOT NULL`, one of `UNVERIFIED`/`VERIFIED`, defaults to `UNVERIFIED` (no mutator exists yet — Milestone 9 moderation) |
| `active` | `BOOLEAN` | `NOT NULL`, defaults to `TRUE` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |

Indexes: `resources_category_id_idx` on `(category_id)` (category-based access, and
the FK's own lookups); `resources_active_name_idx` on `(active, name)` (mirrors
categories' pattern — active-resource listing sorted by name).

**Note on `category_id`'s type:** an earlier planning document for this milestone
suggested a `UUID` foreign key. That is inconsistent with `categories.id`, which is
`BIGINT` (an already-applied, unmodifiable migration) — a foreign key must match its
referenced column's type. `category_id` is `BIGINT`.

The `resources` table is now exposed publicly through `ResourceController`
(`/api/v1/resources` — Milestone 3C, see `docs/api/README.md`) for creation and
active-only reads; `update`/`deactivate` remain business-layer-only (no HTTP
endpoint), exercised directly by automated tests.

A full entity-relationship diagram will be added here once a third related table
exists and a diagram would meaningfully show relationships beyond a single foreign
key.

### `users`

A registered HFX Connect account. `id` is `UUID`, matching `resources`' reasoning,
not `categories`': users are numerous and self-registered over time by many
independent actors — see
[ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md).

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | Primary key |
| `email` | `VARCHAR(180)` | `NOT NULL`, non-blank — stored as submitted (trimmed only), for display |
| `normalized_email` | `VARCHAR(180)` | `NOT NULL`, `UNIQUE` — lowercased, trimmed form of `email`; the authoritative uniqueness key (mirrors `categories.normalized_name`) |
| `password_hash` | `VARCHAR(200)` | `NOT NULL`, non-blank — a BCrypt hash (strength 12), never plaintext |
| `role` | `VARCHAR(20)` | `NOT NULL`, one of `USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN`, defaults to `USER`. Registration (Milestone 5A) only ever writes `USER` — the other values are reserved for Milestone 5C/organization/moderation work |
| `status` | `VARCHAR(30)` | `NOT NULL`, one of `ACTIVE`/`PENDING_VERIFICATION`/`SUSPENDED`/`DEACTIVATED`, defaults to `ACTIVE`. Registration only ever writes `ACTIVE` — see ADR-007 for why, not `PENDING_VERIFICATION` |
| `email_verified` | `BOOLEAN` | `NOT NULL`, defaults to `FALSE` — independent of `status`; always `false` after registration since no email-delivery mechanism exists yet |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |

The `users` table is exposed publicly only through `AuthController`'s
`POST /api/v1/auth/register` (Milestone 5A — see `docs/api/README.md`). There is no
read, update, login, or delete endpoint yet.

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

Hibernate/JPA is used for reading and writing rows (`categories` and `resources` both
have JPA entities), but never for schema creation or changes —
`spring.jpa.hibernate.ddl-auto=validate` makes Hibernate check that entity mappings
match what Flyway already created, and fail startup if they don't, rather than ever
creating or altering a table itself.

## Planned Entities

The remaining entities anticipated by the product requirements are listed in
[docs/architecture/system-overview.md](../architecture/system-overview.md#data-model-direction):
`organizations`, `operating_hours`, `saved_resources`, `resource_reports`,
`resource_submissions`, `events`, and `resource_history`. These will be introduced as
real Flyway migrations in later milestones.
