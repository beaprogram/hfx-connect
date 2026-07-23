# Milestone 3C: Public Resource API

## A Note on Milestone Numbering

The instructions that produced this milestone's work labeled it "Milestone 3B —
Resource Persistence and Public Resource API" and asked for a branch named
`milestone/03b-resource-domain`. Both were already used: Milestone 3B (resource
persistence and business layer, deliberately **without** an HTTP API) was completed
and merged as [PR #5](https://github.com/beaprogram/hfx-connect/pull/5), and that
exact branch name was already deleted after merging. The already-committed
documentation from that milestone — `docs/database/README.md`,
`docs/architecture/backend-architecture.md`, `docs/api/README.md`,
`backend/README.md`, the root `README.md`, and code comments in
`ResourceService`/`CommunityResource`/`CreateResourceCommand` — all independently and
repeatedly state "the public resource API is Milestone 3C's responsibility." This
document (and its branch, `milestone/03c-public-resource-api`) follows that
already-established naming rather than creating a second, conflicting "Milestone 3B."
This is the same kind of correction as
[ADR-005](../decisions/ADR-005-category-identifiers-and-normalization.md)'s
`category_id` type note: the instructions describing the work and the project's own
prior, already-merged decisions disagreed, and the prior decision — being already
built, tested, and documented — took precedence.

## A Note on Schema Scope

The instructions also describe a richer resource schema than what Milestone 3B
actually built: a `shortDescription`/`fullDescription` split, `accessibilityInformation`,
`lastVerifiedAt`, a five-value `cost_type` (adding `SLIDING_SCALE`/`DONATION`), and a
five-value `verification_status` (adding `PENDING_REVIEW`/`NEEDS_REVIEW`/`OUTDATED`).
None of these exist in the already-applied, unmodifiable `V3__create_resources_table.sql`
migration. Expanding the schema now would mean either altering an applied migration
(explicitly forbidden in every milestone so far) or shipping a new migration purely to
match a planning document's suggestion rather than a real, demonstrated product need.
This milestone builds the public API on top of the **existing** schema/entity/service
exactly as Milestone 3B left them (single `description` field, four-value
`CostType`, two-value `VerificationStatus`). Expanding the schema is better deferred
to whichever future milestone actually needs it — for example, `PENDING_REVIEW`/
`NEEDS_REVIEW`/`OUTDATED` genuinely belong to Milestone 9's moderation workflow, where
they can be added alongside the moderation logic that gives them meaning, rather than
speculatively now.

## Objective

Expose the resource domain (already fully built and tested in Milestone 3B) as a
public, read-focused REST API: create a resource, retrieve one by ID or slug, and
list/filter resources — all following the exact conventions
`docs/architecture/backend-architecture.md` already established for categories.

## Product Value

This is the first time a real HFX Connect API client (a future frontend, or anyone
using `curl`) can discover actual community resources — food banks, libraries, study
spaces — rather than only categories. It completes the "browse resources" half of the
core product journey described in `docs/product/user-journeys-and-stories.md`.

## Technical Scope

- `ResourceController` (`/api/v1/resources`): `POST` (create), `GET /{id}`,
  `GET /slug/{slug}`, `GET` (paginated list with optional `categoryId` filter and
  allowlisted `sort`).
- HTTP DTOs: `ResourceCreateRequest` (Bean Validation-annotated), `ResourceResponse`
  (full detail), `ResourceSummaryResponse` (lean list-card shape), `ResourcePageResponse`,
  `CategorySummaryResponse` (embedded category — id/name/slug, not the full category
  representation).
- `ResourceDetails` (business-layer read model) extended with `categoryName`/
  `categorySlug`, and `ResourceRepository` given `JOIN FETCH` query variants
  (`findByIdWithCategory`, `findBySlugAndActiveTrueWithCategory`,
  `findByActiveWithCategory`, `findByCategoryIdAndActiveWithCategory`) so building a
  response with an embedded category summary never triggers an N+1 query against the
  lazy `category` association.
- `ResourceService` gained `getActiveById` (parallel to the existing `getActiveBySlug`)
  and an allowlisted `sort` parameter (`name` ascending by default, `createdAt`
  descending) on both listing methods, rejecting anything else with the new
  `INVALID_SORT` / `InvalidSortException`.
- `CategoryUnavailableException` (Milestone 3B, single code `CATEGORY_UNAVAILABLE` for
  both "missing" and "inactive") split into two distinct exceptions —
  `CategoryNotFoundException` (404, `CATEGORY_NOT_FOUND`) and
  `InactiveCategoryException` (400, `INACTIVE_CATEGORY`) — now that these codes are
  externally observable through a real HTTP response for the first time, and the
  instructions' own error-code list names them separately.
- Public reads only ever see active resources: a deactivated resource returns `404`
  from every read endpoint, identical to a nonexistent one. There is still no way to
  view or list inactive resources through this API — that is an administrative
  capability gated behind the authorization Milestone 5 introduces, not something to
  fake here.
- OpenAPI documentation for all four endpoints via the existing `springdoc-openapi`
  annotation convention.

## Out of Scope

Resource update/delete HTTP endpoints (`ResourceService.update`/`deactivate` remain
business-layer-only, exactly as Milestone 3B left them — the instructions explicitly
forbid adding `PATCH`/`DELETE` here), operating hours, geographic/PostGIS columns,
keyword search, verification-status filtering (see below), authentication/authorization,
organizations, saved resources, reports, submissions, moderation, events, Redis,
CI/CD changes, and deployment.

**Why no `verificationStatus` filter, despite being suggested:** every resource in the
system is currently `UNVERIFIED` — there is no mutator, no moderation workflow, and no
way for any resource to become `VERIFIED` yet (Milestone 9). A filter over a field with
exactly one real-world value provides no practical utility today and would be pure
speculation; it can be added trivially once Milestone 9 gives it something to filter.

**Why no `active` override on the public list, despite the Category API precedent:**
`GET /api/v1/categories` does accept an `active` filter. Resources are different: an
`active=false` toggle on a *public, unsecured* resource endpoint would let anyone browse
deactivated listings — a materially different exposure than deactivated categories,
since a deactivated resource may represent contact/location information that was
deliberately taken down. Without any authentication boundary to gate it behind yet,
the safer, more defensible design is not exposing the toggle at all; the list is always
active-only.

## Acceptance Criteria

- [x] `POST /api/v1/resources` returns `201` with a `Location` header and the created
      resource, including an embedded category summary.
- [x] `GET /api/v1/resources/{id}` and `GET /api/v1/resources/slug/{slug}` return `200`
      for an active resource, `404` for a missing or deactivated one.
- [x] `GET /api/v1/resources` returns a bounded, paginated list; supports an optional
      `categoryId` filter and an allowlisted `sort` (`name`, `createdAt`).
- [x] Pagination is bounded (`INVALID_PAGINATION` for out-of-range `page`/`size`); sort
      is allowlisted (`INVALID_SORT` for anything else).
- [x] Missing category → `404 CATEGORY_NOT_FOUND`; inactive category →
      `400 INACTIVE_CATEGORY`; these are distinguishable from each other and from
      `404 RESOURCE_NOT_FOUND`.
- [x] Duplicate (derived) slug → `409 RESOURCE_CONFLICT`.
- [x] Validation failures (blank/oversized fields, malformed email, unsafe website
      scheme, malformed postal code) → `400 VALIDATION_ERROR` with `fieldErrors`.
- [x] Malformed JSON → `400 MALFORMED_REQUEST`; no response ever leaks stack traces,
      SQL, or internal class names.
- [x] OpenAPI (`/v3/api-docs`) includes all three resource route templates; Swagger UI
      renders them.
- [x] No N+1 queries: list/detail reads use `JOIN FETCH` repository queries.
- [x] Existing Category API, health, Flyway, and OpenAPI tests all still pass.
- [x] `./mvnw clean verify` passes: 147/147 tests, 0 failures, 0 errors, 0 skipped
      (verified against the Surefire reports, not hand-summed).
- [x] Temporary unsecured-`POST` limitation documented (identical to the Category API's).

## Testing

`ResourceApiIntegrationTest` (24 tests, new) covers the full HTTP surface end-to-end
against the real database via Testcontainers: creation, the embedded category summary,
every validation/conflict/not-found scenario above, pagination, category filtering,
sort (including the invalid-sort rejection), deactivated-resource invisibility (set up
via `ResourceService.deactivate` directly, since there's no HTTP path to reach that
state yet), the OpenAPI document, and a Category-API regression check. Six new test
methods were added to the existing `ResourceServiceIntegrationTest` (`getActiveById`
plus the new sort behavior). One pre-existing test,
`GlobalExceptionHandlerIntegrationTest`, had used `GET /api/v1/resources` itself as its
example of a genuinely unmapped path (accurate when it was written, in Milestone 3B,
before any resource controller existed) — fixed to use a path guaranteed to stay
unmapped, since this milestone's entire purpose is mapping that exact route.

See `docs/development-log/2026-07-23.md` for exact commands and authoritative Surefire
output.

## Documentation Updated

`docs/api/README.md`, `docs/database/README.md`, `docs/architecture/backend-architecture.md`,
`backend/README.md`, root `README.md`, `docs/development-workflow.md` (added an
explicit Milestone 3C roadmap row), `docs/tasks/013-public-resource-api.md`,
`docs/development-log/2026-07-23.md`, `docs/career/resume-evidence.md`,
`docs/career/interview-notes.md`.

## Completion Summary

All planned Milestone 3C deliverables were completed: a working, tested, documented
public Resource API built on the resource domain Milestone 3B already established,
with no N+1 queries, precise error codes, and the same documented unsecured-write
limitation as the Category API. Two structural corrections were made along the way —
the milestone-numbering conflict with the supplied instructions (resolved as
Milestone 3C, not a second 3B) and the schema-scope mismatch (built on the existing,
already-merged schema rather than altering an applied migration) — both documented
above rather than silently deviated from. No update/delete HTTP endpoints, search,
geospatial features, or authentication were introduced, consistent with scope.
