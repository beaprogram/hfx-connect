# ADR-005: Category Primary Key Type, Slug Rules, and Name Normalization

## Status

Accepted — 2026-07-15

## Context

Milestone 3A introduces the first real domain entity, `categories`, and needs to
settle three related identifier questions before writing the migration: what type of
primary key to use, how to prevent near-duplicate category names ("Food Assistance"
vs. "food assistance" vs. "Food   Assistance"), and how URL-safe slugs are generated
deterministically from a display name.

## Decision

**Primary key:** `BIGINT GENERATED ALWAYS AS IDENTITY`. Categories are a small,
stable, admin-managed reference list (Food Assistance, Study Spaces, Employment
Support, ...) — not a large, independently-created, publicly-distributed content
entity. This mirrors the project's own architecture direction: the original resource
table sketch in `docs/architecture/system-overview.md` uses `id UUID PRIMARY KEY` for
`resources` but references it via `category_id BIGINT NOT NULL` — i.e., the project
already implied categories use a simple numeric key. Milestone 3B (resources) will use
`UUID` primary keys instead, since resources are numerous, created by many different
actors over time, and may need non-guessable identifiers once they're referenced in
public URLs.

**Name uniqueness:** a stored `normalized_name` column (lowercased, with internal
whitespace collapsed to single spaces and leading/trailing whitespace trimmed), with a
`UNIQUE` constraint. Computed once by the service layer at creation time, not via a
database expression index — keeping the normalization rule in one place (Java, where
it's unit-tested) rather than duplicated as SQL.

**Slug generation:** deterministic and derived from the (whitespace-normalized) name,
not manually supplied by API callers:

1. Unicode-normalize and strip diacritics (`café` → `cafe`).
2. Lowercase (`Locale.ROOT`, so behavior doesn't depend on server locale).
3. Replace every run of one or more characters that are not `a-z` or `0-9` with a
   single hyphen (this one rule handles whitespace, punctuation, and symbols
   uniformly — `"Employment & Career Support"` → `"employment-career-support"`).
4. Trim leading/trailing hyphens.
5. If the result is empty (a name made entirely of symbols, e.g. `"&&&"`), reject the
   request with a validation error rather than persisting an empty slug.

Slugs get their own independent `UNIQUE` constraint, separate from
`normalized_name`'s — because two different display names can normalize to different
`normalized_name` values but generate the identical slug (`"Food Assistance"` and
`"Food, Assistance!"` both slugify to `food-assistance`, but their normalized names,
`"food assistance"` and `"food, assistance!"`, differ).

## Alternatives Considered

- **UUID primary key for categories, for consistency with resources.** Rejected: it
  would add no real benefit for a small, curated, admin-managed list, and a numeric ID
  is simpler to work with in the interim (readable in logs, in ad hoc SQL, and in
  manual testing) while the API has no public consumers yet.
- **Case-insensitive uniqueness via a PostgreSQL expression index
  (`UNIQUE (lower(name))`) instead of a stored `normalized_name` column.** Rejected:
  it would still miss the whitespace-collapsing requirement (`"Food   Assistance"`
  needs to collide with `"Food Assistance"`, not just case) unless the expression grew
  more complex, and it would duplicate the normalization rule in two languages (SQL
  and Java) instead of one.
- **Allowing API callers to supply an explicit slug.** Rejected for this milestone:
  it roughly doubles the validation surface (slug format, slug/name mismatches, slug
  collisions independent of name collisions) for a feature with no demonstrated product
  need yet — categories are few and admin-created, and an auto-generated slug is
  already predictable and readable. This can be revisited if a real need for
  custom slugs emerges (for example, wanting a shorter URL than the full name implies).
- **Silently appending a numeric suffix on slug collision (`food-assistance-2`).**
  Rejected: for a small, curated category list, a silent suffix would more likely mask
  a genuine duplicate-content mistake than serve a real need to have two distinctly
  named categories share a slug root. Returning `409 Conflict` surfaces the collision
  to whoever is creating the category instead of hiding it.

## Consequences

- Every future domain entity needs its own explicit ID-type decision, but this ADR
  establishes the project's default reasoning ("stable/admin-managed → numeric ID,
  content entities → UUID") that later ADRs can reference or explicitly override.
- Because slugs are derived, not supplied, there is no "rename category, keep old
  slug redirecting" concern yet — if that becomes a real product need later (for
  example, to avoid breaking existing links after a rename), it will need its own
  design, since this milestone has no update endpoint at all.
- The normalization and slug rules are implemented once, in
  `com.hfxconnect.category.CategorySlugGenerator` and `CategoryService`, and unit
  tested directly — this is the authoritative reference for exact behavior, more
  precise than this document's prose description.
