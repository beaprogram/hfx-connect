# HFX Connect

HFX Connect is a location-based community-services platform for Halifax students,
newcomers, residents, community organizations, moderators, and administrators. It
consolidates community resources — food assistance, study spaces, employment support,
newcomer services, recreation, and events — that are currently scattered across
municipal websites, organization pages, and social media, and adds transparent
verification so users can trust what they find.

**Project status: Milestone 2A (Application Initialization) complete.** The Spring
Boot backend and Next.js frontend now exist as runnable application shells — no
database, no persistence, no REST endpoints, and no real pages beyond a placeholder
homepage yet. See
[docs/milestones/milestone-01-project-foundation.md](docs/milestones/milestone-01-project-foundation.md)
and
[docs/milestones/milestone-02a-application-initialization.md](docs/milestones/milestone-02a-application-initialization.md)
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

None of this is implemented yet — this section describes the plan, not the current
state.

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
[docs/architecture/system-overview.md](docs/architecture/system-overview.md). A
database diagram will be added under [docs/database/](docs/database/) once the first
Flyway migrations exist (Milestone 2 onward).

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
    database/               Database schema documentation (populated from Milestone
                                       2 onward)
  infrastructure/            Deployment/infrastructure configuration (added later)
  .github/workflows/     CI pipelines (added in Milestone 12)
  docker-compose.yml    Local development environment (added in Milestone 2)
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

Both applications can be run independently today; there is nothing connecting them
yet (no shared database, no API calls from the frontend to the backend).

**Backend** (requires JDK 21):

```bash
cd backend
./mvnw spring-boot:run
```

Runs on [http://localhost:8080](http://localhost:8080). See
[backend/README.md](backend/README.md) for details — there are no routes mapped yet,
so requests currently return `404`.

**Frontend** (requires Node.js 20+):

```bash
cd frontend
npm install
npm run dev
```

Runs on [http://localhost:3000](http://localhost:3000). See
[frontend/README.md](frontend/README.md) for the full script list.

Docker Compose, PostgreSQL/PostGIS, environment variables, and database migrations are
introduced in Milestone 2B and documented here once they exist.

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
- [Development Log](docs/development-log/)
- [Resume Evidence](docs/career/resume-evidence.md)
- [Interview Notes](docs/career/interview-notes.md)

## Known Limitations (as of Milestone 2A)

- No database, persistence, or REST endpoints exist yet (Milestone 2B onward).
- The frontend has a single placeholder homepage; no resource search, listings, or
  authentication exist yet (Milestones 3-5 onward).
- No wireframes exist yet for the core screens; recommended before or alongside
  Milestone 4.
- No live demo, screenshots, or demo video exist yet — these will be added once there
  is a real application to show (Milestone 4 onward for screenshots, Milestone 12 for
  a full demo).
