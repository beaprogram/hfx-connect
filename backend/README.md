# HFX Connect — Backend

The Spring Boot application for the HFX Connect REST API. See the
[repository root README](../README.md) for the product overview, and
[docs/architecture/system-overview.md](../docs/architecture/system-overview.md) for
the intended API and module design.

**Status:** application skeleton only (Milestone 2A). No database, no persistence, and
no REST endpoints exist yet — those are introduced starting in Milestone 2B (database
environment) and Milestone 3 (category/resource domain).

## Stack

Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`), Maven (via the Maven
Wrapper — no local Maven installation required).

## Local Development

Requires a JDK 21 on `JAVA_HOME` (or discoverable on `PATH`).

```bash
./mvnw spring-boot:run
```

The application starts on [http://localhost:8080](http://localhost:8080). There are no
routes mapped yet, so requests currently return `404` — this is expected until
Milestone 3 adds real endpoints.

## Commands

| Command | Purpose |
|---|---|
| `./mvnw spring-boot:run` | Run the application locally |
| `./mvnw test` | Run unit/context tests |
| `./mvnw verify` | Run the full build lifecycle including tests |

## Project Structure

```
backend/
  src/main/java/com/hfxconnect/
    HfxConnectApplication.java     Application entry point
  src/main/resources/
    application.properties               Base configuration
  src/test/java/com/hfxconnect/
    HfxConnectApplicationTests.java  Application-context smoke test
```

Domain packages (`auth/`, `category/`, `resource/`, `search/`, `moderation/`, `event/`,
etc., as described in
[system-overview.md](../docs/architecture/system-overview.md#backend-module-structure))
are added starting in Milestone 3, once there is real domain logic to put in them —
this avoids empty, speculative package scaffolding.
