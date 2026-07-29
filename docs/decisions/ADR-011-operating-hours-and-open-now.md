# ADR-011: Structured Operating Hours and Open-Now Calculation

## Status

Accepted — 2026-07-29

## Context

Milestone 6B adds a weekly operating-hours schedule per resource, a
server-authoritative "is this open right now" calculation, and two new
public filters (`costType`, `verificationStatus`, already-existing enums)
plus a third (`openNow`) that depends on the hours schedule and the current
Halifax date/time. Several decisions need making before writing code:

- What timezone governs "now," given the server, the database, and any
  future visitor could all disagree.
- How to represent an overnight interval (e.g. 22:00–02:00) without
  inventing an ambiguous convention.
- How to filter a paginated list by "open right now" without either
  breaking pagination correctness (filtering after the page is already cut)
  or reaching for infrastructure this project's actual scale doesn't need.
- How to avoid N+1 queries once every resource in a list needs its own
  weekly schedule evaluated.
- How to extend `ResourceRepository.search` (ADR-010) with two more
  optional filters without letting the query become unmaintainable.

## Decision

### Timezone: `America/Halifax`, Fixed, Server-Authoritative

HFX Connect serves Halifax exclusively; there is no multi-region or
per-user timezone concept anywhere else in the product. `OpenNowCalculator`
converts every calculation through `ZoneId.of("America/Halifax")`, never
the server's default zone, the database session zone, or a browser's local
zone. This single constant is the one source of truth — a resource's
schedule is stored as plain local times (`opens_at`/`closes_at`, no zone
component, matching "this is what the sign on the door says," not a UTC
instant), and every comparison against "now" converts the calculator's
`Clock`-derived instant into Halifax local time before comparing.
`America/Halifax` (not a fixed `-04:00`/`-03:00` offset) is required
specifically because Halifax observes daylight saving time — a fixed offset
would silently be wrong for half the year.

### `Clock` Injected, Never `Instant.now()`/`LocalTime.now()` Directly

`OpenNowCalculator` takes a `java.time.Clock` constructor dependency. A
`@Bean Clock` (system UTC) is configured once, in
`com.hfxconnect.common.config.ClockConfig`, and is the only place
`Clock.systemUTC()` is called in the whole codebase. Every test that needs
deterministic "current time" behavior (ordinary hours, boundary conditions,
overnight rollover, daylight-saving transitions) supplies its own
`Clock.fixed(...)`, rather than depending on whatever moment the test suite
happens to run. This is the same reasoning this project already applies to
other non-deterministic dependencies (`SecureRandom` for refresh tokens,
`Instant.now()` for entity timestamps) — a production concern (get the
right answer using real time) and a testing concern (get a *repeatable*
answer) are kept separate.

### Overnight Intervals: `opensAt > closesAt` Means "Crosses Midnight"

An entry where `opensAt` is later in the day than `closesAt` (e.g.
`22:00`–`02:00`) is interpreted as: open starting at `opensAt` on the
listed day, continuing past midnight, until `closesAt` on the *next*
calendar day. This needs no extra field — the ordering of the two times
already encodes it unambiguously, and it matches how a human reads "10 PM
to 2 AM" on a sign. `opensAt == closesAt` is rejected as invalid input
(see "Schedule Validation" below) rather than silently treated as a
24-hour day — an equal-times convention is exactly the kind of implicit,
surprising rule this project's own documentation style argues against;
if a genuine 24-hour-service need arises later, it deserves an explicit,
named representation, not an overloaded coincidence of two equal values.

### Status Model: `UNKNOWN` Only When the Whole Schedule Is Absent

A resource with **zero** `resource_operating_hours` rows has
`hoursStatus = UNKNOWN` and `openNow = null` — there is genuinely nothing
to calculate from. A resource with **at least one** row (even a partial
week — missing days are allowed, per "Schedule Rules" below) is always
resolved to either `OPEN` or `CLOSED`: a day with no row for "today" (and
no overnight continuation from yesterday) contributes to `CLOSED`, not
`UNKNOWN`, because the resource *does* have real schedule data — the
absence is about that specific day, not about the resource as a whole.
This is a deliberate, single dividing line (empty vs. non-empty schedule)
rather than a per-day `UNKNOWN`, because a per-day `UNKNOWN` would make the
public `openNow=true` filter's semantics ambiguous (does "unknown for
today" count as a candidate or not?) — with the chosen rule, `openNow=true`
has one, exact meaning: "the current calculated status is `OPEN`," and
resources with `UNKNOWN` overall are correctly and simply excluded.

