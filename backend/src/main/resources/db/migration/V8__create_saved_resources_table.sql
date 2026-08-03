-- One row per (user, resource) a user has saved for later. Milestone 8A —
-- see docs/decisions (ADR-014 if one was warranted) and
-- docs/milestones/milestone-08a-saved-resources.md for the full design.
--
-- id is BIGINT identity, matching resource_operating_hours' reasoning (V6):
-- these rows are never independently addressable in a URL or public API —
-- always read/written as "this user's saved-resource relation for this
-- resource," identified by (user_id, resource_id), never by this table's
-- own surrogate key.
--
-- user_id cascades on delete: a saved-resource relation has no meaning
-- independent of the account that saved it — the same reasoning
-- refresh_sessions.user_id already established (V5). No user-deletion
-- endpoint exists yet, but the schema is correct in anticipation of one.
--
-- resource_id cascades on delete: a saved-resource relation has no meaning
-- independent of the resource being saved — the same reasoning
-- resource_operating_hours.resource_id already established (V6).
-- Deactivating a resource (resources.active = false) is NOT a delete — a
-- saved relation survives a resource being deactivated and is simply
-- excluded from the visible saved list (SavedResourceService) until/unless
-- the resource becomes active again; only a genuine row deletion (which no
-- endpoint performs yet) triggers this cascade.
--
-- The user_id + resource_id unique constraint is the authoritative
-- duplicate-save guard: SavedResourceService's idempotent save checks
-- existence first, but a concurrent-request race is resolved by this
-- constraint, not by the application-level check alone (see
-- SavedResourceService's handling of the resulting DataIntegrityViolationException).
CREATE TABLE saved_resources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    resource_id UUID NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT saved_resources_user_resource_key UNIQUE (user_id, resource_id)
);

-- Supports the primary read path this table exists for: "list this user's
-- saved resources, newest first" (SavedResourceRepository's paginated
-- query). created_at DESC matches the default savedAt-descending ordering.
CREATE INDEX saved_resources_user_id_created_at_idx ON saved_resources (user_id, created_at DESC);

-- Postgres does not automatically index a foreign key's referencing column.
-- Without this, deleting a resource (cascading to its saved_resources rows)
-- would require a sequential scan of this table to find matching rows.
CREATE INDEX saved_resources_resource_id_idx ON saved_resources (resource_id);
