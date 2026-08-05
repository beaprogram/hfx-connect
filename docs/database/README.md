# Database Documentation

## Current Schema

As of Milestone 8B, the schema contains nine Flyway migrations:

| Version | File | Purpose |
|---|---|---|
| 1 | `backend/src/main/resources/db/migration/V1__enable_postgis_extension.sql` | Enables the PostGIS extension as a tracked, versioned migration rather than relying on the local Docker image's implicit initialization, so it is guaranteed present in every environment Flyway migrates (local, CI, and the managed production database in Milestone 12) |
| 2 | `backend/src/main/resources/db/migration/V2__create_categories_table.sql` | Creates the `categories` table |
| 3 | `backend/src/main/resources/db/migration/V3__create_resources_table.sql` | Creates the `resources` table |
| 4 | `backend/src/main/resources/db/migration/V4__create_users_table.sql` | Creates the `users` table |
| 5 | `backend/src/main/resources/db/migration/V5__create_refresh_sessions_table.sql` | Creates the `refresh_sessions` table |
| 6 | `backend/src/main/resources/db/migration/V6__create_resource_operating_hours.sql` | Creates the `resource_operating_hours` table |
| 7 | `backend/src/main/resources/db/migration/V7__add_resource_location.sql` | Adds `resources.location` (geography) and its GiST index |
| 8 | `backend/src/main/resources/db/migration/V8__create_saved_resources_table.sql` | Creates the `saved_resources` table |
| 9 | `backend/src/main/resources/db/migration/V9__create_resource_submissions_and_correction_reports.sql` | Creates the `resource_submissions` and `correction_reports` tables |

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
| `location` | `GEOGRAPHY(POINT, 4326)` | nullable — added by V7 (Milestone 7A); see ADR-012. Never mapped as a Hibernate entity field — read/written entirely through native SQL on `ResourceRepository` (`updateLocation`/`findNearby`) |

