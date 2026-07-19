# HFX Connect

HFX Connect is a location-based community-services platform for Halifax students,
newcomers, residents, community organizations, moderators, and administrators. It
consolidates community resources — food assistance, study spaces, employment support,
newcomer services, recreation, and events — that are currently scattered across
municipal websites, organization pages, and social media, and adds transparent
verification so users can trust what they find.

**Project status: Milestone 3B (Resource Persistence and Business Layer) complete.**
The backend has a working category REST API (Milestone 3A —
[backend/README.md](backend/README.md#category-api-apiv1categories)) and a complete
resource persistence/business layer (Milestone 3B) — category-aware validation, a
deterministic and stable slug, database-enforced integrity — with **no public
resource API yet**; that's Milestone 3C. There is no authentication and no real
frontend pages beyond a placeholder homepage. See [docs/milestones/](docs/milestones/)
for exactly what each milestone delivered, and
[docs/development-workflow.md](docs/development-workflow.md) for the full 12-milestone
roadmap.

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

None of this is implemented as a public feature yet. Category management exists as a
working backend API — see
[backend/README.md](backend/README.md#category-api-apiv1categories). Resources
(the actual food banks, study spaces, etc.) have a complete, tested backend
persistence and business layer, but no API a frontend or external client can call yet
— see [backend/README.md](backend/README.md#resource-domain-no-http-api-yet).
Everything else in this section describes the plan, not the current state.

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js, React, TypeScript, Tailwind CSS, TanStack Query, React Hook Form, Zod |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven |
| Database | PostgreSQL with PostGIS |
| Migrations | Flyway |
| Testing | JUnit, Mockito, Testcontainers, React Testing Library, Playwright |
| Local infrastructure | Docker Compose |
| CI/CD | GitHub Actions (planned, Milestone 12) |
| Deployment (MVP) | Vercel (frontend), Render (backend), managed PostgreSQL |

Rationale for the significant technical decisions already made is documented as
architecture decision records in [docs/decisions/](docs/decisions/).

## Architecture

A high-level system diagram, backend module structure, and API conventions are
documented in
[docs/architecture/system-overview.md](docs/architecture/system-overview.md); the
layered pattern each backend domain follows (entity/repository/service, DTOs,
centralized error handling, and — a controller only once an HTTP API is actually in
scope) is documented in
[docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md),
established in practice by categories (with an API) and resources (without one yet).
The current schema
is documented in [docs/database/](docs/database/), and the API contract in
[docs/api/README.md](docs/api/README.md) (also always available live from a running
backend at `/v3/api-docs` and `/swagger-ui.html`).

## Repository Structure

```
hfx-connect/
  frontend/                  Next.js application (application shell only — see frontend/README.md)
  backend/                    Spring Boot application (application shell only — see backend/README.md)
  docs/
    product/                  Problem, vision, personas, MVP scope, user stories
    architecture/          System architecture overview
    decisions/               Architecture decision records (ADRs)
    milestones/             One document per milestone: scope, acceptance criteria,
                                       completion summary
    tasks/                     Task-level documentation for meaningful units of work
    development-log/    Dated, factual logs of work completed
    career/                    Resume evidence and interview notes, tied to real
                                       implemented features
    api/                       API documentation (populated from Milestone 3 onward)
    database/               Database schema documentation
  infrastructure/            Deployment/infrastructure configuration (added later)
  .github/workflows/     CI pipelines (added in Milestone 12)
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

The frontend and backend still run independently (no API calls between them yet), but
the backend now depends on a running local database.

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
./mvnw spring-boot:run
```

Runs on [http://localhost:8080](http://localhost:8080) and connects to the database
above using matching defaults. Flyway migrates the schema automatically on startup.
See [backend/README.md](backend/README.md) for environment-variable overrides,
the health endpoint, and troubleshooting (including a port-conflict scenario
encountered and resolved while building this milestone).

**Frontend** (requires Node.js 20+):

```bash
cd frontend
npm install
npm run dev
```

Runs on [http://localhost:3000](http://localhost:3000). See
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
- [API Documentation](docs/api/README.md)
- [Database Documentation](docs/database/)
- [Backend Architecture](docs/architecture/backend-architecture.md)
- [Development Log](docs/development-log/)
- [Resume Evidence](docs/career/resume-evidence.md)
- [Interview Notes](docs/career/interview-notes.md)

## Known Limitations (as of Milestone 3B)

- `POST /api/v1/categories` has no authentication or authorization yet — anyone who
  can reach the API can create a category (Milestone 5 adds authentication).
- The `resource` domain has no HTTP API at all yet — no `ResourceController`, no
  `/api/v1/resources` route (Milestone 3C).
- The frontend has a single placeholder homepage and does not talk to the backend yet;
  no resource search, listings, or authentication exist (Milestones 4-5 onward).
- No CI pipeline runs the backend/frontend/infrastructure checks automatically yet
  (Milestone 12).
- No wireframes exist yet for the core screens; recommended before or alongside
  Milestone 4.
- No live demo, screenshots, or demo video exist yet — these will be added once there
  is a real application to show (Milestone 4 onward for screenshots, Milestone 12 for
  a full demo).