### Open-Now Filtering: One Extended JPQL Query, Not a New Query Layer

`ResourceRepository.search` (introduced in Milestone 6A/ADR-010) gains two
more `(:param IS NULL OR ...)` predicates for `costType`/`verificationStatus`
(trivial equality checks) and one `EXISTS` subquery for `openNow`, computed
entirely from bind parameters the service layer derives once per request
from the injected `Clock` (today's `DayOfWeek`, yesterday's `DayOfWeek`,
and the current Halifax `LocalTime`) — never string-concatenated, and
never dependent on the database's own idea of "now." The `EXISTS` subquery
encodes the exact same same-day/overnight/overnight-continuation logic
`OpenNowCalculator` uses for a single resource, expressed once against the
`resource_operating_hours` table, so the *count* query and the *page*
query see identical filtering — pagination totals stay exact, satisfying
the explicit requirement that open-now filtering never happen after a page
has already been cut.

This was chosen over two alternatives:

- **A native PostgreSQL query** — considered, since open-now depends on
  date/time arithmetic. Rejected: JPQL's ordinary comparison operators
  already handle `LocalTime`/enum comparisons and `EXISTS` subqueries
  correctly with no PostgreSQL-specific function needed, so a native query
  would only add SQL-dialect coupling for no real benefit.
- **Migrating to Spring Data `Specification`s** — considered, given the
  query is accumulating parameters (six optional filters now). Deferred:
  `Specification`-based queries combined with a to-one `JOIN FETCH` and
  `Pageable` have real, documented history of count-query/fetch
  interaction issues across Spring Data versions, and this project has no
  verified-safe precedent for that combination yet. The single `@Query`
  approach remains the simpler, already-proven option for this milestone's
  scope; if a future filter makes the query genuinely unmanageable,
  `Specification`s (or moving to a dedicated read-model/projection) is the
  documented next step, not something to adopt speculatively now.

### Batch-Loading Operating Hours to Avoid N+1

The public resource list first runs the (already paginated, already
filtered) resource query, then issues exactly one additional query —
`ResourceOperatingHoursRepository.findByResourceIdIn(pageResourceIds)` —
for every resource on that one page, groups the results in memory by
resource ID, and calls `OpenNowCalculator` once per resource using a
single Halifax "now" computed once for the whole request (not
re-computed per resource, so every resource on a page is evaluated against
the exact same instant). This is the standard "N+1 avoided via one batched
follow-up query" pattern, chosen over a collection `JOIN FETCH` specifically
because a resource can have up to 7 hours rows — a collection fetch join
combined with `Pageable` is the one case ADR-010's own `CommunityResource
.category` fetch-join note already flags as unsafe (a to-many fetch join
multiplies result rows, corrupting pagination), so it was never
considered for this one-to-many relationship.

## Schedule Data Model

`V6__create_resource_operating_hours.sql` — `resource_operating_hours`:
`id` (`BIGINT GENERATED ALWAYS AS IDENTITY` — these rows are never
independently addressable in a URL or public API, unlike `resources`/
`users`, so the same reasoning that gave `categories` a simple identity
column applies here), `resource_id` (`UUID`, `REFERENCES resources(id) ON
DELETE CASCADE` — an operating-hours row has no meaning independent of the
resource it describes, the same reasoning `refresh_sessions.user_id`
already established for cascading on its owning row's deletion),
`day_of_week` (`VARCHAR`, checked against the seven `java.time.DayOfWeek`
names — the entity field's Java type is `java.time.DayOfWeek` itself via
`@Enumerated(STRING)`, not a bespoke enum, since the standard library type
already is exactly "a clear enum matching `DayOfWeek`"), `opens_at`/
`closes_at` (`TIME`, nullable), `closed` (`BOOLEAN NOT NULL DEFAULT
FALSE`), `created_at`/`updated_at`. A `UNIQUE (resource_id, day_of_week)`
constraint and a `CHECK` constraint enforcing "closed days have no times;
open days have both times, and they are not equal" are both expressed at
the database level, not only in application code — consistent with this
project's established "database constraints are authoritative, application
checks are for better error messages" convention.

## Schedule Write Contract

`PUT /api/v1/resources/{id}/operating-hours`, `ADMIN` or `MODERATOR` (the
same authorization pairing `POST /api/v1/resources` already uses — a
resource's hours are exactly as much an editorial-content concern as the
resource itself). A focused, resource-scoped endpoint was chosen over
extending `POST /api/v1/resources` to accept an optional schedule inline,
per the milestone brief's own "a focused endpoint is often cleaner"
guidance: resource creation and schedule management are genuinely separate
concerns with separate validation rules, and keeping them separate avoids
growing `ResourceCreateRequest` with a second, independently-validated
sub-object. The endpoint replaces the *entire* weekly schedule
transactionally (delete-then-insert, inside one `@Transactional` method) —
no partial `PATCH` semantics, since a caller replacing "Monday's hours"
without seeing the rest of the week risks silently leaving stale data for
every other day.

`opensAt`/`closesAt` serialize as ISO local-time strings *with seconds*
(`"09:00:00"`, not `"09:00"`) — confirmed against a running backend
(`ResourceApiIntegrationTest#operatingHoursTimesSerializeAsIsoLocalTimeWithSeconds`):
Jackson's default JSR-310 `LocalTime` serializer always formats via
`DateTimeFormatter.ISO_LOCAL_TIME`, which is not the same as
`LocalTime#toString()`'s zero-seconds-omitted form. The frontend's Zod
schemas and `formatLocalTime` display helper are written against this
verified wire format, not assumed from `LocalTime#toString()`.

