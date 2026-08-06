-- Moderation workflow (Milestone 9A): review metadata on both Milestone 8B
-- contribution tables, a verification timestamp on resources, and a new
-- append-only moderation audit trail. See
-- docs/decisions/ADR-016-moderation-workflow-design.md and
-- docs/milestones/milestone-09a-moderation-workflow.md for the full design.

-- A resource's verification timestamp companion to verification_status
-- (V3) — did not exist before Milestone 9A because nothing before now could
-- ever move a resource to VERIFIED (see resources_verification_status_valid
-- and VerificationStatus's own "no mutator exists yet" Javadoc, both
-- superseded as of this migration). Nullable: every pre-9A resource, and
-- every resource an ADMIN/MODERATOR creates directly via
-- POST /api/v1/resources, stays UNVERIFIED with no verification timestamp
-- until a moderator reviews it through this workflow.
ALTER TABLE resources ADD COLUMN last_verified_at TIMESTAMPTZ;

-- Review metadata for resource_submissions (Milestone 8B, V9). All four new
-- columns are nullable — populated together, exactly once, when a
-- submission's status leaves PENDING_REVIEW (see the CHECK constraint
-- below, which enforces that "together" as a database invariant rather than
-- an application-only convention).
--
-- reviewed_by_user_id: ON DELETE RESTRICT, not SET NULL or CASCADE — a
-- moderation decision's authorship is permanent audit history; deleting the
-- reviewing account (no such endpoint exists yet, same anticipatory
-- reasoning V9 already used for submitted_by_user_id/reported_by_user_id)
-- must not silently erase who made the decision.
--
-- resulting_resource_id: the public resource an approved submission
-- created. ON DELETE SET NULL (there is no resource-delete endpoint either,
-- but resources.active can already go false — this column tracks *creation
-- provenance*, not current resource availability, so it deliberately does
-- not track deactivation). UNIQUE: a submission may publish at most one
-- resource, ever (a partial unique index, since most rows have this NULL
-- and NULLs are never compared equal by a UNIQUE constraint, which is
-- exactly the "only one non-null value per submission, no constraint on how
-- many submissions have none" semantics this needs).
--
-- resulting_resource_name/resulting_resource_slug: captured once at
-- approval time, the same snapshot reasoning correction_reports already
-- established (V9) for its own target resource — lets the owner-facing and
-- moderation-facing responses render a name/link without a join on every
-- read. name can in principle drift from the live resource's current name
-- if a later correction report edits it (slug never changes — ADR-005 — so
-- the link stays valid regardless); an accepted, documented trade-off, not
-- an oversight.
ALTER TABLE resource_submissions
    ADD COLUMN reviewed_by_user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN review_reason VARCHAR(1000),
    ADD COLUMN resulting_resource_id UUID REFERENCES resources (id) ON DELETE SET NULL,
    ADD COLUMN resulting_resource_name VARCHAR(180),
    ADD COLUMN resulting_resource_slug VARCHAR(220);

CREATE UNIQUE INDEX resource_submissions_resulting_resource_id_key
    ON resource_submissions (resulting_resource_id)
    WHERE resulting_resource_id IS NOT NULL;

-- Review metadata is present together (final APPROVED/REJECTED states) or
-- absent together (PENDING_REVIEW, WITHDRAWN) — never partially populated.
ALTER TABLE resource_submissions ADD CONSTRAINT resource_submissions_review_metadata_check
    CHECK ((status IN ('APPROVED', 'REJECTED'))
        = (reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL));

-- A resulting resource can only exist on an APPROVED submission.
ALTER TABLE resource_submissions ADD CONSTRAINT resource_submissions_resulting_resource_status_check
    CHECK (resulting_resource_id IS NULL OR status = 'APPROVED');

-- Review metadata for correction_reports (Milestone 8B, V9) — the same
-- shape and reasoning as resource_submissions above.
--
-- applied_to_resource_at: distinct from reviewed_at. An APPROVED correction
-- report does not always change the target resource (a moderator may
-- approve without applying any supported field, or approve only a
-- deactivation with no scalar field changes) — this column is set only when
-- at least one scalar field was actually written to the target resource,
-- letting the owner-facing response answer "were my proposed changes
-- actually applied?" precisely rather than conflating it with "was this
-- reviewed?".
ALTER TABLE correction_reports
    ADD COLUMN reviewed_by_user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN review_reason VARCHAR(1000),
    ADD COLUMN applied_to_resource_at TIMESTAMPTZ;

ALTER TABLE correction_reports ADD CONSTRAINT correction_reports_review_metadata_check
    CHECK ((status IN ('APPROVED', 'REJECTED'))
        = (reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL));

-- Changes can only have been applied on an APPROVED report.
ALTER TABLE correction_reports ADD CONSTRAINT correction_reports_applied_status_check
    CHECK (applied_to_resource_at IS NULL OR status = 'APPROVED');

-- The moderation audit trail (Milestone 9A) — one append-only row per
-- concrete moderation effect. Deliberately one focused table, not a
-- generic system-wide event-sourcing framework: it only ever records
-- moderation decisions and their direct consequences (resource creation,
-- resource field updates, resource deactivation), nothing else in this
-- application writes to it, and no API exists (or will exist) to edit or
-- delete a row — see ADR-016's "Audit Model" section for the exact one-row-
-- per-effect rule this table's application code follows.
--
-- contribution_id is a plain UUID, not a foreign key: it references either
-- resource_submissions or correction_reports depending on contribution_type,
-- and Postgres has no polymorphic-foreign-key mechanism — the same
-- reasoning already applies to nothing else in this schema because nothing
-- before this table needed to reference "one of two possible tables."
--
-- actor_user_id: ON DELETE RESTRICT, same reasoning as
-- resource_submissions.reviewed_by_user_id. actor_email is a point-in-time
-- snapshot (the same established pattern as correction_reports's own
-- resource_name_snapshot/resource_slug_snapshot, V9) — captured so a
-- moderator's audit trail entries stay independently readable even if that
-- account's email later changes, without requiring a join back to users for
-- every audit read.
--
-- before_snapshot/after_snapshot: JSONB, both nullable. Populated with a
-- small, explicitly-built set of fields relevant to the specific effect —
-- never a serialized entity — see ADR-016's "Snapshot Policy" section for
-- exactly what each action type stores and, just as importantly, what it
-- never stores (no password hashes, tokens, refresh-session data, or
-- unrelated account fields ever reach this table).
CREATE TABLE moderation_audit_events (
    id UUID PRIMARY KEY,
    contribution_type VARCHAR(30) NOT NULL,
    contribution_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    decision VARCHAR(20),
    actor_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    actor_email VARCHAR(180) NOT NULL,
    review_reason VARCHAR(1000),
    affected_resource_id UUID REFERENCES resources (id) ON DELETE SET NULL,
    before_snapshot JSONB,
    after_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT moderation_audit_events_contribution_type_check
        CHECK (contribution_type IN ('RESOURCE_SUBMISSION', 'CORRECTION_REPORT')),
    CONSTRAINT moderation_audit_events_action_check
        CHECK (action IN ('REVIEW_DECISION', 'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_DEACTIVATED')),
    CONSTRAINT moderation_audit_events_decision_check
        CHECK (decision IS NULL OR decision IN ('APPROVED', 'REJECTED'))
);

-- Supports both "this contribution's full audit history, oldest first" (the
-- moderation detail page) and the global moderator-only audit list, which
-- benefits from the same leading (contribution_type) column when filtered.
CREATE INDEX moderation_audit_events_contribution_idx
    ON moderation_audit_events (contribution_type, contribution_id, created_at);
