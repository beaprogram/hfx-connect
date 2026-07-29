-- Structured weekly operating hours, one row per resource/day. See
-- ADR-011 for the full design (timezone, overnight-interval, and
-- open-now-calculation reasoning this table exists to support).
--
-- id is BIGINT identity, matching categories.id's reasoning (see ADR-005 /
-- V2) rather than resources.id's UUID reasoning: these rows are never
-- independently addressable in a URL or public API, only ever read/written
-- as part of "a resource's whole weekly schedule."
--
-- resource_id cascades on delete: an operating-hours row has no meaning
-- independent of the resource it describes, the same reasoning
-- refresh_sessions.user_id already established (see V5).
--
-- day_of_week is stored as the readable java.time.DayOfWeek enum name
-- (MONDAY..SUNDAY), not an ordinal — readable in the database, and the
-- entity field is typed as java.time.DayOfWeek itself (no bespoke enum).
--
-- opens_at/closes_at are plain local TIME values (no zone component) — they
-- mean "this is what the sign on the door says," always interpreted in
-- America/Halifax by the application layer (see ADR-011), never a UTC
-- instant. Both are nullable because a closed day has no times at all.
--
-- An interval where opens_at is later than closes_at (e.g. 22:00-02:00) is
-- interpreted as crossing midnight, continuing into the next calendar day
-- (see ADR-011) — this needs no extra column. opens_at == closes_at is
-- rejected by the CHECK constraint below rather than treated as a 24-hour
-- day (see ADR-011's "Overnight Intervals" decision).
CREATE TABLE resource_operating_hours (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    opens_at TIME,
    closes_at TIME,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT resource_operating_hours_resource_day_key UNIQUE (resource_id, day_of_week),

    CONSTRAINT resource_operating_hours_day_of_week_valid CHECK (day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
    )),

    -- A closed day carries no times; an open day requires both times, and
    -- they must not be equal (see ADR-011 — an equal-times "24 hours"
    -- convention was deliberately rejected as ambiguous).
    CONSTRAINT resource_operating_hours_times_match_closed CHECK (
        (closed = TRUE AND opens_at IS NULL AND closes_at IS NULL)
        OR (closed = FALSE AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at <> closes_at)
    )
);

-- Required: the batch-load query fetches every row for a page's worth of
-- resource IDs in one query (ADR-011's N+1-avoidance strategy), and the
-- single-resource detail lookup fetches by resource_id alone.
CREATE INDEX resource_operating_hours_resource_id_idx ON resource_operating_hours (resource_id);
