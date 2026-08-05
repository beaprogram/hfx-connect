-- Two focused contribution tables (Milestone 8B) — proposing a brand-new
-- resource, and reporting an issue with an existing one. Kept as two
-- distinct tables rather than one generic "report" table: they have
-- different owning relationships (a category vs. an existing resource),
-- different field sets, and different duplicate-pending rules, and merging
-- them would mean every row carries a large number of columns that are
-- meaningless for the other kind of contribution. See
-- docs/decisions/ADR-015-community-contribution-workflows-design.md and
-- docs/milestones/milestone-08b-submissions-corrections.md for the full
-- design.
--
-- Both tables use a UUID primary key, not a BIGINT identity: unlike
-- resource_operating_hours/saved_resources (V6/V8, never independently
-- addressable by their own id — always looked up via a natural key like
-- resourceId+dayOfWeek or userId+resourceId), a submission or report *is*
-- addressable by its own id in a URL path (GET .../{submissionId},
-- GET .../{reportId}) with no other natural key available — the same
-- reasoning that made resources.id and users.id UUID (see ADR-005/ADR-007).

-- One row per proposed new resource (Milestone 8B). Does not create a
-- resources row and is never surfaced in public search/list/map/nearby
-- results — approval and actual resource creation are Milestone 9.
--
-- submitted_by_user_id: ON DELETE RESTRICT, not CASCADE. Unlike
-- saved_resources.user_id (V8, a relation with no meaning independent of
-- the account, correctly cascaded away), a submission is community-
-- contribution history with standalone value even if the submitting
-- account is later deleted — deleting the account should not silently
-- destroy it. No user-deletion endpoint exists yet, so this is a schema
-- decision made in anticipation of one, matching how V8 anticipated its
-- own future deletion endpoint the other way.
--
-- category_id: ON DELETE RESTRICT, mirroring resources.category_id's own
-- existing policy (V3) exactly — a category with any resource, and now any
-- submission, referencing it cannot be deleted (no category-delete
-- endpoint exists at all, so this is currently unreachable in practice,
-- but the constraint is correct in principle).
CREATE TABLE resource_submissions (
    id UUID PRIMARY KEY,
    submitted_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    name VARCHAR(180) NOT NULL,
    normalized_name VARCHAR(180) NOT NULL,
    short_description VARCHAR(300) NOT NULL,
    full_description VARCHAR(4000),
    address_line_1 VARCHAR(200) NOT NULL,
    address_line_2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    province VARCHAR(2) NOT NULL,
    postal_code VARCHAR(7) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(180),
    website_url VARCHAR(500),
    cost_type VARCHAR(20) NOT NULL,
    eligibility_information VARCHAR(1000),
    accessibility_information VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at TIMESTAMPTZ,

    CONSTRAINT resource_submissions_status_check
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT resource_submissions_cost_type_check
        CHECK (cost_type IN ('FREE', 'LOW_COST', 'PAID', 'UNKNOWN'))
);

-- Supports "list this user's submissions, newest first" — the only owner
-- read path this table has.
CREATE INDEX resource_submissions_owner_submitted_at_idx
    ON resource_submissions (submitted_by_user_id, submitted_at DESC);

-- Postgres does not automatically index a foreign key's referencing column.
CREATE INDEX resource_submissions_category_id_idx ON resource_submissions (category_id);

-- The duplicate-pending guard: at most one PENDING_REVIEW submission per
-- (user, category, normalized name) at a time — a partial unique index, so
-- a user may have any number of WITHDRAWN/REJECTED/APPROVED submissions
-- with the same name/category, just not two simultaneously pending ones
-- (an accidental double-submit guard, not a "you may only ever submit this
-- resource once" rule).
CREATE UNIQUE INDEX resource_submissions_pending_duplicate_key
    ON resource_submissions (submitted_by_user_id, category_id, normalized_name)
    WHERE status = 'PENDING_REVIEW';

-- One row per reported issue on an existing active resource (Milestone
-- 8B). Never modifies the target resource and is never surfaced in public
-- search/list/map/nearby results — reviewing and applying a correction are
-- Milestone 9.
--
-- reported_by_user_id: ON DELETE RESTRICT, same reasoning as
-- resource_submissions.submitted_by_user_id above.
--
-- resource_id: ON DELETE SET NULL, not CASCADE or RESTRICT. An unresolved
-- correction report has standalone review value even if its target
-- resource is later deleted (deleting the resource should not silently
-- destroy an open report about it — the opposite policy from
-- saved_resources.resource_id, V8, whose CASCADE was correct there because
-- a save has no meaning without its resource; a *report* about a resource
-- still means something after the fact, e.g. "this was reported as a
-- duplicate and then removed"). resource_name_snapshot/
-- resource_slug_snapshot are captured once at creation specifically so a
-- report stays reviewable and readable by its own owner even after
-- resource_id becomes null; the live resource's current name is never
-- re-read from the (possibly now-absent) association for display.
CREATE TABLE correction_reports (
    id UUID PRIMARY KEY,
    reported_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    resource_id UUID REFERENCES resources (id) ON DELETE SET NULL,
    resource_name_snapshot VARCHAR(180) NOT NULL,
    resource_slug_snapshot VARCHAR(220) NOT NULL,
    issue_type VARCHAR(30) NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    proposed_name VARCHAR(180),
    proposed_description VARCHAR(4000),
    proposed_address_line_1 VARCHAR(200),
    proposed_address_line_2 VARCHAR(200),
    proposed_city VARCHAR(100),
    proposed_province VARCHAR(2),
    proposed_postal_code VARCHAR(7),
    proposed_phone VARCHAR(40),
    proposed_email VARCHAR(180),
    proposed_website_url VARCHAR(500),
    proposed_cost_type VARCHAR(20),
    proposed_cost_details VARCHAR(500),
    proposed_eligibility VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at TIMESTAMPTZ,

    CONSTRAINT correction_reports_status_check
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT correction_reports_issue_type_check
        CHECK (issue_type IN ('GENERAL_INFORMATION', 'ADDRESS', 'CONTACT_INFORMATION',
            'OPERATING_HOURS', 'ELIGIBILITY', 'ACCESSIBILITY', 'COST', 'RESOURCE_CLOSED',
            'DUPLICATE_RESOURCE', 'OTHER')),
    CONSTRAINT correction_reports_proposed_cost_type_check
        CHECK (proposed_cost_type IS NULL OR proposed_cost_type IN ('FREE', 'LOW_COST', 'PAID', 'UNKNOWN'))
);

-- Supports "list this user's correction reports, newest first".
CREATE INDEX correction_reports_owner_submitted_at_idx
    ON correction_reports (reported_by_user_id, submitted_at DESC);

-- Postgres does not automatically index a foreign key's referencing column.
CREATE INDEX correction_reports_resource_id_idx ON correction_reports (resource_id);

-- The duplicate-pending guard: at most one PENDING_REVIEW report per
-- (user, resource, issue type) at a time — lets a user report two genuinely
-- different issues on the same resource, or re-report after their first
-- report is resolved, without letting an accidental double-submit create
-- two simultaneous pending reports for the identical issue.
CREATE UNIQUE INDEX correction_reports_pending_duplicate_key
    ON correction_reports (reported_by_user_id, resource_id, issue_type)
    WHERE status = 'PENDING_REVIEW';
