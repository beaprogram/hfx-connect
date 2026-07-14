# HFX Connect — Backend

The Spring Boot application for the HFX Connect REST API. See the
[repository root README](../README.md) for the product overview, and
[docs/architecture/system-overview.md](../docs/architecture/system-overview.md) for
the intended API and module design.

**Status:** database-connected application skeleton (Milestone 2B). PostgreSQL/PostGIS
runs locally via Docker Compose, Flyway manages schema migrations, and `/actuator/health`
reports live database health. There are still no domain tables or REST endpoints —
those are introduced starting in Milestone 3 (category/resource domain).

## Stack

Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-jdbc`,
`spring-boot-starter-actuator`), PostgreSQL JDBC driver, Flyway, Maven (via the Maven
Wrapper — no local Maven installation required).

## Local Development

Requires a JDK 21 on `JAVA_HOME` (or discoverable on `PATH`), and Docker (or a
Docker-compatible runtime — see the Colima note below) for the database.

1. Start the database from the repository root:

   ```bash
   docker compose up -d
   ```

2. Run the backend:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

The application starts on [http://localhost:8080](http://localhost:8080) and connects
to the database using the defaults in `src/main/resources/application.properties`,
which match `docker-compose.yml`'s defaults exactly — no environment variables are
required for standard local development. Flyway runs automatically on startup; see
`src/main/resources/db/migration/`.

There are no REST routes mapped yet, so requests to most paths return `404` — this is
expected until Milestone 3 adds real endpoints. `/actuator/health` is the exception;
see below.

### Overriding the Database Connection

Copy `.env.example` to `.env` and edit it, then export it into your shell before
running the backend (Spring Boot does not load `.env` files automatically):

```bash
set -a; source .env; set +a
./mvnw spring-boot:run
```

Variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

## Health Endpoint

`GET /actuator/health` reports `{"status":"UP", ...}` when the application and its
database connection are healthy, and a non-2xx status with `"status":"DOWN"` when the
database is unreachable. Only the top-level status and default health groups are
visible to unauthenticated requests (`management.endpoint.health.show-details=when-authorized`)
— component-level detail (which would reveal datasource/connection internals) is
withheld until authenticated requests are possible (Milestone 5). Only the `health`
endpoint is exposed; no other Actuator endpoints are enabled.

## Commands

| Command | Purpose |
|---|---|
| `./mvnw spring-boot:run` | Run the application locally (requires the database running) |
| `./mvnw test` | Run tests, including Testcontainers-backed integration tests (requires a working Docker daemon) |
| `./mvnw verify` | Run the full build lifecycle including tests |

## Testing

Integration tests (`*IntegrationTest` and the application-context test) run against a
real, ephemeral `postgis/postgis:17-3.5` container via Testcontainers — not a generic
Postgres image and not mocks — using Spring Boot's `@ServiceConnection` support. See
`src/test/java/com/hfxconnect/AbstractPostgresIntegrationTest.java`. A single container
is shared across all test classes in a run (the Testcontainers "singleton container"
pattern) and cleaned up automatically by Testcontainers' Ryuk reaper when the JVM
exits.

## Project Structure

```
backend/
  src/main/java/com/hfxconnect/
    HfxConnectApplication.java              Application entry point
    common/config/
      FlywayMigrationConfig.java                Runs Flyway on startup (see ADR-004)
  src/main/resources/
    application.properties                        Base configuration (env-based DB connection, Actuator)
    db/migration/
      V1__enable_postgis_extension.sql         First Flyway migration
  src/test/java/com/hfxconnect/
    AbstractPostgresIntegrationTest.java   Shared Testcontainers setup
    HfxConnectApplicationTests.java          Application-context smoke test
    FlywayMigrationIntegrationTest.java     Migration + idempotency tests
    HealthEndpointIntegrationTest.java      Health endpoint tests
```

Domain packages (`auth/`, `category/`, `resource/`, `search/`, `moderation/`, `event/`,
etc., as described in
[system-overview.md](../docs/architecture/system-overview.md#backend-module-structure))
are added starting in Milestone 3, once there is real domain logic to put in them —
this avoids empty, speculative package scaffolding.

## Troubleshooting

**`role "hfx_connect" does not exist` / connects but to the wrong database.**
Something else is already listening on port 5432 (a native PostgreSQL install, another
project's container, etc.) — your connection is reaching that instance instead of this
project's container. Check with `lsof -nP -iTCP:5432 -sTCP:LISTEN`. Fix by overriding
the port for both the database and the backend, so they still point at each other:

```bash
POSTGRES_PORT=55432 docker compose up -d
DB_PORT=55432 ./mvnw spring-boot:run
```

**`FATAL: password authentication failed` after changing `POSTGRES_PASSWORD`.**
PostgreSQL only applies `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` the first
time a fresh volume is initialized. If you change credentials after the volume already
has data, either match `DB_PASSWORD` to the original value, or reset the local database
(destroys local data): `docker compose down -v && docker compose up -d`.

**Container shows `unhealthy` or the backend can't connect at all.**
Check `docker compose ps` and `docker compose logs database`. Confirm the container is
actually running (`docker compose up -d` was run from the repository root, where
`docker-compose.yml` lives) before starting the backend — the backend fails fast (by
design) if it cannot reach the database at startup.

**Using Colima instead of Docker Desktop.** Testcontainers-backed tests (`./mvnw test`)
need to know where the Docker socket is, and where it appears *inside* the Colima VM
(for bind-mounting into Testcontainers' own sidecar containers), which differs from
where the `docker` CLI reaches it from the host:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw test
```

Also add `"cliPluginsExtraDirs": ["/opt/homebrew/lib/docker/cli-plugins"]` to
`~/.docker/config.json` if `docker compose` is not found, and remove any
`"credsStore": "desktop"` entry if it references a `docker-credential-desktop` binary
you don't have installed (only relevant if Docker Desktop was previously configured on
the same machine).
