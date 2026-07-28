# Milestone 6A: Keyword Search and Public Resource Filtering

## Objective

Add production-quality keyword search (`q`) to the public
`GET /api/v1/resources` endpoint and the `/resources` frontend page, on top
of the existing category filter, allowlisted sort, and pagination — while
keeping the backend the sole authoritative source for filtering, never
filtering the full dataset only in the browser.

## Product Value

The first slice of Milestone 6 (Search and Filtering), split into 6A
(keyword search — this milestone) and 6B (structured operating hours/
open-now logic — future), the same way Milestone 3 split into 3A/3B/3C and
Milestone 5 split into 5A/5B/5C. Before this milestone, the only way to
narrow the public resource list was the category dropdown; a visitor who
knows roughly what they're looking for (a name, a neighbourhood, a kind of
service mentioned in a description) had no way to search for it directly.

## Technical Scope

**Backend:**

- `GET /api/v1/resources` gains an optional `q` query parameter.
- `com.hfxconnect.resource.ResourceSearchQuery` — normalizes `q` (trim,
  collapse whitespace, strip control characters, 100-character maximum) and
  builds the safe, wildcard-escaped `LIKE` pattern used for matching.
- `com.hfxconnect.common.error.InvalidSearchQueryException` — `400
  INVALID_SEARCH_QUERY` for an over-length query.
- `ResourceRepository.search` — a single parameterized JPQL query replacing
  the previous `findByActiveWithCategory`/`findByCategoryIdAndActiveWithCategory`
  pair, with `categoryId` and the keyword pattern both independently
  optional. Searches `name`, `description`, `addressLine1`, `city`.
- `ResourceService.search` — replaces `listActive`/`listActiveByCategory`
  with one method covering every combination of category/keyword filtering.
- `ResourceController` — the `list` handler now accepts and documents `q`.
- OpenAPI: `q` documented on `GET /api/v1/resources` with its full
  normalization/matching behavior.
- [ADR-010](../decisions/ADR-010-keyword-search-design.md) — the full design:
  why one unified query rather than one method per filter combination, the
  searchable-field choice, the wildcard-escaping mechanism, and the
  deliberate no-`pg_trgm`-index-yet decision.
- 40 new backend tests (see Testing below), bringing the suite to 335 total.
- No new Flyway migration — no schema change was needed.

**Frontend:**

- `lib/query/resource-list-params.ts` — `q` added to `ResourceListParams`,
  parsed with the same trim/collapse/blank-becomes-undefined normalization
  as the backend (best-effort; the backend independently re-validates).
- `lib/query/keys.ts` — `q` added to the resource-list query key.
- `lib/api/resources.ts` — `q` added to `GetResourcesParams`, omitted from
  the request entirely when blank.
- `components/resources/resource-filter-form.tsx` — a labelled `q` search
  field added to the existing GET form (submits alongside category/sort,
  resets to page 1, works with JavaScript disabled), plus a "Clear search"
  action.
- `components/resources/resource-list-view.tsx` — an honest, keyword-aware
  result summary and distinct no-results messaging for keyword-only,
  category-only, and combined keyword+category cases — never a relevance
  claim.
- `components/resources/pagination.tsx` — preserves `q` across page links.
- 23 new frontend tests, bringing the suite to 143 total.

## Out of Scope

Cost-type/verification-status filtering, structured operating hours,
open-now logic, geospatial/distance search, maps, autocomplete, typo
correction, semantic/AI search, saved resources, submissions, moderation,
organization ownership, events, CI/CD, deployment, Milestone 6B, Milestone 7.

## Design Decisions

Full rationale: [ADR-010](../decisions/ADR-010-keyword-search-design.md).
Summary:

- **One unified `search` query/method**, not one method per filter
  combination — `categoryId` and the keyword pattern are both independently
  optional `(:param IS NULL OR ...)` predicates in a single JPQL query,
  replacing the two-method pair a second independent optional filter would
  otherwise have multiplied into four.
- **Searches `name`, `description`, `addressLine1`, `city` only** —
  province/postal code (structured filters, not keyword targets),
  category name (already covered by `categoryId`), and contact fields
  (phone/email/URL — false-positive-prone) are deliberately excluded.
- **Case-insensitive substring match, no relevance ranking.** Results stay
  sorted by the existing `name`/`createdAt` order; this is stated
  explicitly (not left implicit) because sorting and calling results "most
  relevant" would misrepresent what the search actually does.
- **`%` and `_` are escaped to literal characters**, not left as
  functioning `LIKE` wildcards — a search for `50%` or `user_name` matches
  those characters literally, verified live against a deliberate
  false-positive "trap" resource during manual testing (see Manual
  Verification below).
- **No `pg_trgm`/GIN index added.** A `LIKE '%term%'` sequential scan is
  fast enough for this project's actual MVP dataset size; adding a trigram
  index now would be optimizing a cost that doesn't exist yet. Documented as
  a deliberate, revisitable decision, not an oversight.
- **Frontend search is submit-based, not per-keystroke.** No API request
  fires until the form is submitted (Enter or the "Apply" button) —
  consistent with the existing category/sort filter form's progressive-
  enhancement design (a real `method="get"` form, works without JavaScript).

## Security Considerations

- `q` is never concatenated into a query string — `ResourceService` builds
  an already-escaped, already-lowercased `LIKE` pattern in Java and passes
  it as a single bound JPQL parameter; the query text itself (including its
  `ESCAPE '\'` clause) is a fixed literal, never user-controlled.
