# HFX Connect

[![CI](https://github.com/beaprogram/hfx-connect/actions/workflows/ci.yml/badge.svg)](https://github.com/beaprogram/hfx-connect/actions/workflows/ci.yml)

HFX Connect is a location-based community-services platform for Halifax students,
newcomers, residents, community organizations, moderators, and administrators. It
consolidates community resources — food assistance, study spaces, employment support,
newcomer services, recreation, and events — that are currently scattered across
municipal websites, organization pages, and social media, and adds transparent
verification so users can trust what they find.

**Project status: Milestone 6B (Structured Operating Hours, Open-Now Logic,
and Cost/Verification Filters) complete.** The backend has three working REST
APIs: categories (Milestone 3A —
[backend/README.md](backend/README.md#category-api-apiv1categories)), resources
(Milestone 3C, built on the persistence/business layer Milestone 3B added, now
with keyword search (Milestone 6A) and structured operating hours/open-now/
cost/verification filtering (Milestone 6B) —
[backend/README.md](backend/README.md#resource-api-apiv1resources)), and
authentication — registration (Milestone 5A), login/refresh/logout (Milestone
5B), and a current-user endpoint (Milestone 5C —
[docs/api/README.md](docs/api/README.md#auth-apiv1auth)). Every request is
authenticated by a Bearer access token where required, and role-based
authorization protects category/resource creation and operating-hours
replacement — see
[docs/architecture/security-architecture.md](docs/architecture/security-architecture.md)
for the full design. The frontend has real authentication (`/login`,
`/register`, a protected `/dashboard`, an in-memory access token, and session
restoration via the `HttpOnly` refresh cookie — see
[docs/architecture/frontend-architecture.md](docs/architecture/frontend-architecture.md#authentication-architecture))
and a filterable public resource list — `/resources` supports keyword search,
category filtering, cost/verification/open-now filtering, sorting, and
pagination, all enforced server-side, plus a resource detail page showing the
real weekly schedule and current open status (see
[ADR-010](docs/decisions/ADR-010-keyword-search-design.md) and
[ADR-011](docs/decisions/ADR-011-operating-hours-and-open-now.md)). See
[docs/milestones/](docs/milestones/) for exactly what each milestone delivered, and
[docs/development-workflow.md](docs/development-workflow.md) for the full
12-milestone roadmap (Milestones 3, 5, and 6 are each split into lettered
sub-milestones — 3A/3B/3C, 5A/5B/5C, 6A/6B). A baseline GitHub Actions workflow
verifies the backend and frontend on pull requests; deployment automation
remains part of the later release milestone.

## The Problem

A student looking for a food bank, a free workshop, a quiet study space, or a
newcomer service in Halifax has to check several disconnected sources, with no
reliable way to tell whether a listing is current, free, or open right now. See
[docs/product/problem-and-vision.md](docs/product/problem-and-vision.md) for the full
problem statement, product vision, and measurable success criteria.

## Target Users

International students, newcomers and recent immigrants, community organizations
maintaining their own listings, and moderators/administrators responsible for the
directory's trustworthiness. Full personas: [docs/product/personas.md](docs/product/personas.md).

## Planned MVP Features

See [docs/product/mvp-scope.md](docs/product/mvp-scope.md) for the full priority
breakdown. At a high level, the MVP will let the public browse, search, filter, and
view resources on a map or in an accessible list; let registered users save resources,
submit new listings, and report incorrect information; let organizations manage their
own approved listings and events; and let moderators and administrators review
submissions and reports, manage verification status, and maintain an audit history.

The public browsing slice of this (browse, filter by category/cost/
verification/open-now, keyword search, sort, view detail with the real
weekly schedule and current open status) is now real — see
[Local Setup](#local-setup) to run it. Account registration, login, and a
protected dashboard (`/login`, `/register`, `/dashboard` on the frontend;
`POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`,
`GET /api/v1/users/me` on the backend) are real too, with backend-enforced
role-based authorization on category/resource creation and operating-hours
replacement. Maps, saved resources, submissions, organization/moderator
tooling, and role-specific dashboards are not implemented yet. There is no
update/delete endpoint on any backend API yet either, and keyword search
still has no relevance ranking or typo tolerance. Everything else in this
section describes the plan, not the current state.

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js, React, TypeScript, Tailwind CSS, TanStack Query, Zod |
| Backend | Java 21, Spring Boot, Spring Security (full `SecurityFilterChain` as of Milestone 5C), JJWT, Spring Data JPA, Maven |
| Database | PostgreSQL with PostGIS |
| Migrations | Flyway |
| Testing | JUnit, Mockito, Testcontainers, React Testing Library, Playwright |
| Local infrastructure | Docker Compose |
| CI | GitHub Actions for backend verification and frontend lint/typecheck/test/build |
| Deployment | Planned for the release milestone |
| Deployment (MVP) | Vercel (frontend), Render (backend), managed PostgreSQL |

Rationale for the significant technical decisions already made is documented as
architecture decision records in [docs/decisions/](docs/decisions/).

## Architecture

A high-level system diagram, backend module structure, and API conventions are
documented in
[docs/architecture/system-overview.md](docs/architecture/system-overview.md); the
layered pattern each backend domain follows (entity/repository/service/controller,
DTOs, centralized error handling — and that a controller can be added after its
business layer, not necessarily alongside it) is documented in
[docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md),
established in practice by categories and resources, which both now have full APIs.
The frontend's own architecture (route structure, the typed API client and Zod
validation layer, the TanStack Query prefetch/hydration strategy, URL-state handling,
and accessibility decisions) is documented in
[docs/architecture/frontend-architecture.md](docs/architecture/frontend-architecture.md).
The current schema
is documented in [docs/database/](docs/database/), and the API contract in
[docs/api/README.md](docs/api/README.md) (also always available live from a running
backend at `/v3/api-docs` and `/swagger-ui.html`).

## Repository Structure

```
hfx-connect/
  frontend/                  Next.js public browsing application — see frontend/README.md
  backend/                    Spring Boot application (Category and Resource APIs) — see backend/README.md
  docs/
    product/                  Problem, vision, personas, MVP scope, user stories
    architecture/          System architecture overview, backend, frontend, and security architecture
    decisions/               Architecture decision records (ADRs)
    wireframes/            Low-fidelity page/state plans written before implementation
    milestones/             One document per milestone: scope, acceptance criteria,
                                       completion summary
    tasks/                     Task-level documentation for meaningful units of work
    development-log/    Dated, factual logs of work completed
    career/                    Resume evidence and interview notes, tied to real
                                       implemented features
    api/                       API documentation (populated from Milestone 3 onward)
    database/               Database schema documentation
  infrastructure/            Deployment/infrastructure configuration (added later)
  .github/workflows/     Pull-request CI
  docker-compose.yml    Local PostgreSQL/PostGIS database
  README.md
  .gitignore
```

## Development Workflow

Work proceeds one milestone at a time on a dedicated `milestone/NN-name` branch, with
its own scope, acceptance criteria, and completion summary documented in
`docs/milestones/`. See [docs/development-workflow.md](docs/development-workflow.md)
for the full branching model, commit conventions, and the 12-milestone roadmap from
project foundation through release.

## Local Setup

The frontend calls the backend directly from the browser (see
[backend/README.md#cors](backend/README.md#cors)), and the backend depends on a
running local database — start them in this order.

**Database** (requires Docker or a Docker-compatible runtime):

```bash
docker compose up -d
docker compose ps   # wait for "healthy"
```

This starts PostgreSQL 17 with PostGIS on `localhost:5432` with development-only
default credentials baked into `docker-compose.yml` (no `.env` file required for
default local setup). `docker compose down -v` destroys local database data; plain
`docker compose down`/`stop` does not.

**Backend** (requires JDK 21, and the database running):

```bash
cd backend
set -a; source .env; set +a   # JWT_SECRET is required — see below
./mvnw spring-boot:run
```

Runs on [http://localhost:8080](http://localhost:8080) and connects to the database
above using matching defaults. Flyway migrates the schema automatically on startup.
**`JWT_SECRET` must be set** (copy `backend/.env.example` to `backend/.env` first) —
deliberately, `application.properties` has no working default for it, unlike every
other setting (see [backend/README.md](backend/README.md#required-jwt_secret) for
why). See [backend/README.md](backend/README.md) for the rest of the
environment-variable overrides, the health endpoint, and troubleshooting (including
a port-conflict scenario encountered and resolved while building an earlier
milestone).

**Frontend** (requires Node.js 20+):

```bash
cd frontend
npm install
npm run dev
```

Runs on [http://localhost:3000](http://localhost:3000) and expects the backend at
`http://localhost:8080` by default (see
[frontend/README.md](frontend/README.md#environment-configuration) to override). See
[frontend/README.md](frontend/README.md) for the full script list.

## Documentation Index

- [Problem, Vision, and Success Criteria](docs/product/problem-and-vision.md)
- [Personas](docs/product/personas.md)
- [MVP Scope and Feature Priorities](docs/product/mvp-scope.md)
- [User Journeys, Stories, and Acceptance Criteria](docs/product/user-journeys-and-stories.md)
- [System Architecture Overview](docs/architecture/system-overview.md)
- [Architecture Decision Records](docs/decisions/)
- [Development Workflow](docs/development-workflow.md)
- [Milestone 1: Project Foundation](docs/milestones/milestone-01-project-foundation.md)
- [Milestone 2A: Application Initialization](docs/milestones/milestone-02a-application-initialization.md)
- [Milestone 2B: Database Environment](docs/milestones/milestone-02b-database-environment.md)
- [Milestone 3A: Category Domain and API](docs/milestones/milestone-03a-category-domain.md)
- [Milestone 3B: Resource Persistence and Business Layer](docs/milestones/milestone-03b-resource-domain.md)
- [Milestone 3C: Public Resource API](docs/milestones/milestone-03c-public-resource-api.md)
- [Milestone 4: Public Frontend](docs/milestones/milestone-04-public-frontend.md)
- [Milestone 5A: User Registration Foundation](docs/milestones/milestone-05a-user-registration.md)
- [Milestone 5B: Login, Token Refresh, and Logout](docs/milestones/milestone-05b-authentication-sessions.md)
- [Milestone 5C: Request Authentication, Role Authorization, and Protected Frontend Routes](docs/milestones/milestone-05c-role-authorization.md)
- [Milestone 6A: Keyword Search and Public Resource Filtering](docs/milestones/milestone-06a-keyword-search.md)
- [Milestone 6B: Structured Operating Hours, Open-Now Logic, and Cost/Verification Filters](docs/milestones/milestone-06b-operating-hours-filters.md)
- [Wireframes](docs/wireframes/)
- [API Documentation](docs/api/README.md)
- [Database Documentation](docs/database/)
- [Backend Architecture](docs/architecture/backend-architecture.md)
- [Frontend Architecture](docs/architecture/frontend-architecture.md)
- [Security Architecture](docs/architecture/security-architecture.md)
- [ADR-006: Frontend-Backend Connectivity (CORS)](docs/decisions/ADR-006-frontend-backend-connectivity.md)
- [ADR-007: User Identity and Password Hashing](docs/decisions/ADR-007-user-identity-and-password-hashing.md)
- [ADR-008: Authentication Session Architecture](docs/decisions/ADR-008-authentication-session-architecture.md)
- [ADR-009: Request Authentication and Role Authorization](docs/decisions/ADR-009-request-authentication-and-role-authorization.md)
- [ADR-010: Keyword Search Design](docs/decisions/ADR-010-keyword-search-design.md)
- [ADR-011: Operating Hours and Open-Now Design](docs/decisions/ADR-011-operating-hours-and-open-now.md)
- [Development Log](docs/development-log/)
- [Resume Evidence](docs/career/resume-evidence.md)
- [Interview Notes](docs/career/interview-notes.md)

## Known Limitations (as of Milestone 6B)

- **No rate limiting exists** — login accepts unlimited attempts. **No
  access-token revocation exists** — a compromised access token remains valid
  until it naturally expires (≤15 minutes by default), independent of any
  role/status change (the per-request database reload catches that on the
  *next* request, not immediately). Both are documented, honest
  limitations — see
  [docs/architecture/security-architecture.md](docs/architecture/security-architecture.md).
- **No object-level/ownership authorization** — every authorization rule is
  role-based; "this resource belongs to this organization" isn't a concept
  yet (Milestone 10). `ORGANIZATION` accounts cannot create resources yet
  for the same reason.
- The frontend's `/dashboard` route guard is a client-side UX convenience,
  not a security boundary — the backend's `SecurityConfig` is authoritative
  regardless of what the frontend renders or hides. See
  [ADR-009](docs/decisions/ADR-009-request-authentication-and-role-authorization.md).
- No role-specific dashboards, category/resource creation UI, saved
  resources, submissions, moderation, or organization tooling exist yet
  (Milestone 5C's frontend scope is deliberately `/login`, `/register`, and
  a minimal `/dashboard` only).
- Newly-registered accounts are always `emailVerified: false` — no email-delivery
  mechanism exists to verify them, a deliberate, documented limitation (see
  [ADR-007](docs/decisions/ADR-007-user-identity-and-password-hashing.md)), not a bug.
  No password reset, MFA, or OAuth/social login exist yet either.
- No user-facing endpoint (update, delete, password reset) exists beyond
  registration/login/refresh/logout/current-user.
- Neither the Category nor Resource API has an update or delete endpoint.
  `ResourceService.update`/`deactivate` exist and are fully tested but aren't exposed
  over HTTP yet.
- The public resource list has real `costType`/`verificationStatus`/`openNow`
  filters (Milestone 6B), but `verificationStatus=VERIFIED` currently
  returns nothing (nothing has ever been `VERIFIED` yet — Milestone 9), each
  filter accepts one value at a time (no multi-select), and there's still no
  `active` override (would let anyone browse deactivated listings with no
  authentication boundary to gate it behind).
- Keyword search (Milestone 6A) has no typo tolerance, relevance ranking, or
  multi-word AND/OR semantics — a single-phrase, case-insensitive substring
  match across name/description/address/city only, with no `pg_trgm`/full-text
  index (an explicit, revisitable scale assumption — see
  [ADR-010](docs/decisions/ADR-010-keyword-search-design.md)).
- Operating hours (Milestone 6B) support one interval per day only — no
  split shifts, holiday exceptions, seasonal schedules, appointment-only
  scheduling, next-opening-time prediction, or per-resource timezone
  (`America/Halifax` is fixed for every resource — see
  [ADR-011](docs/decisions/ADR-011-operating-hours-and-open-now.md)). There
  is no public UI for editing hours — only an `ADMIN`/`MODERATOR` API
  endpoint. No distance/geospatial filtering, maps, saved resources,
  submissions, moderation, or organizations exist yet (Milestone 7-10).
- No deployment workflow or hosted environment exists yet; CI currently verifies the
  backend and frontend only.
- No automated dependency-vulnerability scanning is configured in this project.
  `npm audit` reports pre-existing transitive vulnerabilities in the frontend's
  build/test toolchain (postcss, sharp, jest chains bundled by Next.js/tooling
  dependencies) that require a breaking Next.js downgrade to resolve — not
  introduced by, or specific to, this milestone.
- Frontend responsive/visual verification continues to be code-review- and
  `curl`-based, not a live graphical browser session — no browser-automation
  tool was available in the development environment used for this or the
  Milestone 4 session. See
  [docs/architecture/frontend-architecture.md](docs/architecture/frontend-architecture.md)'s
  Known Limitations.
- No live demo, screenshots, or demo video exist yet — these will be added once there
  is more of the application to show (Milestone 12 for a full demo).
