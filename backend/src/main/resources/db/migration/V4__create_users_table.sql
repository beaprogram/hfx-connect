-- Registered HFX Connect accounts (students, newcomers, residents, community
-- organizations, moderators, administrators — see docs/product/personas.md).
--
-- id is UUID (Hibernate-generated), matching resources' reasoning, not
-- categories': users are numerous, self-registered over time by many
-- independent actors, and a sequential integer ID would let one account
-- holder estimate the total user count or enumerate other accounts by
-- incrementing a URL/token value — see ADR-007.
--
-- normalized_email enforces case-and-whitespace-insensitive uniqueness,
-- computed once in the service layer (mirrors categories.normalized_name —
-- see ADR-005). email stores the address as submitted (trimmed only), for
-- display; normalized_email is the authoritative uniqueness key.
--
-- role and status are persisted as strings (not ordinals), each constrained
-- to a fixed set of valid values so an application bug can never silently
-- write an invalid value. Milestone 5A only ever writes role='USER' and
-- status='ACTIVE' (see ADR-007 for why ACTIVE, not PENDING_VERIFICATION);
-- the other values are reserved for later milestones (5B/5C roles,
-- Milestone 9 moderation) and already enforced here so no later migration
-- is needed to add them.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL,
    normalized_email VARCHAR(180) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT users_normalized_email_key UNIQUE (normalized_email),

    CONSTRAINT users_email_not_blank CHECK (length(btrim(email)) > 0),
    CONSTRAINT users_password_hash_not_blank CHECK (length(btrim(password_hash)) > 0),

    CONSTRAINT users_role_valid CHECK (role IN ('USER', 'ORGANIZATION', 'MODERATOR', 'ADMIN')),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'DEACTIVATED'))
);
