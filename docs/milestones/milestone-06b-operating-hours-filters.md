# Milestone 6B: Structured Operating Hours, Open-Now Logic, and Cost/Verification Filters

## Objective

Complete the public filtering stage (started in 6A) by adding a structured
weekly operating-hours schedule per resource, a server-authoritative
"is this open right now" calculation, and public `costType`/
`verificationStatus`/`openNow` filters on `GET /api/v1/resources` — combined
freely with the existing `q`/`categoryId`/`sort`/pagination — while the
backend remains the sole authority for filtering, open-now calculation,
active-resource visibility, pagination, sorting, and schedule
interpretation.

## Product Value

The second and final slice of Milestone 6 (Search and Filtering), after 6A's
keyword search. Before this milestone, a visitor had no way to know whether
a resource was actually open, or to narrow results to free/verified/
currently-open resources — exactly the kind of practical filter someone
searching for a food bank or shelter at 9pm needs.

## Technical Scope

**Backend:**

- `V6__create_resource_operating_hours.sql` — the `resource_operating_hours`
  table (one row per resource/day-of-week), with `UNIQUE(resource_id,
  day_of_week)` and a `CHECK` constraint enforcing "closed days have no
  times; open days have both, and they differ."
- `com.hfxconnect.resource.ResourceOperatingHours` — JPA entity
  (`java.time.DayOfWeek` via `@Enumerated(STRING)`, no bespoke enum).
- `com.hfxconnect.resource.ResourceOperatingHoursRepository` —
  `findByResourceId`, the batch `findByResourceIdIn` (N+1 avoidance),
  `deleteByResourceId`.
- `com.hfxconnect.resource.OperatingHoursEntry` — business-layer read model.
- `com.hfxconnect.resource.OpenNowCalculator` — the same-day/overnight/
  overnight-continuation algorithm, `Clock`-injected for determinism.
- `com.hfxconnect.common.config.ClockConfig` — the one production
  `Clock.systemUTC()` bean.
- `com.hfxconnect.resource.OperatingHoursValidation` — schedule body
  validation (duplicate day, closed-with-times, missing time, equal times,
  >7 entries, null entries), reusing the existing `ValidationException`
  shape.
- Request/response DTOs: `OperatingHoursEntryRequest`,
  `ReplaceOperatingHoursRequest`, `OperatingHoursEntryResponse`,
  `OperatingHoursResponse`, `HoursStatus`.
- `PUT /api/v1/resources/{id}/operating-hours` — `ADMIN`/`MODERATOR` only,
  full-replace, transactional.
- `ResourceRepository.search` extended with `costType`,
  `verificationStatus`, and a correlated `EXISTS` subquery for `openNow` —
  one query, bind parameters only.
- `ResourceService` — batch-loads operating hours for a page
  (`findByResourceIdIn`, grouped in memory), computes a single Halifax
  "now" per request, evaluates every resource on that page against it.
- `ResourceResponse`/`ResourceSummaryResponse` extended with hours/status
  fields.
- `com.hfxconnect.common.error.{InvalidCostTypeException,
  InvalidVerificationStatusException, InvalidOpenNowFilterException}`.
- `SecurityConfig` — `PUT /api/v1/resources/*/operating-hours` gated
  `ADMIN`/`MODERATOR`.
- OpenAPI: `costType`/`verificationStatus`/`openNow` documented on the list
  endpoint; the operating-hours endpoint fully documented.
- [ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md) — the
  full design.
- 74 new backend tests, bringing the suite to 409 total.

**Frontend:**

- `lib/validation/schemas.ts` — `hoursStatusSchema`, `dayOfWeekSchema`,
  `operatingHoursEntrySchema`, `operatingHoursSchema`; `hoursStatus`/
  `openNow` added to `resourceSummaryResponseSchema`; `hours` added to
  `resourceResponseSchema`.
- `lib/constants/resources.ts` — `COST_TYPE_FILTER_OPTIONS`,
  `VERIFICATION_STATUS_FILTER_OPTIONS`, `WEEKLY_DAY_ORDER`.