- `git grep` for `like`/`ilike`/`nativeQuery`/`entityManager`/`createQuery`
  across `backend/src/main` reviewed directly — the only `LIKE` usage is the
  one parameterized query this milestone added; no raw SQL, no native
  query, no direct `EntityManager` usage.
- The search phrase is rendered as plain React text (JSX interpolation,
  never `dangerouslySetInnerHTML`) everywhere it's echoed back to the user
  (the result summary, the no-results heading) — verified live: HTML
  entities in the rendered page confirm React's own escaping is active.
- No access token or other secret enters the URL; `q` is public, unlogged
  request data, same as `categoryId`/`sort`/`page` already were.
- Backend log output reviewed directly for the raw search phrases used
  during manual verification — none appear (no query-content logging was
  added).

## Acceptance Criteria

**Backend search**

- [x] `GET /api/v1/resources` accepts optional `q`.
- [x] Blank/whitespace-only `q` behaves as no keyword filter.
- [x] Query text is normalized (trim, whitespace collapse, control-character
      stripping).
- [x] An over-100-character normalized query returns `400
      INVALID_SEARCH_QUERY`.
- [x] Search is case-insensitive.
- [x] `%`/`_` are treated as literal characters, verified with a deliberate
      false-positive "trap" resource, not just asserted.
- [x] SQL/JPQL parameters are bound; no raw concatenation.
- [x] Active-only visibility, category filter, sorting, and pagination all
      remain correct in combination with `q`.
- [x] No match returns an empty `200` page, never `404`.
- [x] OpenAPI documents `q`; public `GET` access is unaffected.

**Frontend search**

- [x] The search input is labelled and keyboard-submittable.
- [x] `q` appears in the URL and is restorable/shareable.
- [x] Submitting a new search resets to page 1 while preserving
      category/sort.
- [x] "Clear search" removes `q` while preserving other filters; "Reset
      filters" clears everything.
- [x] The result summary and no-results messaging are keyword-aware and
      honest (no relevance claim).
- [x] Search works without authentication and without JavaScript (progressive
      enhancement).

**Testing**

- [x] 335 backend tests pass (295 inherited + 40 new) — authoritative per
      `./mvnw clean verify`.
- [x] 143 frontend tests pass (120 inherited + 23 new) — authoritative per
      `npm test`.

**Manual verification** — all performed against the real docker-compose
database and real running frontend/backend:

- [x] Search by name, description, address line, and city, each confirmed
      live via `curl` against real created resources.
- [x] Case-insensitivity, whitespace normalization, and the `%`/`_`-as-
      literal-character behavior each confirmed live, including a
      deliberate false-positive "trap" resource proving the underscore
      case specifically.
- [x] Keyword + category combination, keyword + sort, and no-results (`200`,
      empty content) all confirmed live.
- [x] Over-length query confirmed to return `400 INVALID_SEARCH_QUERY` live.
- [x] OpenAPI document confirmed to declare `q` with its full description.
- [x] Frontend `/resources` page confirmed (via rendered HTML) to show the
      labelled search field, a keyword-aware result summary, and a
      keyword-specific no-results message — with the search phrase visibly
      HTML-escaped, not raw-injected.
- [x] Milestone 4 (homepage, resource detail) and Milestone 5C
      (login/dashboard, role-based write authorization) all reconfirmed
      unaffected.
- [x] Backend log output grepped for the raw search phrases used during
      verification — none found.

## Known Limitations (as of Milestone 6A)

- No typo tolerance, fuzzy matching, or relevance ranking.
- No multi-word AND/OR token semantics — the whole normalized phrase is
  matched as one substring.
- No full-text search, `pg_trgm`, or GIN index — an accepted, documented
  scaling limitation for this project's current dataset size, not a current
  problem (see ADR-010).
- No cost-type or verification-status filter.
- No structured operating hours or open-now logic (Milestone 6B).
- No location/distance filter or maps (Milestone 7).
- No search analytics or query logging.
- Frontend verification remained code-review- and `curl`-based — no
  browser-automation tool was available in this development environment.

## Risks

| Risk | Mitigation |
|---|---|
| Consolidating two repository/service methods into one could silently change existing category-filter/pagination/sort behavior | The full pre-existing `ResourceServiceIntegrationTest`/`ResourceApiIntegrationTest` suites (unchanged in intent, only mechanically updated for the new method signature) were run and confirmed passing before any new search-specific tests were added |
| `%`/`_` silently acting as unintended wildcards would be a real, user-facing correctness bug (and confusing security surprise) | Verified with a dedicated false-positive "trap" resource in both automated tests and live manual `curl` verification — not just asserted from the escaping logic's design |
| A test bug (search phrase not actually contained as a contiguous substring in the seeded resource name) could produce a false test failure that looks like an implementation bug | Caught during test authoring itself (two such bugs were found and fixed in this milestone's own test suite before considering it complete) — a good example of why the test's own assertions were re-verified against real query behavior, not assumed correct on first write |

## Completion Summary

All planned Milestone 6A deliverables were completed and verified three
ways: 335 automated backend tests (including a dedicated false-positive
wildcard-literal proof), 143 automated frontend tests, and a full manual
pass against the real running backend/frontend/database — including a live
reproduction of the wildcard-escaping guarantee with a deliberately crafted
trap resource, and a full regression pass confirming Milestone 4's public
browsing and Milestone 5C's authentication/authorization remain unaffected.
