# HFX Connect — Backend

The Spring Boot application for the HFX Connect REST API. See the
[repository root README](../README.md) for the product overview, and
[docs/architecture/system-overview.md](../docs/architecture/system-overview.md) for
the intended API and module design.

**Status:** category management (Milestone 3A), a public resource API (Milestone 3C,
built on the persistence/business layer Milestone 3B added, now with keyword
search (6A), structured operating hours/open-now/cost/verification
filtering (6B), and PostGIS resource locations/nearby search (7A)), CORS
support for the Milestone 4 public frontend,
account registration (Milestone 5A), login/refresh/logout (Milestone 5B), and
request authentication plus role-based authorization (Milestone 5C) — see
[Category API](#category-api-v1categories),
[Resource API](#resource-api-v1resources), [CORS](#cors),
[Auth API](#auth-api-v1auth), and [Users API](#users-api-v1users) below.
PostgreSQL/PostGIS runs locally via Docker Compose, Flyway manages schema
migrations, and `/actuator/health` reports live database health. Every
authenticated request is verified by `com.hfxconnect.security
.JwtAuthenticationFilter` and authorized by `SecurityConfig`'s route matrix —
see [docs/architecture/security-architecture.md](../docs/architecture/security-architecture.md).

## Stack

Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-validation`, `spring-boot-starter-actuator`,
`spring-boot-starter-security` — added Milestone 5C, see
[ADR-009](../docs/decisions/ADR-009-request-authentication-and-role-authorization.md)),
PostgreSQL JDBC driver, Flyway, springdoc-openapi, `spring-security-crypto`
(password hashing — see
[ADR-007](../docs/decisions/ADR-007-user-identity-and-password-hashing.md)),
JJWT 0.12.6 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson` — access-token signing/
validation, see
[ADR-008](../docs/decisions/ADR-008-authentication-session-architecture.md)), Maven
(via the Maven Wrapper — no local Maven installation required).

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
which match `docker-compose.yml`'s defaults exactly. **`JWT_SECRET` must be set**
(see [Required: `JWT_SECRET`](#required-jwt_secret) below) — every other
environment variable is an optional override with a working default. Flyway runs
automatically on startup; see `src/main/resources/db/migration/`.

Most paths still return `404` — only `/actuator/health`, `/api/v1/categories`,
`/api/v1/resources`, `/api/v1/auth/{register,login,refresh,logout}`, and
`/api/v1/users/me` (and each of their sub-routes) are mapped so far. An
unmapped path returns `404 NOT_FOUND` from `GlobalExceptionHandler` (fixed in
Milestone 3B) **only for an authenticated request** — an unauthenticated
request to any unmapped path is rejected `401` by `SecurityConfig`'s
`anyRequest().authenticated()` default before Spring MVC's own routing ever
runs (Milestone 5C) — see
[GlobalExceptionHandlerIntegrationTest](src/test/java/com/hfxconnect/common/error/GlobalExceptionHandlerIntegrationTest.java).

### Required: `JWT_SECRET`

Copy `.env.example` to `.env`, then export it into your shell before running the
backend (Spring Boot does not load `.env` files automatically):

```bash
set -a; source .env; set +a
./mvnw spring-boot:run
```

`application.properties` has **no fallback default** for `app.jwt.secret` — unlike
every other setting below, the backend refuses to start at all unless
`JWT_SECRET` is set to something at least 32 bytes long, in `.env` or any other
way your shell provides it. This is deliberate (see
[ADR-008](../docs/decisions/ADR-008-authentication-session-architecture.md)): a
JWT signing secret with a working default baked into a public repository would
let anyone who reads the source forge valid access tokens if a real deployment
ever forgot to override it — a materially worse failure mode than a default
database password, which is why this one variable doesn't get the same
convenience default as the others. `.env.example`'s shipped value is an obviously
insecure placeholder for local development only; generate a real one for any
non-local environment with `openssl rand -base64 48`.

### Overriding Other Defaults

The remaining variables are all optional overrides — `application.properties`
falls back to a value matching `docker-compose.yml`'s local defaults for each of
them if unset: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`,
`CORS_ALLOWED_ORIGINS`, `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`,
`JWT_REFRESH_TOKEN_TTL`, `AUTH_COOKIE_SECURE`, `AUTH_COOKIE_SAME_SITE` — see
`.env.example` for defaults and
[ADR-008](../docs/decisions/ADR-008-authentication-session-architecture.md) for
why the cookie variables' correct values differ between local development and
production.

## Health Endpoint

`GET /actuator/health` reports `{"status":"UP", ...}` when the application and its
database connection are healthy, and a non-2xx status with `"status":"DOWN"` when the
database is unreachable. It is explicitly public in `SecurityConfig` (Milestone 5C)
regardless of authentication. Only the top-level status is currently ever shown
(`management.endpoint.health.show-details=when-authorized`, and no
`management.endpoint.health.roles` is configured, so component-level detail is
not granted to any caller yet — a reasonable future improvement, not built in
this milestone). Only the `health` endpoint is exposed; no other Actuator
endpoints are enabled.

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

`WebCorsConfig` now exposes a `CorsConfigurationSource` bean (Milestone 5C)
rather than a `WebMvcConfigurer` — `SecurityConfig`'s `HttpSecurity.cors()`
delegates to it directly, so this is the one CORS policy definition, enforced
for every request. `allowedHeaders` includes `Authorization` (required for
the frontend's Bearer access token to be sent cross-origin at all).

## Category API (`/api/v1/categories`)

Full reference: `docs/api/README.md` and `docs/milestones/milestone-03a-category-domain.md`.
Interactive docs from a running backend: `http://localhost:8080/swagger-ui.html`.

**`POST /api/v1/categories` requires a Bearer access token for an `ADMIN`
account** (Milestone 5C — see [Auth API](#auth-api-v1auth) for how to get one).

```bash
# Create (requires an ADMIN access token — see the Auth API section)
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
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

**`POST /api/v1/resources` requires a Bearer access token for an `ADMIN` or
`MODERATOR` account** (Milestone 5C; `ORGANIZATION` accounts cannot create
resources yet — see [ADR-009](../docs/decisions/ADR-009-request-authentication-and-role-authorization.md)).
**There is no update or delete endpoint** —
`ResourceService.update`/`deactivate` exist and are fully tested (Milestone 3B) but
are not exposed over HTTP in this milestone. Public reads only ever see active
resources: a deactivated resource returns `404` from every read endpoint, the same as
a nonexistent one.

```bash
# Create (categoryId must reference an existing, active category;
# requires an ADMIN or MODERATOR access token)
curl -X POST http://localhost:8080/api/v1/resources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_OR_MODERATOR_ACCESS_TOKEN" \
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

# Keyword search (Milestone 6A) — combines with categoryId/sort/pagination
curl "http://localhost:8080/api/v1/resources?q=library&categoryId=1"

# Cost/verification/open-now filters (Milestone 6B) — all combine freely
curl "http://localhost:8080/api/v1/resources?costType=FREE&verificationStatus=VERIFIED&openNow=true"

# Replace a resource's weekly schedule (Milestone 6B) — ADMIN or MODERATOR only
curl -X PUT "http://localhost:8080/api/v1/resources/{id}/operating-hours" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"hours":[{"dayOfWeek":"MONDAY","closed":false,"opensAt":"09:00","closesAt":"17:00"}]}'

# Nearby search (Milestone 7A) — combines with q/categoryId/costType/verificationStatus/openNow
curl "http://localhost:8080/api/v1/resources/nearby?latitude=44.6488&longitude=-63.5752&radiusKm=10"

# Replace a resource's coordinate (Milestone 7A) — ADMIN or MODERATOR only
curl -X PUT "http://localhost:8080/api/v1/resources/{id}/location" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"latitude":44.6488,"longitude":-63.5752}'
```

**Keyword search (`q`, Milestone 6A):** case-insensitive substring match
across `name`/`description`/`addressLine1`/`city`; at most 100 characters
after normalization (trim, whitespace collapse); blank is treated as no
filter; `%`/`_` are escaped and matched literally, never as `LIKE`
wildcards; no relevance ranking. Full design:
[ADR-010](../docs/decisions/ADR-010-keyword-search-design.md).

**Operating hours and filters (Milestone 6B):** every resource read
includes an `hours` object (`timezone`, `weeklyHours`, `hoursStatus`,
`openNow`) evaluated in `America/Halifax`; `costType`/`verificationStatus`/
`openNow` are optional public list filters, each independently combinable
with `q`/`categoryId`/`sort`/pagination. `PUT
/api/v1/resources/{id}/operating-hours` fully replaces a resource's weekly
schedule and requires an `ADMIN`/`MODERATOR` Bearer token. Full design:
[ADR-011](../docs/decisions/ADR-011-operating-hours-and-open-now.md).

**Nearby search and locations (Milestone 7A):** `GET
/api/v1/resources/nearby` finds active resources within `radiusKm`
(default 5, maximum 50) of a required `latitude`/`longitude`, ordered
nearest first, combining with every existing filter above.
`distanceMeters` in each result is straight-line geographic distance —
never route distance or travel time. `PUT /api/v1/resources/{id}/location`
replaces a resource's coordinate and requires an `ADMIN`/`MODERATOR`
Bearer token. `location` is a PostGIS `geography(Point, 4326)` column,
deliberately never mapped as a Hibernate entity field — every geospatial
read/write goes through native SQL. Full design:
[ADR-012](../docs/decisions/ADR-012-postgis-nearby-search-design.md).

A resource is created under an existing, active category (`categories.id`, a
`BIGINT` — not the `UUID` a resource's own `id` is; see
`docs/database/README.md`'s note on `resources.category_id`'s type). Its slug is
generated once from its name and never changes, even across updates that rename it —
see [ADR-005](../docs/decisions/ADR-005-category-identifiers-and-normalization.md).
`com.hfxconnect.resource.ResourceValidation` normalizes and validates whitespace,
Canadian province/postal code, practical phone/email checks, and allowlists
`http`/`https` website schemes.

## Auth API (`/api/v1/auth`)

Full reference: `docs/api/README.md`,
`docs/milestones/milestone-05a-user-registration.md`, and
`docs/milestones/milestone-05b-authentication-sessions.md`. Full security design:
`docs/architecture/security-architecture.md`.

**None of the four endpoints below require an access token** — they are how a
caller obtains one in the first place (Milestone 5C added request
authentication for every *other* route — see [Users API](#users-api-v1users)
and `docs/architecture/security-architecture.md`).

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "student@example.org", "password": "a-genuinely-unique-passphrase"}'

# Log in — sets the refresh cookie via -c (cookie jar file); the JSON response's
# accessToken is what you pass as `Authorization: Bearer` to protected routes
curl -i -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "student@example.org", "password": "a-genuinely-unique-passphrase"}'

# Refresh — reads the cookie via -b; rotates it (cookies.txt is updated by -c again)
curl -i -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/v1/auth/refresh

# Log out — revokes the session and clears the cookie; always 204
curl -i -b cookies.txt -X POST http://localhost:8080/api/v1/auth/logout
```

Email is normalized (trimmed, lowercased) before the uniqueness check — a
differently-cased duplicate returns `409 USER_CONFLICT`, same as an exact one.
Passwords are hashed with BCrypt (strength 12 —
[ADR-007](../docs/decisions/ADR-007-user-identity-and-password-hashing.md)) before
storage; the response never includes a password or its hash. Password policy: at
least 8 characters, **at most 72 bytes when UTF-8 encoded** (not 72 characters —
BCrypt's own limit is a byte limit; a password made of multibyte-Unicode characters
can exceed it well under 72 characters, and is rejected with the normal `400
VALIDATION_ERROR` shape rather than ever reaching the hasher — see ADR-007's
2026-07-24 correction). Every account is created as `USER`/`ACTIVE`/unverified — a
`role` or other privilege field in the request body has no effect, by design (see
`docs/architecture/backend-architecture.md`'s "Preventing Privilege Escalation
Structurally" section).

**Login** returns a short-lived (15 min default) signed JWT access token in the
JSON body and sets a rotating, `HttpOnly` refresh-token cookie
(`hfx_refresh_token`, 30-day default, scoped to `/api/v1/auth`) — never the
other way around, and the refresh token is never present in JSON. Unknown email
and wrong password return the exact same `401 AUTHENTICATION_FAILED` response
(with a real, timing-mitigated password comparison either way — see ADR-008); a
correct-credentials login against a non-`ACTIVE` account returns `403
ACCOUNT_UNAVAILABLE` instead.

**Refresh** reads the cookie only — never the body, query string, or path — and
rotates it on every success; the previous cookie value becomes permanently
unusable. Presenting an already-used (rotated or revoked) token revokes every
session descended from the same original login, not just that one.

**Logout** revokes the matching session and clears the cookie; it is always safe
and idempotent (`204`, whether or not a valid session was presented) and does not
require an access token.

**No rate limiting exists** — login accepts unlimited attempts; see
`docs/architecture/security-architecture.md`'s honest limitations section.

## Users API (`/api/v1/users`)

Full reference: `docs/api/README.md` and
`docs/milestones/milestone-05c-role-authorization.md`. Added in Milestone 5C.

```bash
# Requires a Bearer access token (from login/refresh above)
curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer $ACCESS_TOKEN"
```

Returns the caller's own account only — the same safe shape registration
returns (`{id, email, role, status, emailVerified, createdAt}`). No password
hash, refresh sessions, or other internal metadata; no user-ID parameter.

## Request Authentication and Authorization (Milestone 5C)

Every route not listed as public in
`com.hfxconnect.security.SecurityConfig` requires a valid `Authorization:
Bearer <accessToken>` header; the account's *current* database role and
status are used for the authorization decision, never the token's own
`role` claim (a stale-claim scenario — an administrator changing a role
after a token was already issued — is closed by re-loading the account on
every request). Full design:
[ADR-009](../docs/decisions/ADR-009-request-authentication-and-role-authorization.md).
Full posture: `docs/architecture/security-architecture.md`.

```bash
# Missing/invalid token, or a non-ACTIVE account → 401 AUTHENTICATION_REQUIRED
curl -i http://localhost:8080/api/v1/users/me

# Authenticated, but the wrong role → 403 ACCESS_DENIED
curl -i -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer $USER_ROLE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" -d '{"name": "Nope"}'
```

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
                                                             (UnauthorizedException/ForbiddenException added
                                                             in Milestone 5B; InvalidSearchQueryException
                                                             added in Milestone 6A)
      text/
        SlugGenerator.java                          Shared deterministic slug algorithm (used by
                                                            both category/ and resource/)
        EmailNormalizer.java                       Shared email-normalization rule (Milestone 5B —
                                                            used by both user/ and auth/)
    category/                                           Category domain (entity, repository,
                                                             service, controller, DTOs)
    resource/                                          Resource domain (entity, repository, service,
                                                             business-layer models, validation, controller,
                                                             HTTP DTOs) — controller added in Milestone 3C;
                                                             ResourceSearchQuery (keyword-search
                                                             normalization/escaping) added in Milestone 6A —
                                                             see ADR-010
    user/                                                 User domain (entity, repository, service,
                                                             validation, DTOs) — registration; AuthController
                                                             (login/refresh/logout added in Milestone 5B),
                                                             CurrentUserController (/users/me, Milestone 5C)
                                                             also live here — see backend-architecture.md's
                                                             "Cross-Domain Dependencies" section
    auth/                                                 Authentication-session domain (Milestone 5B):
                                                             RefreshSession(+repository), AccessTokenService,
                                                             RefreshTokenGenerator, RefreshSessionService,
                                                             AuthenticationService, RefreshService, LogoutService,
                                                             LoginRequest/Response, RefreshCookieConfig,
                                                             auth-specific exceptions — see ADR-008
    security/                                            Request authentication/authorization (Milestone
                                                             5C): JwtAuthenticationFilter, CurrentUserPrincipal,
                                                             SecurityConfig, ApiAuthenticationEntryPoint,
                                                             ApiAccessDeniedHandler — see ADR-009 and
                                                             backend-architecture.md's "Request Authentication
                                                             and Authorization" section
  src/main/resources/
    application.properties                        Base configuration (env-based DB connection,
                                                             JPA, Actuator, JWT/cookie config)
    db/migration/
      V1__enable_postgis_extension.sql         First Flyway migration
      V2__create_categories_table.sql         Second Flyway migration
      V3__create_resources_table.sql          Third Flyway migration
      V4__create_users_table.sql               Fourth Flyway migration
      V5__create_refresh_sessions_table.sql  Fifth Flyway migration
                                                             (Milestone 5C added no new migration — Role/
                                                             AccountStatus already existed on V4's users table)
      V6__create_resource_operating_hours.sql Sixth Flyway migration (Milestone 6B —
                                                             see ADR-011)
      V7__add_resource_location.sql            Seventh Flyway migration (Milestone 7A —
                                                             see ADR-012)
  src/test/java/com/hfxconnect/
    AbstractPostgresIntegrationTest.java   Shared Testcontainers setup (public — extended
                                                             from sub-packages like category/, resource/)
    HfxConnectApplicationTests.java          Application-context smoke test
    FlywayMigrationIntegrationTest.java     Migration + idempotency tests
    HealthEndpointIntegrationTest.java      Health endpoint tests
    common/
      text/SlugGeneratorTest.java                Pure unit tests for the shared slug algorithm
      error/GlobalExceptionHandlerIntegrationTest.java  Unmapped-route 404 (authenticated)/
                                                             401 (unauthenticated) regression test
      config/CorsConfigurationIntegrationTest.java  CORS allowlist + credentials + Authorization
                                                             header regression test
    category/                                           Category domain tests (unit, repository,
                                                             API integration — see
                                                             docs/milestones/milestone-03a-category-domain.md)
    resource/                                          Resource domain tests (validation unit tests,
                                                             repository + service + API integration tests,
                                                             ResourceSearchQueryTest for keyword-search
                                                             normalization/escaping — see
                                                             docs/milestones/milestone-03b-resource-domain.md,
                                                             milestone-03c-public-resource-api.md, and
                                                             milestone-06a-keyword-search.md)
    user/                                                 User domain tests (repository, service, API
                                                             integration, TestUserFactory for role-gated
                                                             test accounts — see
                                                             docs/milestones/milestone-05a-user-registration.md)
    auth/                                                 Auth-session domain tests (token unit tests,
                                                             repository, service, full login/refresh/logout
                                                             API integration — see
                                                             docs/milestones/milestone-05b-authentication-sessions.md)
    security/                                            Authorization matrix tests (every role × every
                                                             protected route, token-validation edge cases,
                                                             stale-role-claim and disabled-account-after-
                                                             issuance proofs — see
                                                             docs/milestones/milestone-05c-role-authorization.md)
```

Domain packages not yet needed (`search/`, `moderation/`, `event/`, etc., as
described in
[system-overview.md](../docs/architecture/system-overview.md#backend-module-structure))
are added when there's real domain logic to put in them — this avoids empty,
speculative package scaffolding. `category/`, `resource/`, `user/`, and `auth/`
follow the same pattern (entity/repository/service/controller/DTOs, no setters
without a real mutation need, shared `common/error` exceptions), documented in
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
