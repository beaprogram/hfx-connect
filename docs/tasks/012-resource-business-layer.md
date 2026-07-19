# Task 012: Resource Business Layer

## Objective

Implement `ResourceService` and its supporting business-layer models
(`CreateResourceCommand`, `UpdateResourceCommand`, `ResourceDetails`, `ResourcePage`)
and pure validation (`ResourceValidation`), covering category-relationship rules,
field normalization, deterministic slugging, and race-safe duplicate handling —
without any HTTP layer.

## Context

Part of Milestone 3B, depending on Task 011's persistence model. This is the task
where "a resource can be created, updated, deactivated, and read" actually becomes
true, exercised entirely through direct service calls in tests (no controller exists).

## Scope

- `com.hfxconnect.resource.ResourceValidation` — pure normalization/format validation
  (whitespace, province, Canadian postal code, practical phone/email checks, allowlist
  website-scheme validation), independent of Spring/the database, unit tested directly.
- `com.hfxconnect.resource.ResourceService` — category existence/active validation,
  slug generation via the shared `SlugGenerator`, duplicate-slug detection (pre-check
  plus a race-safe `DataIntegrityViolationException` translation using
  `saveAndFlush()`), create/update/deactivate/read operations, transaction boundaries.
- Domain exceptions: `ResourceNotFoundException`, `ResourceConflictException`,
  `CategoryUnavailableException` — all extend the existing shared
  `com.hfxconnect.common.error` base types, so no new `GlobalExceptionHandler` code
  was needed for them to be handled consistently (their HTTP status/code just isn't
  reachable by any request yet, since there's no controller).
- Fixed an unrelated, pre-existing bug found via this task's own regression
  verification: unmapped routes returned `500` instead of `404`
  (`GlobalExceptionHandler` had no handler for Spring's `NoResourceFoundException`).

## Out of Scope

Any controller, HTTP request/response DTO, or OpenAPI documentation for resources —
Milestone 3C.

## Acceptance Criteria

- Category must exist and be active for both resource creation and any category move
  during update; missing vs. inactive produce distinguishable exceptions
  (`CategoryUnavailableException.missing`/`.inactive`).
- Slug is generated once at creation and never changes on update, even when the name
  changes.
- A name that normalizes to an empty slug (e.g., `"&&&"`) is rejected before any
  database write is attempted.
- Duplicate slugs are rejected both via an application-level pre-check (clear error,
  common case) and via database-constraint translation (race-safe, uncommon case) —
  the latter specifically requires `saveAndFlush()` rather than `save()`, since
  `CommunityResource`'s Hibernate-generated UUID id does not force a synchronous
  insert the way `Category`'s `IDENTITY` column does.
- Deactivating a resource removes it from `getActiveBySlug`/`listActive` results.
- No partial persistence: repository row counts remain zero after every rejected
  create scenario tested.
- Website URLs are validated via an `http`/`https` allowlist, not a blocklist of
  specific dangerous schemes.

## Technical Approach

Followed `CategoryService`'s established shape (see
`docs/architecture/backend-architecture.md`) with one deliberate deviation: because
`ResourceService` has a genuine cross-entity dependency (category existence/active
state, which mocking cannot meaningfully prove), its primary test coverage is a
Testcontainers-backed integration test against real `CategoryRepository`/
`ResourceRepository` instances, rather than `CategoryServiceTest`'s Mockito-based
approach — an explicit instruction for this task, not a style preference.

## Testing Requirements

`ResourceValidationTest` (24 pure unit tests, no Spring context): whitespace/case/
postal-code normalization, deterministic slugging, every rejection scenario, and
multi-field error accumulation in one exception. `ResourceServiceIntegrationTest` (23
tests, Testcontainers): full create/update/deactivate/read behavior, category rules,
slug stability, pagination, and no-partial-persistence checks.
`GlobalExceptionHandlerIntegrationTest` (2 tests, new): guards the unmapped-route 404
fix.

## Result

Completed. 24 + 23 + 2 = 49 new tests pass, alongside the 68 from Task 011 and
Milestones 1-3A (117 total backend tests).

## Related Commit

`feat: implement resource business layer`