- `lib/api/resources.ts` — `costType`/`verificationStatus`/`openNow` added
  to `GetResourcesParams`.
- `lib/query/resource-list-params.ts`/`keys.ts` — the three new filters
  parsed with safe fallbacks and added to the resource-list query key.
- `lib/formatting/labels.ts` — `hoursStatusLabel`, `dayOfWeekLabel`,
  `formatLocalTime`.
- `components/resources/status-badges.tsx` — `HoursStatusBadge`.
- `components/resources/resource-filter-form.tsx` — labelled cost/
  verification selects, an "Open now" checkbox, and a "Reset all filters"
  link — all progressive-enhancement, all reset pagination.
- `components/resources/resource-card.tsx` — open-status badge.
- `components/resources/resource-detail.tsx` — a "Hours" section: current
  status badge, weekly schedule Monday-Sunday, closed vs. unavailable days
  distinguished, overnight intervals marked.
- `components/resources/pagination.tsx` — preserves the new filters across
  page links.
- 34 new frontend tests, bringing the suite to 177 total.

## Out of Scope

Holiday exceptions, seasonal schedules, multiple intervals/day,
appointment-only scheduling, next-opening-time prediction, per-resource
timezone, public hours-editing UI, geospatial/distance filtering, maps,
typo tolerance/relevance ranking, cost/verification multi-select, saved
resources, submissions, moderation, organization ownership, events,
CI/CD, deployment, Milestone 7.

## Design Decisions

Full rationale: [ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md).
Summary:

- **`America/Halifax`, fixed, server-authoritative** — never server-default,
  browser, or raw UTC. Halifax observes DST, so a fixed offset would be
  wrong for half the year.
- **`Clock` injected everywhere "now" drives a calculation** — one
  production `Clock.systemUTC()` bean; tests use `Clock.fixed(...)`.
- **`opensAt > closesAt` means "crosses midnight"** — no extra column;
  `opensAt == closesAt` is rejected as invalid rather than treated as an
  implicit 24-hour day.
- **`UNKNOWN` only when the whole schedule is absent** — a resource with
  any schedule data always resolves `OPEN`/`CLOSED`, even for a day with no
  entry, so `openNow=true` has one unambiguous meaning.
- **One extended JPQL query, not a new query layer** — `costType`/
  `verificationStatus` as `(:param IS NULL OR ...)` predicates,
  `openNow` as a correlated `EXISTS` subquery, both the page query and the
  count query — pagination totals stay exact.
- **Batch-loading, not a collection fetch-join** — one additional
  `findByResourceIdIn` query per page, grouped in memory, evaluated
  against one Halifax "now" for the whole page.
- **A focused `PUT .../operating-hours` endpoint**, not an inline field on
  resource creation — full-replace, transactional, `ADMIN`/`MODERATOR`.
- **Reuses `VALIDATION_ERROR`** for schedule-body validation rather than
  inventing new codes — every violation is already expressible as a named
  field error, and every other body-validation failure in this codebase
  already uses this shape.

## Security Considerations

- Public filters (`costType`/`verificationStatus`/`openNow`) remain fully
  public — no authentication required, same as `q`/`categoryId`.
- Operating-hours writes require a Bearer access token for an `ADMIN` or
  `MODERATOR` account — confirmed with a full role matrix (unauthenticated
  `401`, `USER`/`ORGANIZATION` `403`, `MODERATOR`/`ADMIN` `200`).
- `costType`/`verificationStatus` are validated against the real enum
  values (`CostType.valueOf`/`VerificationStatus.valueOf`) — never
  interpolated into query text; an invalid value is rejected with `400`
  before any query runs.
- `openNow`'s `today`/`yesterday`/`now` bind parameters are computed
  server-side from the injected `Clock`, never derived from user input —
  the `EXISTS` subquery uses bound parameters exclusively, same discipline
  ADR-010 established for the keyword-search `LIKE` pattern.
- Inactive resources remain invisible through every new filter — the
  `active = true` predicate is unchanged and combines with every new
  predicate in the same query.
