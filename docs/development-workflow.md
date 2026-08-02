# Development Workflow

This document describes how HFX Connect is actually built: branching, commits, and the
milestone process. It is written so the process is understandable from the repository
alone, without needing outside context.

## Milestone-Based Development

Work proceeds one milestone at a time, defined in the roadmap below. Each milestone is
developed on its own branch, documented, tested, reviewed, and merged before the next
one begins.

### Roadmap

| # | Milestone | Focus |
|---|---|---|
| 1 | `milestone/01-project-foundation` | Problem, vision, personas, scope, user stories, repository and documentation foundation |
| 2A | `milestone/02a-application-initialization` | Spring Boot + Next.js scaffolding, quality tooling, accessible frontend shell |
| 2B | `milestone/02b-database-environment` | Docker Compose PostgreSQL/PostGIS, Flyway, env-based backend DB config, health endpoint |
| 3A | `milestone/03a-category-domain` | Category persistence, DTOs, validation, REST API |
| 3B | `milestone/03b-resource-domain` | Resource persistence and business layer (entity, service, validation — no HTTP API) |
| 3C | `milestone/03c-public-resource-api` | Public resource REST API (controller, DTOs) on top of Milestone 3B's business layer |
| 4 | `milestone/04-public-frontend` | Public resource list, detail pages, application shell |
| 5A | `milestone/05a-user-registration` | User persistence, password hashing, validation, duplicate-account prevention, registration API |
| 5B | `milestone/05b-authentication-sessions` | Login, access tokens, refresh cookies, logout |
| 5C | `milestone/05c-role-authorization` | Roles, backend authorization, protected frontend routes |
| 6A | `milestone/06a-keyword-search` | Public keyword search, combined with category filtering, sorting, and pagination |
| 6B | `milestone/06b-operating-hours-filters` | Structured operating hours, open-now logic, cost/verification filters |
| 7A | `milestone/07a-postgis-nearby-search` | PostGIS resource locations, nearby search, distance ordering (backend only) |
| 7B | `milestone/07b-interactive-map` | Visual map, browser geolocation, marker clustering, list/map sync |
| 8 | `milestone/08-user-features` | Saved resources, submissions, correction reports |
| 9 | `milestone/09-moderation` | Moderation queue, approvals, verification status, audit trail |
| 10 | `milestone/10-organizations-events` | Organization ownership, events, expiry handling |
| 11 | `milestone/11-quality` | Full test pass, security review, accessibility audit |
| 12 | `milestone/12-release` | Production deployment, CI/CD, monitoring, final documentation |

Large milestones are split into lettered sub-milestone branches (for example
`milestone/03a-category-domain`, `milestone/03b-resource-domain`) when a single
session cannot responsibly complete the whole milestone with proper testing and review.

Each milestone branch is merged into `main` via a pull request once its acceptance
criteria, documented in `docs/milestones/milestone-XX-name.md`, are met.

## Branching Rules

- Feature/milestone work happens on `milestone/...` branches, never directly on `main`.
- Branches are not force-pushed.
- A new milestone branch is not created until the previous one has been merged (or the
  project owner explicitly directs otherwise).

## Commit Conventions

Commits use Conventional Commits prefixes:

| Prefix | Use |
|---|---|
| `feat:` | A new user-facing or API capability |
| `fix:` | A bug fix |
| `test:` | Adding or updating tests without changing behavior |
| `docs:` | Documentation only |
| `refactor:` | Internal restructuring with no behavior change |
| `chore:` | Tooling, dependencies, repository housekeeping |
| `ci:` | CI/CD pipeline changes |
| `build:` | Build configuration, Docker, migrations infrastructure |
| `perf:` | Performance improvements |

Each commit represents one coherent, explainable unit of work, leaves the affected part
of the project in a working state, and excludes unrelated changes, secrets, and
generated build output. Before every commit: `git status`, `git diff`, and
`git diff --check` are reviewed; changes are staged deliberately by path rather than
with a blanket `git add .`.

## Documentation Produced Per Milestone

Every milestone updates:

- `docs/milestones/milestone-XX-name.md` — objective, scope, acceptance criteria,
  completion summary.
- `docs/tasks/NNN-task-name.md` — for each meaningful internal task (not every small
  edit).
- `docs/development-log/YYYY-MM-DD.md` — a factual record of what was actually done,
  written the day the work happens.
- Architecture documents and ADRs, when the milestone makes or changes a significant
  technical decision.
- `docs/career/resume-evidence.md` and `docs/career/interview-notes.md`, once the
  milestone delivers a real, demonstrable feature.

## Quality Gate Before Every Push

Before a milestone branch is pushed, applicable checks are run and must pass:
backend tests (`./mvnw verify`), frontend checks (`npm run lint`, `npm run typecheck`,
`npm test`, `npm run build`), Docker Compose validity, and a review of the full diff for
unrelated changes or accidental secrets. The exact commands available depend on what
each milestone has actually built — Milestone 1 is documentation-only, so no
application build commands apply yet.

## Pull Requests

Each milestone branch is opened as a pull request into `main` using the template in
`docs/milestones/` acceptance criteria plus a summary of what shipped, how it was
tested, and any known limitations. Pull requests are not merged automatically; the
project owner reviews and merges.
