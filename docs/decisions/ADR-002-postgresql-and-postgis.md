# ADR-002: PostgreSQL with PostGIS for Geographic Search

## Status

Accepted — 2026-07-10

## Context

The core "find a nearby service" journey requires radius search ("resources within 5km
of me") and distance-based sorting against resource coordinates. This is a first-class
product requirement, not an afterthought — see the "Nearby Food Assistance" acceptance
criteria in [user-journeys-and-stories.md](../product/user-journeys-and-stories.md).
The rest of the domain (users, organizations, categories, resources, moderation
history) is naturally relational, with real foreign-key relationships and constraints
that matter (unique slugs, unique bookmark pairs, valid status transitions).

## Decision

Use PostgreSQL as the primary datastore, with the PostGIS extension enabled for a
`geography(Point, 4326)` column on `resources`. Geographic queries use PostGIS's
`ST_DWithin` and `ST_Distance` functions with a GiST spatial index, rather than
computing distance in application code.

## Alternatives Considered

- **Calculate distance in application code (e.g., naive Haversine over all rows).**
  Rejected: this cannot use a spatial index, so it scans every active resource on every
  search request. It does not scale even at MVP content volumes gracefully and forfeits
  a well-tested, purpose-built tool for a problem PostGIS already solves correctly.
- **A dedicated document/search engine (e.g., Elasticsearch) for geo queries.**
  Rejected for the MVP: it would mean running and keeping a second datastore in sync
  with PostgreSQL for a query pattern PostGIS already handles natively, adding
  operational complexity with no MVP-stage benefit. This can be revisited later if
  full-text search needs outgrow PostgreSQL's built-in text search.
- **A NoSQL document store (e.g., MongoDB) for the whole domain.** Rejected: the domain
  is highly relational (resources belong to categories and organizations, users save
  many resources, submissions and reports reference resources and reviewers), and
  losing foreign-key and uniqueness constraints would push correctness enforcement
  entirely into application code, which is a materially weaker guarantee.

## Consequences

- Nearby search becomes a single indexed SQL query instead of an application-level
  scan-and-sort — see the reference query captured in
  [system-overview.md](../architecture/system-overview.md) and implemented starting in
  Milestone 7.
- The project takes a direct dependency on the PostGIS extension being available in
  every environment (local Docker Compose, CI Testcontainers, and the managed
  production database), which is confirmed as part of Milestone 2's environment setup.
- Flyway migrations must enable the `postgis` extension explicitly before creating the
  `geography` column.
