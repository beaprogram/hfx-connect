# Milestone 2A: Application Initialization

## Objective

Stand up runnable, testable application shells for both halves of HFX Connect — the
Spring Boot backend and the Next.js frontend — with the toolchain, strictness
settings, and quality scripts that every later milestone will build on. No database,
no domain entities, and no real pages beyond a placeholder homepage.

## Product Value

Nothing user-facing yet. The value is entirely engineering foundation: a backend that
boots and is verifiably testable, and a frontend shell that is accessible, responsive,
and already wired for linting, type checking, testing, and production builds — so
Milestone 2B and Milestone 3 can add real functionality without first fixing basic
tooling gaps.

## Technical Scope

**Backend** (`backend/`):

- Spring Boot 4.1.0 project generated via Spring Initializr, Java 21, Maven Wrapper,
  `spring-boot-starter-webmvc` (Spring Boot 4's renamed equivalent of the `web`
  starter used in Spring Boot 3).
- Root application class renamed from the generated `BackendApplication` to
  `HfxConnectApplication` for clarity, in package `com.hfxconnect`.
- `spring.application.name=hfx-connect-backend` set for future structured-logging
  correlation.
- A single `@SpringBootTest` application-context test confirming the app boots.
- No domain packages (`auth/`, `resource/`, etc.) were pre-created — they will be
  added starting in Milestone 3 alongside the code that actually belongs in them.

**Frontend** (`frontend/`):

- Next.js 16.2.10 (App Router, Turbopack) generated via `create-next-app`, TypeScript,
  Tailwind CSS v4, ESLint.
- Strict TypeScript, plus `noUncheckedIndexedAccess` for additional indexing safety.
- Jest + React Testing Library configured via `next/jest`, with four passing tests.
- `npm run typecheck` script added (not present by default).
- A minimal, accessible, responsive HFX Connect shell: skip-to-content link, semantic
  `<header>`/`<main>`/`<footer>` landmarks, visible focus-visible states, a single
  `<h1>`, and honest placeholder copy (no unbuilt search/listing functionality is
  implied).
- A moderate-severity transitive `postcss` advisory (bundled inside Next.js's own
  dependency tree, not our direct dependency) resolved via an `overrides` entry rather
  than downgrading Next.js — `npm audit` now reports zero vulnerabilities.

**Repository:**

- `backend/README.md` and `frontend/README.md` — project-specific setup
  instructions, replacing generated boilerplate (`HELP.md` removed).
- Root `README.md` updated: current status, real local-setup instructions for both
  applications, repository-structure description, known limitations.

## Out of Scope

Docker Compose, PostgreSQL, PostGIS, Flyway, any database configuration, categories,
resources, authentication, maps, and deployment — all explicitly deferred to
Milestone 2B and later.

## Acceptance Criteria

**Backend**

- [x] Java 21 is configured (`pom.xml` `java.version`, verified against the JDK
      actually used to build).
- [x] Spring Boot starts successfully (`./mvnw spring-boot:run` — verified serving on
      port 8080).
- [x] Maven wrapper works (`./mvnw -v` resolves and runs without a local Maven
      install).
- [x] The application-context test passes (`./mvnw test`).
- [x] Package organization is clean and not overengineered (a single root package;
      no empty speculative domain packages).
- [x] No database configuration is introduced.

**Frontend**

- [x] Next.js starts successfully (`npm run dev` — verified serving on port 3000 with
      expected content).
- [x] Strict TypeScript is enabled.
- [x] Tailwind CSS is configured.
- [x] The initial HFX Connect shell is responsive, accessible, restrained, and
      professional.
- [x] Linting passes (`npm run lint`).
- [x] Type checking passes (`npm run typecheck`).
- [x] Tests pass (`npm test` — 3 suites, 4 tests).
- [x] Production build succeeds (`npm run build`).

**Repository**

- [x] No secrets are committed (none were introduced — no `.env.example` files were
      added this milestone because neither application consumes any environment
      variables yet; see Known Limitations).
- [x] No generated dependency or build directories are tracked (`node_modules/`,
      `.next/`, `target/` all verified absent from `git add -n` output).
- [x] Setup instructions are accurate (both README files were written against
      commands actually run during this milestone).
- [x] The branch contains only Milestone 2A work.
- [x] Commit history is coherent and uses conventional commits.

## Planned Tasks

1. Backend initialization — Spring Boot, Java 21, Maven Wrapper, application-context
   test ([docs/tasks/004-backend-initialization.md](../tasks/004-backend-initialization.md)).
2. Frontend initialization — Next.js, strict TypeScript, Tailwind, testing, and the
   accessible shell
   ([docs/tasks/005-frontend-initialization.md](../tasks/005-frontend-initialization.md)).

## Testing Requirements

Backend: `./mvnw test` (application-context test) and `./mvnw verify` (full build
lifecycle). Frontend: `npm run lint`, `npm run typecheck`, `npm test`, `npm run
build`. All four frontend checks and both backend commands were run and passed during
this milestone; results are recorded in the development log.

## Documentation Requirements

`backend/README.md`, `frontend/README.md`, this milestone document, two task
documents, a development-log entry, and updates to the root README's status, local
setup, and known-limitations sections.

## Security Considerations

- No secrets or environment files were introduced.
- The transitive `postcss` XSS advisory (GHSA-qx2v-qp2m-jg93), present in a package
  bundled inside `next@16.2.10`'s own dependency tree, was resolved via a `postcss`
  `overrides` entry pinned to a patched version rather than downgrading Next.js seven
  major versions (npm's own `--force` suggestion, which was rejected as unsafe).
  `npm audit` confirms zero vulnerabilities after the fix.
- `npm approve-scripts` was used to allow install scripts for `sharp`, `fsevents`, and
  `unrs-resolver` — all standard transitive dependencies of Next.js/ESLint tooling
  (native image processing, macOS file watching, and module resolution respectively),
  not project-introduced packages.

## Accessibility Considerations

The frontend shell includes a skip-to-main-content link, semantic landmark elements
(`header`, `main`, `footer`), a single `<h1>` per page, visible `focus-visible`
outlines on interactive elements, and no information conveyed by color alone. Verified
manually via keyboard navigation (Tab reaches the skip link first, then the brand
link, then the footer link) and via rendered HTML inspection. No automated
accessibility tooling (`axe`) is wired in yet — appropriate for a two-link, one-page
shell; it will be introduced once there is real interactive UI to audit (Milestone 4
onward).

## Risks

| Risk | Mitigation |
|---|---|
| Spring Boot 4 / Next.js 16 are materially newer than commonly documented versions, increasing the chance of outdated assumptions | Verified every claim by actually running the toolchain (`./mvnw test`, `npm run build`, live `curl` checks against both dev servers) rather than assuming prior-version behavior; consulted the Next.js-bundled docs (`frontend/AGENTS.md`, `node_modules/next/dist/docs/`) before writing App Router code |
| `postcss` override could silently stop applying if Next.js changes its internal dependency structure in a future upgrade | Documented in `frontend/README.md`; `npm audit` is part of the ongoing quality gate and will surface a regression |

## Completion Summary

All planned Milestone 2A deliverables were completed: the backend boots on Java 21 via
the Maven Wrapper with a passing application-context test, and the frontend runs,
lints, type-checks, tests, and builds cleanly with an accessible, responsive shell.
Both applications were manually verified running (not just build-checked) before this
milestone was considered complete. No database, domain, or authentication code was
introduced, consistent with the milestone's explicit scope. The recruiter-readiness
review for this milestone is recorded in the completion report delivered alongside it.
