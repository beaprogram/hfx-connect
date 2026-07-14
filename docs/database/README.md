# Database Documentation

## Current Schema

As of Milestone 2B, the schema contains exactly one Flyway migration:

| Version | File | Purpose |
|---|---|---|
| 1 | `backend/src/main/resources/db/migration/V1__enable_postgis_extension.sql` | Enables the PostGIS extension as a tracked, versioned migration rather than relying on the local Docker image's implicit initialization, so it is guaranteed present in every environment Flyway migrates (local, CI, and the managed production database in Milestone 12) |

No application tables exist yet. The `categories` and `resources` tables (and every
other entity anticipated by the product requirements) are introduced in Milestone 3.
A full entity-relationship diagram will be added here once that schema exists — this
file intentionally does not describe a schema that isn't in the repository yet.

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

## Planned Entities

The entities anticipated by the product requirements are listed in
[docs/architecture/system-overview.md](../architecture/system-overview.md#data-model-direction):
`users`, `organizations`, `categories`, `resources`, `operating_hours`,
`saved_resources`, `resource_reports`, `resource_submissions`, `events`, and
`resource_history`. These will be introduced as real Flyway migrations starting in
Milestone 3, and this document will grow an entity-relationship diagram at that point.
