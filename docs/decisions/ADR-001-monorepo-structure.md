# ADR-001: Monorepo Structure

## Status

Accepted — 2026-07-10

## Context

HFX Connect consists of a Next.js frontend, a Spring Boot backend, database
migrations, infrastructure configuration, and a substantial amount of project
documentation (product definition, architecture, decisions, milestones). These pieces
are developed together, by a single developer, and released together for the MVP —
there is no independent release cadence between frontend and backend at this stage.

## Decision

Use a single repository (`hfx-connect`) containing `frontend/`, `backend/`, `docs/`,
and `infrastructure/` as top-level directories, with a shared `docker-compose.yml` for
local development and a single `.github/workflows/` directory for CI.

## Alternatives Considered

- **Separate frontend and backend repositories.** Rejected for the MVP: it would
  require coordinating two repositories for every full-stack feature (a resource field
  added to the API and consumed by the UI), duplicate CI/documentation setup, and add
  no real benefit since both halves are deployed from the same milestone cadence by the
  same developer. This may become worth revisiting if the project grows a second
  contributor or an independent release cycle per side.
- **A generic multi-package workspace tool (Nx/Turborepo) from day one.** Rejected as
  premature: the project has exactly two applications and does not yet need shared
  package extraction, cross-project task graphs, or remote build caching. Section 2 of
  the project's technology direction explicitly avoids unnecessary complexity before
  the core product is stable.

## Consequences

- A single `git log` shows the full history of a feature across frontend, backend, and
  schema changes, which is useful both for day-to-day development and for explaining
  the project in an interview.
- CI must be scoped per-directory (only run frontend checks when `frontend/` changes,
  etc.) once the pipeline is built in Milestone 12, to avoid unnecessary build time.
- If the project ever needs independent deployment cadences or a second team working
  on just the frontend, this decision should be revisited.
