# Task 026: Operating-Hours Schema, Open-Now Calculation, and Resource Filters

## Objective

Add a structured weekly operating-hours schedule per resource, a
`Clock`-injected, timezone-safe open-now calculation, an `ADMIN`/
`MODERATOR`-only write endpoint, and public `costType`/`verificationStatus`/
`openNow` filters on the existing resource listing query.

## Context

The first of two tasks completing Milestone 6B. Builds on Milestone 3B/3C's
resource domain and Milestone 6A's unified `ResourceRepository.search`
query (ADR-010), extending it rather than replacing it.

## Scope

- `V6__create_resource_operating_hours.sql` — `resource_operating_hours`
  table, `UNIQUE(resource_id, day_of_week)`, closed/open-times `CHECK`
  constraint, `ON DELETE CASCADE`.
- `ResourceOperatingHours` entity, `ResourceOperatingHoursRepository`
  (`findByResourceId`, `findByResourceIdIn`, `deleteByResourceId`).
- `OperatingHoursEntry` business-layer read model.
- `OpenNowCalculator` — `Clock`-injected same-day/overnight/overnight-
  continuation algorithm; `ClockConfig` — the one production
  `Clock.systemUTC()` bean.
- `OperatingHoursValidation` — schedule body validation, reusing
  `ValidationException`.
- `OperatingHoursEntryRequest`, `ReplaceOperatingHoursRequest`,
  `OperatingHoursEntryResponse`, `OperatingHoursResponse`, `HoursStatus`.
- `PUT /api/v1/resources/{id}/operating-hours` — `ResourceController`,
  `ResourceService.replaceOperatingHours`, `SecurityConfig` gating.
- `ResourceRepository.search` extended with `costType`,
  `verificationStatus`, and a correlated `EXISTS` subquery for `openNow`.
- `ResourceService.search` — batch-loads hours per page
  (`findByResourceIdIn`), computes one Halifax "now" per request.
- `ResourceResponse`/`ResourceSummaryResponse`/`ResourceDetails` extended.
- `InvalidCostTypeException`, `InvalidVerificationStatusException`,
  `InvalidOpenNowFilterException`.
- OpenAPI documentation for all of the above.
- [ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md).

## Out of Scope

Frontend filter/schedule UI (Task 027), holiday/seasonal schedules,
multiple intervals per day, next-opening-time calculation, geospatial
search (Milestone 7).

## Acceptance Criteria

- [x] `V6` migration creates the table with the documented constraints;
      earlier migrations unmodified.
- [x] Open-now calculation correct for same-day, overnight, and
      overnight-continuation-from-yesterday intervals, including exact
      boundary cases (before/at/after opening and closing) and DST
      (standard-time and daylight-time), proven with fixed-`Clock` tests.
- [x] A resource with no schedule is `UNKNOWN`/`null`; any schedule always
      resolves `OPEN`/`CLOSED`.
- [x] `PUT .../operating-hours` replaces the full schedule transactionally;
      `ADMIN`/`MODERATOR` only (`401`/`403` otherwise); rejects invalid
      schedules with `400 VALIDATION_ERROR`.
- [x] `costType`/`verificationStatus`/`openNow` all independently optional,
      combine correctly with existing filters; invalid values return their
      own `400` codes.
- [x] `openNow=true` never returns a resource with `UNKNOWN` hours;
      pagination totals stay exact (filtering happens in the query, not
      after fetching a page).
- [x] Operating-hours lookups for a resource list are batch-loaded (one
      additional query per page), never N+1.
- [x] All 335 inherited backend tests still pass, alongside 74 new ones
      (409 total).

## Technical Approach

Extends, rather than replaces, ADR-010's single parameterized JPQL query —
`costType`/`verificationStatus` as more `(:param IS NULL OR ...)`
predicates, `openNow` as a correlated `EXISTS` subquery against
`resource_operating_hours` using bind parameters (`today`/`yesterday`/`now`,
computed once per request from the injected `Clock`) the service layer
derives — never string-concatenated, never dependent on the database's own
`now()`. The identical same-day/overnight/overnight-continuation logic is
expressed twice by design: once in SQL (the filter) and once in Java
(`OpenNowCalculator`, the display value) — both are exercised against the
same seeded data in the service-layer tests, so a divergence between them
would show up as a failing test, not a silent inconsistency between what a
resource card claims and what the filter actually returned. See
[ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md) for the
full reasoning, including why a collection `JOIN FETCH` and a Spring Data
`Specification` rewrite were both rejected for this milestone.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. `OpenNowCalculatorTest` (14 fixed-`Clock`
unit tests), `OperatingHoursValidationTest` (13 pure unit tests),
`ResourceOperatingHoursRepositoryIntegrationTest` (11 tests against the
real `postgis/postgis:17-3.5` database — migration shape, constraints,
cascade, batch loading), extended `ResourceServiceIntegrationTest` (18 new
tests: filters individually and combined, schedule replacement and its
transactional full-replace semantics, missing/inactive resource
behavior), extended `ResourceApiIntegrationTest` (13 new tests: filters
over HTTP, the write endpoint, the detail response's `hours` section, the
exact wire-format of `LocalTime` serialization, OpenAPI), and 5 new role-
matrix tests in `AuthorizationMatrixApiIntegrationTest`.

## Result

Completed. 74 new tests (14 calculator, 13 validation, 11 repository, 18
service, 13 API, 5 authorization-matrix) pass alongside the 335 from
Milestones 3A-6A (409 total, authoritative per `./mvnw clean verify`).

## Related Commits

`build: add operating-hours schema`,
`feat: add operating-hours model and open-now calculation`,
`feat: add resource cost, verification, and open-now filters`,
`test: add operating-hours and filter backend coverage`.
