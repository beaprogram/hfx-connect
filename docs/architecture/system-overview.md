# System Architecture Overview

> Status: planning document written during Milestone 1 (Project Foundation). No
> application code exists yet. This document describes the intended architecture that
> Milestones 2 onward will implement; it will be revised as real implementation
> decisions are made.

## High-Level Shape

HFX Connect is a two-tier web application: a Next.js frontend that talks to a Spring
Boot backend over a JSON REST API, backed by PostgreSQL with the PostGIS extension for
geographic queries.

```
[ Next.js Web Application ]
        |  HTTPS / JSON REST API (/api/v1/...)
        v
[ Spring Boot Application ]
    |-- auth            authentication and authorization
    |-- resource         resource and category services
    |-- search            search and geospatial queries
    |-- moderation        submission and report review workflows
    |-- event              events
    |-- notification    async notifications (email)
    |
    +--> [ PostgreSQL + PostGIS ]   primary data store
    +--> [ Redis ]                   optional, introduced only if caching/session
    |                                      needs justify it
    +--> [ Email provider ]     transactional email (verification, moderation
    |                                       outcomes)
    +--> [ Halifax open data / transit feeds ]   later enhancement, not MVP
```

## Why This Stack

Full rationale for the individually significant choices lives in
[docs/decisions/](../decisions/) as architecture decision records. Summary:

| Layer | Technology | Why |
|---|---|---|
| Frontend | Next.js + TypeScript | Mature React ecosystem, file-based routing, SSR for fast/SEO-friendly public pages, strong job-market relevance |
| UI | Tailwind CSS | Consistent, fast, utility-first styling without a heavy component-library dependency |
| Data fetching | TanStack Query | Handles caching, loading and error state, and cache invalidation without hand-rolled fetch logic |
| Forms | React Hook Form + Zod | Typed, schema-validated forms with good UX and minimal re-renders |
| Backend | Java 21 + Spring Boot | Explicit, testable layered architecture; strong relevance to the target job market (see [ADR-003](../decisions/ADR-003-rest-api.md)) |
| Security | Spring Security | Battle-tested authentication/authorization primitives rather than a hand-rolled auth layer |
| Database | PostgreSQL + PostGIS | Relational integrity for a normalized domain model, plus native geographic indexing and distance queries (see [ADR-002](../decisions/ADR-002-postgresql-and-postgis.md)) |
| Migrations | Flyway | Every schema change is versioned and reproducible; no manual production schema edits |
| Testing | JUnit, Mockito, Testcontainers, Playwright | Layered automated testing against a real Postgres/PostGIS instance, not just mocks |
| Deployment (MVP) | Vercel (frontend) + Render (backend) + managed PostgreSQL | Fast path to a public URL without operating Kubernetes/ECS before the product is proven |

## Backend Module Structure

```
com.hfxconnect
  auth/
  user/
  organization/
  category/
  resource/
  search/
  moderation/
  event/
  notification/
  common/
    config/
    error/
    security/
    validation/
```

Each domain module owns its controller, service, repository, and DTOs. Controllers
stay thin (HTTP concerns only); business rules live in services; repositories are
limited to persistence concerns. JPA entities are never returned directly from the
API — every endpoint has explicit request/response DTOs, so the database schema and
the public API contract can evolve independently.

## API Conventions

- All endpoints are versioned under `/api/v1/`.
- List endpoints are paginated.
- Errors use a single consistent shape (`timestamp`, `status`, `code`, `message`,
  optional `fieldErrors`) documented alongside the API once Milestone 3 introduces the
  first real endpoints.
- Multi-step workflows (for example, approving a submission and writing its audit
  history entry) run inside a single database transaction so the system is never left
  in a partially updated state.

## Data Model Direction

Core entities anticipated by the product requirements: `users`, `organizations`,
`categories`, `resources` (now with a geographic `location` column, Milestone
7A — see [ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md)),
`resource_operating_hours` (Milestone 6B), `saved_resources`,
`resource_reports`, `resource_submissions`, `events`, and `resource_history`. The
authoritative, versioned schema lives in Flyway migration files (see
`docs/database/README.md` for the current, real schema — not a plan).

Milestone 7B built the first visual consumer of that geographic column: an
interactive Leaflet/OpenStreetMap frontend map over the existing
`GET /resources/nearby` endpoint, with browser geolocation and marker
clustering — no new database schema (see
[ADR-013](../decisions/ADR-013-interactive-map-and-geolocation-design.md)).

## Deployment Path

| Stage | Setup |
|---|---|
| Local | Docker Compose running PostgreSQL/PostGIS (and Redis if introduced) alongside the backend |
| MVP | Vercel (frontend), Render (backend), managed PostgreSQL |
| Advanced (post-MVP) | AWS ECS/Fargate, RDS PostgreSQL, S3, SES, CloudWatch — only if the project's scope grows to justify it |

## What This Milestone Does Not Cover

This document intentionally does not specify: authentication token design (Milestone
5), the finalized database schema (Milestone 2 onward, tracked via Flyway), the
concrete API surface (Milestone 3), or CI/CD pipeline configuration (Milestone 12).
Those will be documented when they are implemented, not speculated about here.