Indexes: `resources_category_id_idx` on `(category_id)` (category-based access, and
the FK's own lookups); `resources_active_name_idx` on `(active, name)` (mirrors
categories' pattern — active-resource listing sorted by name);
`idx_resources_location_gist` on `(location)` (Milestone 7A). A GiST spatial
index was selected because it supports the project's geography-based
ST_DWithin radius queries and was confirmed through EXPLAIN to be used by
PostgreSQL (`docs/decisions/ADR-012-postgis-nearby-search-design.md`).

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
| `role` | `VARCHAR(20)` | `NOT NULL`, one of `USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN`, defaults to `USER`. Registration (Milestone 5A) only ever writes `USER` — the other values have no assignment endpoint yet (no role-management API exists) but are now enforced as authorization authorities by `SecurityConfig` (Milestone 5C) wherever a row is set to one directly |
| `status` | `VARCHAR(30)` | `NOT NULL`, one of `ACTIVE`/`PENDING_VERIFICATION`/`SUSPENDED`/`DEACTIVATED`, defaults to `ACTIVE`. Registration only ever writes `ACTIVE` — see ADR-007 for why, not `PENDING_VERIFICATION` |
| `email_verified` | `BOOLEAN` | `NOT NULL`, defaults to `FALSE` — independent of `status`; always `false` after registration since no email-delivery mechanism exists yet |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |

The `users` table is exposed publicly through `AuthController`'s
`POST /api/v1/auth/register`, `/login`, `/refresh`, and `/logout` (Milestones 5A/5B
— see `docs/api/README.md`). There is still no read, update, or delete endpoint.

### `refresh_sessions`

One row per issued (and every rotated-away) refresh token — see
[ADR-008](../decisions/ADR-008-authentication-session-architecture.md) for the
full authentication-session design. `id` is `UUID`, matching `users`/`resources`'
reasoning (numerous, created continuously) — not `categories`'.

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | Primary key |
| `user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE CASCADE` — a session has no meaning independent of its account (see ADR-008's cascading-deletion decision) |
| `token_hash` | `VARCHAR(64)` | `NOT NULL`, `UNIQUE`, non-blank — a SHA-256 hex digest of the raw refresh token; **the raw token itself is never stored** |
| `family_id` | `UUID` | `NOT NULL` — groups every session descended from one original login through however many rotations; used by reuse detection to revoke an entire family at once |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `revoked_at` | `TIMESTAMPTZ` | nullable — set on rotation, logout, or reuse-detection's family-wide revocation |
| `replaced_by_session_id` | `UUID` | nullable, `REFERENCES refresh_sessions(id) ON DELETE SET NULL` — links a rotated-away session to the one that replaced it |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `last_used_at` | `TIMESTAMPTZ` | nullable — set when a session is successfully used to refresh |

No `user_agent`/`ip_address` columns — nothing in this milestone's product
requirements needs device or location tracking; see ADR-008 for why this was a
deliberate omission, not an oversight.

Indexes: `refresh_sessions_user_id_idx` on `(user_id)`, `refresh_sessions_family_id_idx`
on `(family_id)` (reuse detection's bulk-revoke query), `refresh_sessions_expires_at_idx`
on `(expires_at)` (a future expired-session cleanup job, not yet built). The `UNIQUE`
constraint on `token_hash` already provides its own lookup index.

Not exposed as its own resource — only read/written internally by
`AuthController`'s login/refresh/logout endpoints.

### `resource_operating_hours`

One row per resource/day-of-week — see
[ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md) for the full
timezone, overnight-interval, and open-now-calculation design this table
supports. `id` is `BIGINT GENERATED ALWAYS AS IDENTITY`, matching
`categories`' reasoning (never independently addressable in a URL or public
API) rather than `resources`'/`users`' `UUID`.

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` | Primary key, `GENERATED ALWAYS AS IDENTITY` |
| `resource_id` | `UUID` | `NOT NULL`, `REFERENCES resources(id) ON DELETE CASCADE` — an hours row has no meaning independent of its resource |
| `day_of_week` | `VARCHAR(9)` | `NOT NULL` — the readable `java.time.DayOfWeek` name (`MONDAY`..`SUNDAY`), checked against the seven valid values |
| `opens_at` | `TIME` | nullable — local time, no zone component; required (with `closes_at`) unless `closed` |
| `closes_at` | `TIME` | nullable — same; if earlier than `opens_at`, the interval crosses midnight |
| `closed` | `BOOLEAN` | `NOT NULL`, defaults `FALSE` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |

`UNIQUE (resource_id, day_of_week)` — at most one entry per resource/day. A
`CHECK` constraint enforces "closed days have no times; open days have both
times, and they are not equal" at the database level (equal `opens_at`/
`closes_at` was deliberately rejected as an implicit 24-hour convention —
see ADR-011).

Indexes: `resource_operating_hours_resource_id_idx` on `(resource_id)` —
supports both the single-resource lookup and the batch `findByResourceIdIn`
query the public resource list uses to avoid N+1 (see ADR-011's
"Batch-Loading" section).

Read/written by `ResourceService` (`getActiveById`/`getActiveBySlug`/
`search`, and `replaceOperatingHours` for the `ADMIN`/`MODERATOR`-only
`PUT /api/v1/resources/{id}/operating-hours` endpoint — see
`docs/api/README.md`). Never exposed as its own standalone resource.

### `saved_resources`

One row per (user, resource) a user has saved for later — see
[ADR-014](../decisions/ADR-014-saved-resources-design.md) for the full
design. `id` is `BIGINT GENERATED ALWAYS AS IDENTITY`, matching
`resource_operating_hours`' reasoning (V6): never independently
addressable in a URL or public API — always read/written as "this
user's saved-resource relation for this resource," identified by
`(user_id, resource_id)`.

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` | Primary key, `GENERATED ALWAYS AS IDENTITY` |
| `user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE CASCADE` — a saved-resource relation has no meaning independent of the account that saved it (same reasoning as `refresh_sessions.user_id`, V5) |
| `resource_id` | `UUID` | `NOT NULL`, `REFERENCES resources(id) ON DELETE CASCADE` — a saved-resource relation has no meaning independent of the resource being saved (same reasoning as `resource_operating_hours.resource_id`, V6) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |

`UNIQUE (user_id, resource_id)` — the authoritative duplicate-save
guard: `SavedResourceService`'s idempotent save checks existence first,
but a genuine concurrent-request race is resolved by this constraint,
not the application-level check alone (the losing insert's
`DataIntegrityViolationException` is caught and treated as success).

**Deactivating a resource (`resources.active = false`) is not a
delete** — a saved relation survives a resource being deactivated and
is simply excluded from the visible saved list
(`SavedResourceService.list`) until/unless the resource becomes active
again. Only a genuine row deletion (which no endpoint currently
performs) triggers the `ON DELETE CASCADE`.

Indexes: `saved_resources_user_id_created_at_idx` on
`(user_id, created_at DESC)` — supports the primary read path, "list
this user's saved resources, newest first" (matches the default
`savedAt`-descending ordering); `saved_resources_resource_id_idx` on
`(resource_id)` — Postgres does not automatically index a foreign
key's referencing column, and without this, deleting a resource
(cascading to its `saved_resources` rows) would require a sequential
scan of this table.

Read/written by `SavedResourceService` for the authenticated-only
`PUT`/`DELETE`/`GET`/`POST status` endpoints under
`/api/v1/users/me/saved-resources` — see `docs/api/README.md`. Never
exposed as its own standalone resource, and never readable for any
account other than the authenticated caller.

### `resource_submissions`

A user-proposed new community resource, awaiting review (Milestone 8B)
— see [ADR-015](../decisions/ADR-015-community-contribution-workflows-design.md)
for the full design. `id` is `UUID`, matching `resources.id`/`users.id`'s
reasoning rather than `saved_resources.id`'s: a submission genuinely is
addressable by its own id in a URL (`GET .../{submissionId}`), with no
alternative natural key.

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | Primary key |
| `submitted_by_user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE RESTRICT` — a submission is community-contribution history with standalone value, so deleting the account must not silently destroy it (the opposite policy from `saved_resources.user_id`'s `CASCADE`) |
| `category_id` | `BIGINT` | `NOT NULL`, `REFERENCES categories(id) ON DELETE RESTRICT` — mirrors `resources.category_id`'s existing policy |
| `name` | `VARCHAR(180)` | `NOT NULL` |
| `normalized_name` | `VARCHAR(180)` | `NOT NULL` — lowercased form of `name`, used only for the duplicate-pending index below |
| `short_description` | `VARCHAR(300)` | `NOT NULL` |
| `full_description` | `VARCHAR(4000)` | nullable |
| `address_line_1` | `VARCHAR(200)` | `NOT NULL` |
| `address_line_2` | `VARCHAR(200)` | nullable |
| `city` | `VARCHAR(100)` | `NOT NULL` |
| `province` | `VARCHAR(2)` | `NOT NULL` |
| `postal_code` | `VARCHAR(7)` | `NOT NULL` |
| `phone` | `VARCHAR(40)` | nullable |
| `email` | `VARCHAR(180)` | nullable |
| `website_url` | `VARCHAR(500)` | nullable |
| `cost_type` | `VARCHAR(20)` | `NOT NULL`, `CHECK` one of `FREE`/`LOW_COST`/`PAID`/`UNKNOWN` |
| `eligibility_information` | `VARCHAR(1000)` | nullable |
| `accessibility_information` | `VARCHAR(1000)` | nullable — a field with no equivalent on `resources` yet; captured here as free text for a future resource-model expansion, not tied to today's `CommunityResource` columns |
| `status` | `VARCHAR(20)` | `NOT NULL`, `CHECK` one of `PENDING_REVIEW`/`APPROVED`/`REJECTED`/`WITHDRAWN`, defaults `PENDING_REVIEW` |
| `submitted_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `withdrawn_at` | `TIMESTAMPTZ` | nullable |

`resource_submissions_pending_duplicate_key` — a **partial** unique
index, `UNIQUE (submitted_by_user_id, category_id, normalized_name)
WHERE status = 'PENDING_REVIEW'`: at most one pending submission per
account/category/name at a time, without ever blocking a later
resubmission once the earlier one is withdrawn/approved/rejected.

Indexes: `resource_submissions_owner_submitted_at_idx` on
`(submitted_by_user_id, submitted_at DESC)` (the owner-list read path);
`resource_submissions_category_id_idx` on `(category_id)`.

Read/written by `ResourceSubmissionService` for the authenticated-only
`POST`/`GET`/`GET {id}`/`POST {id}/withdraw` endpoints under
`/api/v1/users/me/resource-submissions`. Never exposed publicly and
never creates a `resources` row itself.

### `correction_reports`

A user-reported issue on an existing, active resource, awaiting review
(Milestone 8B). `id` is `UUID` for the same reasoning as
`resource_submissions.id` above.

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | Primary key |
| `reported_by_user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE RESTRICT` — same reasoning as `resource_submissions.submitted_by_user_id` |
| `resource_id` | `UUID` | nullable, `REFERENCES resources(id) ON DELETE SET NULL` — a report outlives its target resource being deleted (a third, distinct deletion policy from both `saved_resources.resource_id`'s `CASCADE` and the `RESTRICT` columns above — see ADR-015) |
| `resource_name_snapshot` | `VARCHAR(180)` | `NOT NULL` — captured once at creation; read directly for display so the report stays meaningful after `resource_id` becomes null |
| `resource_slug_snapshot` | `VARCHAR(220)` | `NOT NULL` — same reasoning |
| `issue_type` | `VARCHAR(30)` | `NOT NULL`, `CHECK` one of `GENERAL_INFORMATION`/`ADDRESS`/`CONTACT_INFORMATION`/`OPERATING_HOURS`/`ELIGIBILITY`/`ACCESSIBILITY`/`COST`/`RESOURCE_CLOSED`/`DUPLICATE_RESOURCE`/`OTHER` |
| `explanation` | `VARCHAR(2000)` | `NOT NULL` |
| `proposed_name` | `VARCHAR(180)` | nullable |
| `proposed_description` | `VARCHAR(4000)` | nullable — matches `resources.description` (a single field); no proposed short/full split, since the real resource has no such split to correct |
| `proposed_address_line_1` | `VARCHAR(200)` | nullable |
| `proposed_address_line_2` | `VARCHAR(200)` | nullable |
| `proposed_city` | `VARCHAR(100)` | nullable |
| `proposed_province` | `VARCHAR(2)` | nullable |
| `proposed_postal_code` | `VARCHAR(7)` | nullable |
| `proposed_phone` | `VARCHAR(40)` | nullable |
| `proposed_email` | `VARCHAR(180)` | nullable |
| `proposed_website_url` | `VARCHAR(500)` | nullable |
| `proposed_cost_type` | `VARCHAR(20)` | nullable, `CHECK` one of `FREE`/`LOW_COST`/`PAID`/`UNKNOWN` when present |
| `proposed_cost_details` | `VARCHAR(500)` | nullable |
| `proposed_eligibility` | `VARCHAR(1000)` | nullable |
| `status` | `VARCHAR(20)` | `NOT NULL`, same `CHECK`/default as `resource_submissions.status` |
| `submitted_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL`, defaults to `now()` |
| `withdrawn_at` | `TIMESTAMPTZ` | nullable |

Every `proposed_*` column is nullable and independently optional — a
`RESOURCE_CLOSED` or `OTHER` report can be explanation-only, with no
proposed correction at all.

`correction_reports_pending_duplicate_key` — a partial unique index,
`UNIQUE (reported_by_user_id, resource_id, issue_type) WHERE status =
'PENDING_REVIEW'`: at most one pending report per account/resource/
issue-type at a time, without blocking a different issue type on the
same resource or a later re-report.

Indexes: `correction_reports_owner_submitted_at_idx` on
`(reported_by_user_id, submitted_at DESC)`;
`correction_reports_resource_id_idx` on `(resource_id)`.

Read/written by `CorrectionReportService` for the authenticated-only
`POST /api/v1/resources/{resourceId}/correction-reports` and
`GET`/`GET {id}`/`POST {id}/withdraw` under
`/api/v1/users/me/correction-reports`. Never exposed publicly and never
modifies the `resources` row it targets.

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

Hibernate/JPA is used for reading and writing rows (`categories`, `resources`,
`resource_operating_hours`, `saved_resources`, `resource_submissions`, and
`correction_reports` all have JPA entities), but never for schema creation
or changes — `spring.jpa.hibernate.ddl-auto=validate` makes Hibernate check
that entity mappings match what Flyway already created, and fail startup if
they don't, rather than ever creating or altering a table itself.

## Planned Entities

The remaining entities anticipated by the product requirements are listed in
[docs/architecture/system-overview.md](../architecture/system-overview.md#data-model-direction):
`organizations`, `events`, and `resource_history` (`operating_hours` is now
implemented, as `resource_operating_hours` above — Milestone 6B;
`saved_resources` is now implemented, as above — Milestone 8A;
`resource_submissions`/`correction_reports` are now implemented, as above —
Milestone 8B, in place of the originally-anticipated `resource_reports`
name). These will be introduced as real Flyway migrations in later
milestones.
