# Task 006: Docker Compose PostgreSQL/PostGIS Environment

## Objective

Provide a reproducible local PostgreSQL + PostGIS database via Docker Compose, with a
pinned image version, persistent storage, and a health check.

## Context

Part of Milestone 2B (Database Environment). This is the first task of the milestone
because the backend's database configuration (Task 007) needs a running, known-good
database to connect to and be tested against.

## Scope

- `docker-compose.yml`: `database` service on `postgis/postgis:17-3.5`, named volume
  `hfx_connect_postgres_data`, `pg_isready` health check, environment-variable
  substitution with safe defaults (`POSTGRES_DB`, `POSTGRES_USER`,
  `POSTGRES_PASSWORD`, `POSTGRES_PORT`).
- Installed a local Docker runtime (Colima + the Docker CLI/Compose plugin via
  Homebrew, since neither Docker nor Docker Desktop was present on this machine), and
  resolved a `docker-credential-desktop` credential-helper reference left over from a
  prior Docker Desktop configuration that no longer existed.

## Out of Scope

Any backend configuration or code (Task 007); any application schema.

## Acceptance Criteria

- `docker compose config` succeeds.
- `docker compose up -d` starts a container that reaches `healthy`.
- `postgis_full_version()` confirms PostGIS is actually installed and enabled, not
  just that the image was pulled.
- A named volume is used, and data survives a `docker compose stop` / `docker compose start`
  cycle (verified with a manual marker row, later confirmed again via
  `flyway_schema_history` after Task 007 introduced it).
- `docker compose down -v` (destructive) is clearly distinguished from `docker compose down`/`stop`
  (non-destructive) in documentation.

## Technical Approach

The official `postgis/postgis` image publishes no `linux/arm64` build — confirmed via
`docker manifest inspect` before adopting it, rather than discovering this via a failed
pull. Rather than switching to an unofficial multi-arch mirror, the service is pinned to
`platform: linux/amd64` and run under Colima's QEMU-based emulation, which was smoke-tested
directly (a throwaway container, `pg_isready`, and a real `CREATE EXTENSION postgis;` /
`postgis_full_version()` check) before being wired into `docker-compose.yml`. This keeps
the project depending on the canonical, officially published image.

## Testing Requirements

`docker compose config`, `docker compose up -d`, `docker compose ps` (confirm
`healthy`), a direct `psql` version/PostGIS check, and a stop/start persistence check —
all run manually and recorded in the development log.

## Result

Completed. The database starts reliably, reports healthy, has PostGIS enabled, and
persists data across restarts. Destructive vs. non-destructive shutdown is documented
in the root README and `backend/README.md`.

## Related Commit

`build: add PostgreSQL and PostGIS with Docker Compose`
