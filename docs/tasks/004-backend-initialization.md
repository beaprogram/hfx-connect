# Task 004: Backend Initialization

## Objective

Create a runnable, testable Spring Boot application shell on Java 21 with the Maven
Wrapper, ready for Milestone 2B (database) and Milestone 3 (domain code) to build on.

## Context

Part of Milestone 2A (Application Initialization). This is the first task of the
milestone because the frontend shell (Task 005) does not depend on it, but the
project's overall "backend-first, database-later" sequencing (see
[docs/development-workflow.md](../development-workflow.md)) makes it the natural
starting point.

## Scope

- Generate the project via Spring Initializr: Maven, Java, `com.hfxconnect`/`backend`,
  Java 21, `spring-boot-starter-webmvc`.
- Rename the generated `BackendApplication` class (and its test) to
  `HfxConnectApplication` for a name specific to the product rather than the
  artifact.
- Set `spring.application.name=hfx-connect-backend`.
- Remove generated boilerplate not useful to keep (`HELP.md`).
- Clean up placeholder empty POM elements (`description`, empty `url`/`licenses`/
  `developers`/`scm` blocks) left by the generator.
- Write `backend/README.md` with real, verified setup instructions.
- Install a JDK 21 toolchain locally (via Homebrew) since none was present, and
  confirm the Maven Wrapper resolves and builds against it.

## Out of Scope

Any database configuration, Docker, domain entities, or REST endpoints.

## Acceptance Criteria

- `./mvnw -v` reports Java 21.
- `./mvnw test` passes the application-context test.
- `./mvnw verify` completes the full build lifecycle successfully.
- `./mvnw spring-boot:run` boots a Tomcat server on port 8080 (verified live, not just
  build-checked).
- No package exists for a domain that has no code in it yet.

## Technical Approach

Generated via `curl` against `start.spring.io`'s `/starter.zip` endpoint rather than a
local Spring Initializr plugin, since no local IDE tooling was assumed. The generated
parent POM version initially failed to resolve
(`spring-boot-starter-parent:4.1.0.RELEASE` does not exist on Maven Central — the
Initializr metadata's `.RELEASE` suffix is a display artifact, not the real published
coordinate, which is `4.1.0`); corrected by checking Maven Central's
`maven-metadata.xml` directly and using the real version string.

No JDK 21 was installed on the development machine (only 11, 17, and 25 were present).
Installed via `brew install openjdk@21` and invoked the Maven Wrapper with
`JAVA_HOME` pointed at the Homebrew-installed JDK 21, since the formula is keg-only
and not symlinked into the default `java` on `PATH`.

## Testing Requirements

`./mvnw test` (unit/context test) and `./mvnw verify` (full lifecycle). Both were run
and passed. The running application was also manually verified with `curl` against
`http://localhost:8080/` (returns `404`, correctly indicating the server is up with no
routes mapped yet), then stopped.

## Result

Completed. The backend boots, passes its context test, and passes `./mvnw verify`.
JDK 21 is installed and confirmed as the toolchain Maven actually uses.

## Related Commit

`chore: initialize Spring Boot backend on Java 21`
