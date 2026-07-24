# HFX Connect — Backend

The Spring Boot application for the HFX Connect REST API. See the
[repository root README](../README.md) for the product overview, and
[docs/architecture/system-overview.md](../docs/architecture/system-overview.md) for
the intended API and module design.

**Status:** category management (Milestone 3A), a public resource API (Milestone 3C,
built on the persistence/business layer Milestone 3B added), CORS support for the
Milestone 4 public frontend, and account registration (Milestone 5A) — see
[Category API](#category-api-v1categories), [Resource API](#resource-api-v1resources),
[CORS](#cors), and [Auth API](#auth-api-v1auth) below. PostgreSQL/PostGIS runs locally
via Docker Compose, Flyway manages schema migrations, and `/actuator/health` reports
live database health. There is still no login, tokens, or role-based authorization —
registering an account does not log the caller in, and `POST` on the Category/Resource
APIs remains unprotected.

## Stack

Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-validation`, `spring-boot-starter-actuator`), PostgreSQL JDBC
driver, Flyway, springdoc-openapi, `spring-security-crypto` (password hashing only —
see [ADR-007](../docs/decisions/ADR-007-user-identity-and-password-hashing.md); not
the full `spring-boot-starter-security`), Maven (via the Maven Wrapper — no local
Maven installation required).

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

Most paths still return `404` — only `/actuator/health`, `/api/v1/categories`,
`/api/v1/resources`, and `/api/v1/auth/register` (and each of their sub-routes) are
mapped so far. (An unmapped
path correctly returns `404 NOT_FOUND` — this was a real bug in
`GlobalExceptionHandler` until Milestone 3B fixed it; see the development log for
2026-07-19.)

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
withheld until authenticated requests are possible (Milestone 5B/5C). Only the
`health` endpoint is exposed; no other Actuator endpoints are enabled.

## CORS

The Milestone 4 frontend calls this API directly from the browser, so
`/api/v1/**` allows cross-origin requests from a configured allowlist — see
`com.hfxconnect.common.config.WebCorsConfig` and
[ADR-006](../docs/decisions/ADR-006-frontend-backend-connectivity.md). Allowed
origins come from `CORS_ALLOWED_ORIGINS` (comma-separated), defaulting to
`http://localhost:3000` — no configuration is needed for standard local
development with the frontend on its own default port. There is no `"*"`
wildcard; a production deployment must set `CORS_ALLOWED_ORIGINS` to the real
deployed frontend origin(s).

## Category API (`/api/v1/categories`)

Full reference: `docs/api/README.md` and `docs/milestones/milestone-03a-category-domain.md`.
Interactive docs from a running backend: `http://localhost:8080/swagger-ui.html`.

**`POST /api/v1/categories` is not protected by authentication yet** — anyone who can
reach the API can create a category. This is a deliberate, documented limitation
(Milestone 5 adds authentication/authorization), not an oversight.

```bash
# Create
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Content-Type: application/json" \
  -d '{"name": "Food Assistance", "description": "Food banks and meals."}'

# Get by ID or slug
curl http://localhost:8080/api/v1/categories/1
curl http://localhost:8080/api/v1/categories/slug/food-assistance

# Paginated list (page/size bounded; size max 100)
curl "http://localhost:8080/api/v1/categories?page=0&size=20"
```

The slug is always derived from the name (see
[ADR-005](../docs/decisions/ADR-005-category-identifiers-and-normalization.md)) — it
cannot be supplied directly. Duplicate names/slugs (case- and whitespace-insensitive)
return `409`; validation failures return `400` with the shape documented in
`docs/api/README.md`.

## Resource API (`/api/v1/resources`)

Full reference: `docs/api/README.md` and
`docs/milestones/milestone-03c-public-resource-api.md`.

**`POST /api/v1/resources` is not protected by authentication yet**, identical to the
Category API's own limitation. **There is no update or delete endpoint** —
`ResourceService.update`/`deactivate` exist and are fully tested (Milestone 3B) but
are not exposed over HTTP in this milestone. Public reads only ever see active
resources: a deactivated resource returns `404` from every read endpoint, the same as
a nonexistent one.

```bash
# Create (categoryId must reference an existing, active category)
curl -X POST http://localhost:8080/api/v1/resources \
  -H "Content-Type: application/json" \
  -d '{
    "categoryId": 1,
    "name": "Halifax Central Library",
    "description": "A full-service public library with free WiFi and study rooms.",
    "addressLine1": "5381 Spring Garden Rd",
    "city": "Halifax",
    "province": "NS",
    "postalCode": "B3J 2K9",
    "costType": "FREE"
  }'

# Get by ID or slug (404 if deactivated or unknown)
curl http://localhost:8080/api/v1/resources/{id}
curl http://localhost:8080/api/v1/resources/slug/halifax-central-library

# Paginated, active-only list; optional categoryId filter; sort=name (default) or createdAt
curl "http://localhost:8080/api/v1/resources?categoryId=1&sort=createdAt&size=20"
```

A resource is created under an existing, active category (`categories.id`, a
`BIGINT` — not the `UUID` a resource's own `id` is; see
`docs/database/README.md`'s note on `resources.category_id`'s type). Its slug is
generated once from its name and never changes, even across updates that rename it —
see [ADR-005](../docs/decisions/ADR-005-category-identifiers-and-normalization.md).
`com.hfxconnect.resource.ResourceValidation` normalizes and validates whitespace,
Canadian province/postal code, practical phone/email checks, and allowlists
`http`/`https` website schemes.

## Auth API (`/api/v1/auth`)

Full reference: `docs/api/README.md` and
`docs/milestones/milestone-05a-user-registration.md`.

**Registering an account does not log the caller in** — there is no login endpoint,
access token, refresh token, or session yet (Milestone 5B). **No route in the API is
protected by authentication yet** (Milestone 5C).

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "student@example.org", "password": "a-genuinely-unique-passphrase"}'
```

Email is normalized (trimmed, lowercased) before the uniqueness check — a
differently-cased duplicate returns `409 USER_CONFLICT`, same as an exact one.
Passwords are hashed with BCrypt (strength 12 —
[ADR-007](../docs/decisions/ADR-007-user-identity-and-password-hashing.md)) before
storage; the response never includes a password or its hash. Every account is
created as `USER`/`ACTIVE`/unverified — a `role` or other privilege field in the
request body has no effect, by design (see
`docs/architecture/backend-architecture.md`'s "Preventing Privilege Escalation
Structurally" section).

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
    common/
      config/
        FlywayMigrationConfig.java             Runs Flyway on startup; also orders it
                                                            before JPA (see ADR-004 and this file's
                                                            Javadoc)
        OpenApiConfig.java                          OpenAPI document metadata
        WebCorsConfig.java                          CORS allowlist for the frontend origin
                                                             (Milestone 4) — see ADR-006
        PasswordEncoderConfig.java             BCryptPasswordEncoder bean (Milestone 5A) —
                                                             see ADR-007
      error/                                             Shared error-handling pattern — see
                                                             docs/architecture/backend-architecture.md
      text/
        SlugGenerator.java                          Shared deterministic slug algorithm (used by
                                                            both category/ and resource/)
    category/                                           Category domain (entity, repository,
                                                             service, controller, DTOs)
    resource/                                          Resource domain (entity, repository, service,
                                                             business-layer models, validation, controller,
                                                             HTTP DTOs) — controller added in Milestone 3C
    user/                                                 User domain (entity, repository, service,
                                                             validation, controller, DTOs) — registration only
                                                             (Milestone 5A); login/roles are 5B/5C
  src/main/resources/
    application.properties                        Base configuration (env-based DB connection,
                                                             JPA, Actuator)
    db/migration/
      V1__enable_postgis_extension.sql         First Flyway migration
      V2__create_categories_table.sql         Second Flyway migration
      V3__create_resources_table.sql          Third Flyway migration
      V4__create_users_table.sql               Fourth Flyway migration
  src/test/java/com/hfxconnect/
    AbstractPostgresIntegrationTest.java   Shared Testcontainers setup (public — extended
                                                             from sub-packages like category/, resource/)
    HfxConnectApplicationTests.java          Application-context smoke test
    FlywayMigrationIntegrationTest.java     Migration + idempotency tests
    HealthEndpointIntegrationTest.java      Health endpoint tests
    common/
      text/SlugGeneratorTest.java                Pure unit tests for the shared slug algorithm
      error/GlobalExceptionHandlerIntegrationTest.java  Unmapped-route 404 regression test
    category/                                           Category domain tests (unit, repository,
                                                             API integration — see
                                                             docs/milestones/milestone-03a-category-domain.md)
    resource/                                          Resource domain tests (validation unit tests,
                                                             repository + service + API integration tests —
                                                             see docs/milestones/milestone-03b-resource-domain.md
                                                             and milestone-03c-public-resource-api.md)
    user/                                                 User domain tests (repository, service, API
                                                             integration — see
                                                             docs/milestones/milestone-05a-user-registration.md)
```

Domain packages not yet needed (`search/`, `moderation/`, `event/`, etc., as
described in
[system-overview.md](../docs/architecture/system-overview.md#backend-module-structure))
are added when there's real domain logic to put in them — this avoids empty,
speculative package scaffolding. `category/`, `resource/`, and `user/` follow the same
pattern (entity/repository/service/controller/DTOs, no setters without a real mutation
need, shared `common/error` exceptions), documented in
`docs/architecture/backend-architecture.md` for later domains to follow.

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

**After using the `POSTGRES_PORT` override, a later plain `docker compose up -d`
recreates the container back onto port 5432.** Docker Compose re-reads
`docker-compose.yml`'s environment substitution on every `up`, including the default
value, if you don't pass `POSTGRES_PORT` again — it will recreate the container (not
the volume; your data is preserved) with whatever port resolves this time. If you're
using the override on a given machine, pass it every time:
`POSTGRES_PORT=55432 docker compose up -d`.

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

**`Container startup failed for image testcontainers/ryuk:0.14.0`.** Testcontainers'
resource-reaper sidecar (Ryuk) can fail to start under Colima even once `DOCKER_HOST`
above is set correctly — encountered during Milestone 5A. Disabling it is safe for
local development (the shared singleton container this project uses is already
long-lived and cleaned up by the JVM exiting, not by Ryuk specifically):

```bash
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw test
```

Also add `"cliPluginsExtraDirs": ["/opt/homebrew/lib/docker/cli-plugins"]` to
`~/.docker/config.json` if `docker compose` is not found, and remove any
`"credsStore": "desktop"` entry if it references a `docker-credential-desktop` binary
you don't have installed (only relevant if Docker Desktop was previously configured on
the same machine).
