# ADR-010: Keyword Search Design

## Status

Accepted — 2026-07-28

## Context

Milestone 6A adds a public keyword-search parameter (`q`) to `GET
/api/v1/resources`, on top of the existing `categoryId` filter, `sort`
allowlist, and pagination Milestone 3C already established. Three questions
need deciding before writing code:

- How to combine two independently-optional filters (`categoryId`, `q`)
  with the existing active-only visibility rule, without multiplying the
  service/repository surface into one method per combination.
- How to accept arbitrary user text into a `LIKE`-style database query
  safely — `%` and `_` are pattern metacharacters, and this project's own
  Milestone 3A/3B history (`SlugGenerator`, `ResourceValidation`) already
  established a strong "never trust user input into a query without a
  documented safety story" precedent.
- Whether this milestone's expected dataset size justifies a `pg_trgm`/GIN
  index investment now, or whether that's premature.

## Decision

### One Unified Query, Not One Method Per Filter Combination

Before this milestone, `ResourceService` had two public listing methods —
`listActive(page, size, sort)` and `listActiveByCategory(categoryId, page,
size, sort)` — backed by two repository queries
(`findByActiveWithCategory`/`findByCategoryIdAndActiveWithCategory`). Adding
keyword search as a second independent optional filter would have meant
either four method combinations (list / listByCategory / search /
searchByCategory) or a growing combinatorial mess as more optional filters
arrive in future milestones (verification status, cost type, ...).

Both methods are replaced with one: `ResourceService.search(query,
categoryId, page, size, sort)`, backed by one repository query,
`ResourceRepository.search(categoryId, likePattern, pageable)`. Both
optional filters are expressed as `(:param IS NULL OR ...)` predicates in a
single JPQL query rather than as separate methods — the standard,
well-understood pattern for an optional-filter query, and the direct
"simplest maintainable option" the milestone brief asks for. This is a
genuine generalization triggered by a second real, independent optional
filter arriving — consistent with this project's own established practice
of extracting/generalizing on a second real need, not preemptively (see
`docs/architecture/backend-architecture.md`'s note on `SlugGenerator`'s and
`EmailNormalizer`'s extraction history, which is the same reasoning applied
here to a query shape instead of a utility function).

`findByActiveWithCategory`/`findByCategoryIdAndActiveWithCategory` are
removed outright (not deprecated in place) — they had no callers outside
`ResourceService` itself, so keeping them around after replacing their only
caller would be exactly the "no speculative/orphaned code" pattern this
project's own conventions already reject. The plain (non-fetch-join)
`findByActive`/`findByCategoryIdAndActive` derived-query methods are
untouched — they exist only to exercise Spring Data's basic query derivation
directly in `ResourceRepositoryIntegrationTest`, independent of the public
listing path this ADR is about.

### Searchable Fields: Name, Description, Address Line 1, City

A keyword matches if it appears (case-insensitively, as a substring) in any
of: `name`, `description`, `addressLine1`, `city`. Deliberately excluded:

- **`province`/`postalCode`** — a two-letter code and a structured postal
  code are not naturally something a person searching by *keyword* types;
  the existing `categoryId` filter already covers structured filtering, and
  a real location filter is explicitly Milestone 7's (geospatial) concern.
- **`category.name`** — the existing `categoryId` parameter already lets a
  caller filter by category precisely; adding category name into the same
  substring-match clause would blur two already-distinct filter concepts
  (an exact category selection vs. a fuzzy text match) for marginal benefit,
  and would require joining a second field family into the same predicate
  group with no clear product need driving it yet.
- **`phone`/`email`/`websiteUrl`** — not meaningful "keyword" targets, and
  surfacing a resource because a search phrase happens to appear inside a
  URL or email address is far more likely to be a confusing false-positive
  than a useful match.

### Safety: Bound Parameters, Explicit `ESCAPE`, No Raw Concatenation

The `q` value is never concatenated into a query string. `ResourceService`
builds a `likePattern` — an already-lowercased, already-escaped,
`"%"`-wrapped string — entirely in Java, and passes it as a single bound
JPQL parameter (`:likePattern`). The query text itself
(`LOWER(r.name) LIKE :likePattern ESCAPE '\'`) is a fixed literal defined at
compile time; only the *value* of `:likePattern` varies per request, which
is exactly what parameter binding is for — this is not meaningfully
different, safety-wise, from every other `@Query` this project already
uses.

**Wildcard escaping:** PostgreSQL's `LIKE` treats `%` (any run of
characters) and `_` (any single character) as metacharacters. A search for
`50%` or `user_name` must match those characters *literally*, not as
wildcards a searcher never intended to use. `ResourceSearchQuery.toLikePattern`
escapes `\`, `%`, and `_` (in that order — the escape character itself must
be escaped first, or a user-typed `\` would silently become a
still-functional escape sequence for whatever follows it) before wrapping
the term in `%...%`, and the JPQL query declares `ESCAPE '\'` explicitly so
the database knows which character means "the next character is literal."
This is the standard, minimal-footprint way to support literal substring
search without either rejecting `%`/`_` outright or silently treating them
as wildcards a public search box was never documented to support.