- No persistence ID is exposed from `resource_operating_hours` —
  `OperatingHoursEntryResponse` carries only `dayOfWeek`/`closed`/
  `opensAt`/`closesAt`/`overnight`.
- `git grep -n "Instant.now"`, `"LocalTime.now"`, `"ZonedDateTime.now"`,
  `-i "America/Halifax"`, `-i "nativeQuery"`, `-i "createQuery"` reviewed
  directly (including untracked new files) — every `Instant.now()` hit is
  an existing entity-timestamp stamp (`@PrePersist`/`@PreUpdate`),
  unrelated to time-of-day business logic; `America/Halifax` appears
  exactly once as the actual `ZoneId` constant, everywhere else is
  documentation/schema text; no native or `EntityManager` query usage
  anywhere in this milestone's code.
- `git grep -i "localStorage"`/`"sessionStorage"` across `frontend/src`
  reviewed — both hits are the pre-existing auth-provider doc/test, nothing
  new from this milestone.

## Performance Review

- List requests: one paginated/filtered resource query (with `JOIN FETCH
  category`), one `count` query (Spring Data's own `Page` machinery), and
  one batch `findByResourceIdIn` query for operating hours — three queries
  total per page, not N+1, regardless of page size.
- `resource_operating_hours_resource_id_idx` supports both the
  single-resource and batch lookups.
- The `openNow` `EXISTS` subquery adds a correlated join per candidate row;
  no benchmark was run against this project's actual (small, MVP-scale)
  dataset, and none is claimed — the design avoids the one correctness-
  breaking anti-pattern (post-pagination filtering) rather than optimizing
  a scale this project doesn't have yet.
- No additional indexes were added speculatively beyond
  `resource_operating_hours_resource_id_idx`, which is required (not
  optional) for the batch-load query.

## Acceptance Criteria

**Database**

- [x] `V6` creates `resource_operating_hours` with the documented
      columns/constraints; earlier migrations unmodified.
- [x] `UNIQUE(resource_id, day_of_week)` and the closed/open-times `CHECK`
      constraint both enforced at the database level.
- [x] `ON DELETE CASCADE` verified live (deleting a resource removes its
      hours rows).

**Operating hours**

- [x] Same-day, overnight, and overnight-continuation-from-yesterday
      intervals all calculate correctly, verified with fixed-`Clock` unit
      tests covering every boundary (before/at/after opening and closing)
      and DST (standard-time and daylight-time cases).
- [x] A resource with no schedule is `UNKNOWN` (`openNow: null`); a
      resource with any schedule always resolves `OPEN`/`CLOSED`.
- [x] `PUT .../operating-hours` fully replaces the schedule, transactional,
      `ADMIN`/`MODERATOR` only; rejects duplicate days, closed-with-times,
      missing times, equal times, and >7 entries with `400
      VALIDATION_ERROR`.

**Filtering**

- [x] `costType`/`verificationStatus`/`openNow` all independently optional,
      combine correctly with `q`/`categoryId`/`sort`/pagination in every
      combination.
- [x] Invalid `costType`/`verificationStatus`/`openNow` values return their
      own `400` codes.
- [x] `openNow=true` never returns a resource with `UNKNOWN` hours.
- [x] Pagination totals stay exact with `openNow=true` applied — filtering
      happens in the database query, never after a page is fetched.

**Frontend**

- [x] Labelled cost/verification selects and an "Open now" checkbox, all
      progressive-enhancement (`method="get"` form), all reset pagination.
- [x] Invalid filter values in a hand-edited URL fall back safely, never
      shown as active if the backend wouldn't have received them.
- [x] Resource cards show a readable open-status label; the detail page
      shows the full weekly schedule, Monday-Sunday, with closed vs.
      unavailable days clearly distinguished and overnight intervals
      marked.
- [x] The frontend never calculates open-now itself — every status shown
      is the backend's own `hoursStatus`/`openNow`.

**Testing**

- [x] 409 backend tests pass (335 inherited + 74 new) — authoritative per
      `./mvnw clean verify`.
- [x] 177 frontend tests pass (143 inherited + 34 new) — authoritative per
      `npm test`.

**Manual verification** — all performed against the real docker-compose
database and real running frontend/backend:

- [x] A freshly created resource confirmed `UNKNOWN`/`null` live.
- [x] A schedule set to open "now" (computed from the real current Halifax
      time) confirmed `OPEN`/`true` live, proving the Halifax conversion
      against a real clock, not just a fixed-`Clock` test.
- [x] `costType`, `verificationStatus`, `openNow`, and all-filters-combined
      confirmed live via `curl`, including invalid-value `400`s.
- [x] `PUT .../operating-hours` confirmed live as `ADMIN`, and confirmed
      `401` unauthenticated.
- [x] A closed day and an overnight interval (22:00-02:00) both confirmed
      live in the rendered `/resources/[slug]` HTML, including the
      12-hour-formatted times and the "continues past midnight" note.
- [x] OpenAPI document confirmed to declare `costType`/`verificationStatus`/
      `openNow` and the operating-hours endpoint.
- [x] Milestone 6A keyword search, Milestone 5C authentication/
      authorization, and Milestone 4 public browsing all reconfirmed
      unaffected.

## Known Limitations (as of Milestone 6B)

- One interval per day — no split shifts ("9-12, 1-5").
- No holiday exceptions or seasonal schedules — a single, ongoing weekly
  pattern only.
- No appointment-only scheduling model.
- No next-opening-time calculation — considered and deferred (see ADR-011)
  as meaningfully more complex than the OPEN/CLOSED/UNKNOWN status this
  milestone actually needs.
- No per-resource or per-visitor timezone — `America/Halifax` is fixed,
  matching this product's single-city scope.
- No public UI for editing hours — only the `ADMIN`/`MODERATOR` API
  endpoint exists.
- No geospatial/distance filtering or maps (Milestone 7).
- No typo tolerance or relevance ranking (unchanged from 6A).
- No cost-type or verification-status multi-select — one value at a time.
- Frontend verification remained code-review-, automated-test-, and
  `curl`/rendered-HTML-based — no browser-automation tool was available in
  this development environment, so live mouse/keyboard/screen-reader
  interaction was not directly observed (the automated accessibility-
  relevant assertions — labelled controls, keyboard-operable selects/
  checkbox via `userEvent`, semantic `<dl>`/`<dt>`/`<dd>` schedule markup —
  substitute for it, same as Milestone 6A).

## Risks

| Risk | Mitigation |
|---|---|
| An off-by-one or day-of-week-wraparound bug in the overnight-continuation logic would be a real, user-facing correctness bug (a resource reads as closed when it's actually open past midnight, or vice versa) | 14 fixed-`Clock` unit tests cover every boundary explicitly (before/at/after opening and closing, same-day and overnight, overnight-from-yesterday, Sunday-to-Monday wraparound, standard-time and DST), plus a live manual verification against the real current Halifax time |
| The `openNow` `EXISTS` subquery could silently diverge from `OpenNowCalculator`'s single-resource logic, giving the list and the detail page different answers for the same resource | Both express the identical same-day/overnight/overnight-continuation conditions from the same ADR-011 design; the batch-loaded service tests exercise both paths against the same seeded data |
| Filtering `openNow` after pagination would silently corrupt totals under real traffic in a way that's easy to miss in a small dev dataset | Verified directly: the `EXISTS` subquery is inside both the page query and the `countQuery`, and a dedicated test (`allFiltersCombinedNarrowToTheExactMatch`, plus the API-level combined-filter test) confirms `totalElements` reflects the filtered set, not the full one |

## Completion Summary

All planned Milestone 6B deliverables were completed and verified three
ways: 409 automated backend tests (including exhaustive fixed-`Clock`
open-now boundary/DST coverage and a full write-endpoint role matrix), 177
automated frontend tests, and a full manual pass against the real running
backend/frontend/database — including a live open-now calculation against
the real current Halifax time, a live closed-day and overnight-interval
render, and a full regression pass confirming Milestone 6A's keyword search
and Milestone 5C's authentication/authorization remain unaffected.