**Error codes.** `costType`/`verificationStatus`/`openNow` query-param
failures get their own codes (`INVALID_COST_TYPE`,
`INVALID_VERIFICATION_STATUS`, `INVALID_OPEN_NOW_FILTER`), matching the
existing `INVALID_SORT`/`INVALID_SEARCH_QUERY` precedent for a single bad
query parameter. Operating-hours *body* validation deliberately reuses the
existing `ValidationException`/`VALIDATION_ERROR` shape (with
`fieldErrors`) rather than inventing `INVALID_OPERATING_HOURS`/
`DUPLICATE_OPERATING_DAY` codes — every violation (missing day, duplicate
day, closed-with-times, missing time, equal times, oversized list, null
entry) is already expressible as a named field error in that existing
shape, and every other request-body validation failure in this codebase
already uses it; a second body-validation error shape would be
inconsistency without a corresponding benefit.

## Alternatives Considered

- **Treating `opensAt == closesAt` as a 24-hour day.** Rejected — see
  "Overnight Intervals" above.
- **A per-day `UNKNOWN` status instead of a whole-resource one.** Rejected
  — see "Status Model" above; it would make the `openNow=true` filter's
  meaning ambiguous.
- **Filtering `openNow` in the service layer after fetching a page.**
  Rejected outright — explicitly disallowed by the milestone brief, and for
  a correct reason: it would make `totalElements`/`totalPages` wrong the
  moment any candidate on the fetched page turned out not to be open.
- **A collection `JOIN FETCH` for operating hours.** Rejected — see
  "Batch-Loading" above; a to-many fetch join multiplies result rows and
  breaks pagination, the exact failure mode this project already documented
  once for `CommunityResource.category` (a to-one relationship, where the
  same risk does not apply).
- **Next-opening-time calculation.** Considered and deferred — the
  milestone brief permits this only "if cleanly supportable," and a correct
  implementation (scanning forward across explicitly-closed days, missing
  days, and multiple overnight boundaries) is meaningfully more complex
  than the current OPEN/CLOSED/UNKNOWN status this milestone actually
  needs. Recorded as a known limitation, not implemented speculatively.

## Consequences

- Every authenticated schedule write and every public list/detail read that
  touches hours goes through `OpenNowCalculator`'s single, tested
  same-day/overnight/overnight-continuation algorithm — there is exactly
  one place this logic exists, not one implementation on the filter path
  and a second, potentially-divergent one on the display path.
- A future milestone adding a second timezone (unlikely for this product,
  but a good test of the design) would need to move the timezone from a
  fixed constant to a per-resource or per-region column — this ADR's
  design deliberately does not build that flexibility in early, since
  nothing in this product's actual scope needs it yet.
- The extended `ResourceRepository.search` query is now the single most
  complex query in the codebase; this ADR's "Alternatives Considered"
  section is the documented trigger for when to revisit that (a
  `Specification`-based rewrite, or a dedicated read-model), rather than
  leaving that decision to be rediscovered from scratch later.

## Honest Limitations Not Solved By This ADR

- No holiday exceptions, seasonal schedules, or date-specific overrides —
  the schedule is a single, ongoing weekly pattern only.
- No more than one interval per day (no "9–12, 1–5" split shifts).
- No next-opening-time calculation.
- No per-resource or per-visitor timezone — `America/Halifax` is fixed for
  every resource, matching this product's actual single-city scope.
- No public UI for editing hours — only the `ADMIN`/`MODERATOR`-only API
  endpoint exists; there is no frontend form for it in this milestone.