### No Relevance Ranking

Results are returned in the same `name`-ascending or `createdAt`-descending
order the existing `sort` parameter already controls — a keyword match does
not change result order, and no relevance score is computed. This is stated
explicitly (not left implicit) because sorting by `name`/`createdAt` and
calling results "most relevant" would be a real, user-facing
misrepresentation the milestone brief explicitly warns against.

### Query Normalization, Not Slug Generation

`ResourceSearchQuery.normalize` is a new, dedicated utility — not a reuse of
`com.hfxconnect.common.text.SlugGenerator`. A slug is a lossy, one-way
transformation designed to produce a URL-safe identifier (lowercase,
hyphenated, alphanumeric-only); a search query needs to preserve the user's
actual words (including punctuation that might matter, like "St." vs "St")
for both matching and for safely echoing back in a "no results for ‘...’"
message. The two solve genuinely different problems, so — consistent with
`SlugGenerator`'s own extraction history (shared only once a second,
authentically identical need existed) — this is *not* a case where sharing
code would be appropriate.

Normalization: trim, collapse internal whitespace runs to a single space,
strip non-whitespace control characters, and enforce a 100-character maximum
on the *normalized* result (throwing `InvalidSearchQueryException` → `400
INVALID_SEARCH_QUERY` if exceeded). A blank or whitespace-only query
normalizes to `null` — "no keyword filter," the same as `q` being absent
entirely — rather than an error; a public search box that rejects an
accidental space bar press would be a poor, surprising experience for no
security or correctness benefit.

### No `pg_trgm`/GIN Index — Documented, Deliberate Scope Decision

A `LIKE '%term%'` predicate cannot use a standard B-tree index (the leading
wildcard defeats prefix-based index lookups), so this search performs a
sequential scan of active resources per request. For this project's actual
current and near-term dataset size — an MVP directory of Halifax community
resources, realistically dozens to low hundreds of rows, not millions — a
sequential scan is fast enough that adding `pg_trgm` and a GIN index now
would be optimizing a cost that doesn't exist yet, at the cost of a new
PostgreSQL extension dependency and index-maintenance overhead on every
write. This is an explicit, documented assumption, not an oversight:
**if/when the resource count grows enough that this becomes a measured
problem**, the correct fix is a new Flyway migration enabling `pg_trgm` and
adding a GIN index on the searched columns (`CREATE INDEX ... USING gin
(column gin_trgm_ops)`) — tracked as a known limitation/future optimization
below, not implemented speculatively now.

## Alternatives Considered

- **Four separate service/repository methods (list/listByCategory/search/
  searchByCategory).** Rejected — see "One Unified Query" above; this is
  exactly the combinatorial duplication a second optional filter should
  trigger consolidating, not multiplying.
- **Token-splitting the query into separate AND/OR-combined words.**
  Rejected for this milestone: a single-phrase substring match is simpler
  to explain, test, and reason about, and nothing in the current product
  requirements demands multi-word independent matching yet. If a real need
  emerges, this is a documented, isolated extension point
  (`ResourceSearchQuery`/the repository query), not a redesign.
- **PostgreSQL full-text search (`tsvector`/`tsquery`) instead of `LIKE`.**
  Rejected for this milestone — a real, more scalable option, but a bigger
  step (generated/stored `tsvector` columns, ranking functions, a schema
  migration) than a small MVP dataset currently justifies; recorded as a
  future option alongside `pg_trgm` below.
- **`pg_trgm` + GIN index now, proactively.** Rejected — see "No `pg_trgm`
  Index" above; not justified by any current evidence of a performance
  problem, and this milestone's brief explicitly warns against adding it
  without justification.

## Consequences

- Every `GET /api/v1/resources` call now runs one query (`search`)
  regardless of which optional filters are present, rather than branching
  between two different repository methods — simpler to reason about, test,
  and extend with a third optional filter later (e.g. cost type) without
  another round of method multiplication.
- A future milestone adding real scale (or a real performance complaint)
  has a clearly documented next step (`pg_trgm`/GIN, or full-text search)
  rather than needing to rediscover the trade-off from scratch.
- Search relevance is explicitly *not* a feature of this milestone; a
  future milestone wanting true relevance ranking needs a different
  underlying mechanism (full-text search's `ts_rank`, or an external search
  service) — this ADR's `LIKE`-based design does not evolve into ranking
  incrementally.

## Honest Limitations Not Solved By This ADR

- No typo tolerance, fuzzy matching, or relevance ranking.
- No multi-word AND/OR token semantics — the whole normalized phrase is
  matched as one substring.
- No full-text search, `pg_trgm`, or GIN index — acceptable now, a known,
  documented scaling limitation, not a current problem.
- No search analytics or query logging (see
  `docs/architecture/security-architecture.md` for the deliberate choice not
  to log raw user search text).
