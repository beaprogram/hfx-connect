-- Organization identity, verification, and resource ownership (Milestone
-- 10A) — see docs/decisions/ADR-017-organization-identity-and-ownership.md
-- and docs/milestones/milestone-10a-organization-management.md for the
-- full design. Establishes the foundation Milestone 10B's events will
-- build on; events themselves are out of scope here.

-- One organization profile per ORGANIZATION-role account (Milestone 10A's
-- deliberate MVP simplification — see ADR-017's "One Owner Per
-- Organization" section: no staff/membership/invitations yet). `id` is
-- UUID for the same reasoning resources.id/users.id already established
-- (ADR-005/ADR-007) — an organization is addressable by its own id/slug in
-- a URL, with no natural-key alternative.
--
-- owner_user_id: ON DELETE RESTRICT, the same "contribution/identity
-- history has standalone value" reasoning already applied to
-- resource_submissions.submitted_by_user_id (V9) — deleting the owning
-- account must not silently destroy the organization profile. The unique
-- index below is what actually enforces "one organization per owner."
--
-- verified_by_user_id: ON DELETE RESTRICT, matching
-- resource_submissions.reviewed_by_user_id's (V10) reasoning exactly —
-- moderation/verification history must not silently lose the reviewer's
-- identity.
--
-- verification_status: PENDING_VERIFICATION/VERIFIED/REJECTED/SUSPENDED —
-- deliberately reuses the exact "PENDING_VERIFICATION" name
-- users.status already established (V4) for the analogous concept,
-- rather than inventing a differently-spelled equivalent. A distinct enum
-- from resources.verification_status (UNVERIFIED/VERIFIED) — organization
-- identity verification and resource content verification are different
-- concepts that happen to share a word.
--
-- verified_by_user_id/verified_at/verification_reason: present together
-- exactly when verification_status is a final decision (VERIFIED,
-- REJECTED, or SUSPENDED — suspension is itself an admin decision with
-- its own reason, reusing these same three columns rather than adding a
-- separate set just for that one additional transition), absent
-- otherwise (PENDING_VERIFICATION, including the very first submission).
CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name VARCHAR(180) NOT NULL,
    normalized_name VARCHAR(180) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description VARCHAR(2000),
    website_url VARCHAR(500),
    public_email VARCHAR(180),
    phone VARCHAR(40),
    address_line_1 VARCHAR(200),
    city VARCHAR(100),
    province VARCHAR(2),
    postal_code VARCHAR(7),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    verified_by_user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    verified_at TIMESTAMPTZ,
    verification_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT organizations_verification_status_check
        CHECK (verification_status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT organizations_verification_metadata_check
        CHECK ((verification_status IN ('VERIFIED', 'REJECTED', 'SUSPENDED'))
            = (verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL AND verification_reason IS NOT NULL))
);

-- "One organization per owner account" — a real database constraint, not
-- just an application-level check (the same "database is the authority"
-- posture every duplicate-guard in this project already takes).
CREATE UNIQUE INDEX organizations_owner_user_id_key ON organizations (owner_user_id);
CREATE UNIQUE INDEX organizations_slug_key ON organizations (slug);

-- Supports the admin queue's default (PENDING_VERIFICATION first) and its
-- verification-status filter.
CREATE INDEX organizations_verification_status_idx ON organizations (verification_status, created_at);

-- Resource ownership (Milestone 10A). NULL means "unowned, an ordinary
-- HFX Connect-managed listing" — the default and only state every
-- pre-existing resource has after this migration; no ownership is ever
-- fabricated here.
--
-- ON DELETE SET NULL, not RESTRICT or CASCADE: deleting or
-- administratively removing an organization must never destroy the
-- public community resource itself — the resource simply reverts to
-- unowned. There is no organization-delete endpoint yet, but this policy
-- is correct in principle the same way resources.category_id's RESTRICT
-- was chosen in anticipation of category-domain needs (V3).
ALTER TABLE resources ADD COLUMN organization_id UUID REFERENCES organizations (id) ON DELETE SET NULL;
CREATE INDEX resources_organization_id_idx ON resources (organization_id);

-- Resource-ownership claims — workflow history, not the authority on
-- current ownership (resources.organization_id is that authority; see
-- ADR-017's "Ownership Source of Truth" section). A claim records one
-- organization's request to own one resource and the admin decision on
-- it; approving a claim is a separate write to resources.organization_id
-- performed atomically alongside marking the claim APPROVED.
--
-- organization_id/resource_id: ON DELETE RESTRICT — claim history has
-- standalone review/audit value, the same reasoning applied throughout
-- this schema to contribution/decision history.
CREATE TABLE resource_ownership_claims (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE RESTRICT,
    resource_id UUID NOT NULL REFERENCES resources (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by_user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    review_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT resource_ownership_claims_status_check
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT resource_ownership_claims_review_metadata_check
        CHECK ((status IN ('APPROVED', 'REJECTED'))
            = (reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL))
);

-- At most one pending claim per (organization, resource) at a time —
-- prevents an accidental double-submit from creating two simultaneous
-- pending claims; does not block a later re-claim once the earlier one
-- is withdrawn/rejected. This index alone does not prevent two different
-- *organizations* from each having a pending claim on the same resource
-- at once (that race is resolved at approval time — see
-- resources_organization_id_idx and the claim-approval transaction's own
-- "resource still unowned" check, ADR-017).
CREATE UNIQUE INDEX resource_ownership_claims_pending_key
    ON resource_ownership_claims (organization_id, resource_id)
    WHERE status = 'PENDING_REVIEW';

CREATE INDEX resource_ownership_claims_organization_id_idx
    ON resource_ownership_claims (organization_id, requested_at DESC);
CREATE INDEX resource_ownership_claims_resource_id_idx ON resource_ownership_claims (resource_id);

-- Organization audit trail (Milestone 10A) — a focused, separate table
-- from moderation_audit_events (V10), not a reuse of it: that table's
-- contribution_type/action columns are CHECK-constrained to the
-- Milestone 8B/9A contribution-review domain specifically
-- (RESOURCE_SUBMISSION/CORRECTION_REPORT, REVIEW_DECISION/
-- RESOURCE_CREATED/...), and organization verification/ownership-claim
-- events do not fit that shape without corrupting its meaning — see
-- ADR-017's "Audit Model" section. Same append-only, explicit-snapshot
-- design as moderation_audit_events otherwise.
CREATE TABLE organization_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(30) NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE RESTRICT,
    resource_id UUID REFERENCES resources (id) ON DELETE SET NULL,
    claim_id UUID REFERENCES resource_ownership_claims (id) ON DELETE SET NULL,
    actor_user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    actor_email VARCHAR(180) NOT NULL,
    review_reason VARCHAR(1000),
    before_snapshot JSONB,
    after_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT organization_audit_events_event_type_check
        CHECK (event_type IN ('ORGANIZATION_SUBMITTED', 'ORGANIZATION_VERIFIED', 'ORGANIZATION_REJECTED',
            'ORGANIZATION_SUSPENDED', 'OWNERSHIP_CLAIM_SUBMITTED', 'OWNERSHIP_CLAIM_APPROVED',
            'OWNERSHIP_CLAIM_REJECTED', 'OWNERSHIP_CLAIM_WITHDRAWN'))
);

CREATE INDEX organization_audit_events_organization_idx
    ON organization_audit_events (organization_id, created_at);
