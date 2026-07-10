# ADR-003: REST over JSON as the API Style

## Status

Accepted — 2026-07-10

## Context

The Next.js frontend needs to communicate with the Spring Boot backend for resource
search, submissions, moderation actions, saved resources, and events. The backend also
needs to be independently understandable and documentable — a recruiter or interviewer
should be able to look at the API surface and understand the product's capabilities
without reading frontend code.

## Decision

Expose a versioned JSON REST API under `/api/v1/`, following conventional HTTP verbs
and status codes, documented with OpenAPI/Swagger as endpoints are implemented starting
in Milestone 3.

## Alternatives Considered

- **GraphQL.** Rejected for the MVP: the frontend's data-fetching needs are a
  relatively small, well-known set of screens (resource list, resource detail,
  dashboard, admin queues) rather than many independent clients with divergent query
  shapes, which is where GraphQL's flexibility pays for its added complexity (schema
  stitching, resolver N+1 handling, a second query language). REST with TanStack Query
  caching covers the same needs with less operational overhead, and REST is more
  directly relevant to the target job market described in the project's stack
  rationale.
- **tRPC or another TypeScript-only RPC layer.** Rejected: it assumes both ends of the
  API are TypeScript. The backend here is Java/Spring Boot specifically because that
  combination (Java backend + TypeScript frontend) is the skill pairing this project is
  meant to demonstrate.
- **A single unversioned API surface.** Rejected: versioning under `/api/v1/` from the
  first endpoint costs nothing now and avoids a breaking migration later if the
  contract needs to change incompatibly.

## Consequences

- Every endpoint needs an explicit DTO for its request and response shape (JPA entities
  are never serialized directly), which is called out as a hard requirement in the
  backend engineering standards.
- API documentation (OpenAPI/Swagger) becomes a natural artifact of implementation
  rather than a separately maintained document, reducing the risk of docs drifting from
  the real contract.
- Error responses use one consistent JSON shape across every endpoint (see
  [system-overview.md](../architecture/system-overview.md)), so frontend error handling
  can be written once and reused.
