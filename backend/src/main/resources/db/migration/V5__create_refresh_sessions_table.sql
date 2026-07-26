-- One row per issued (and every rotated-away) refresh token. See ADR-008 for
-- the full authentication-session design this table implements.
--
-- id is UUID, matching users/resources' reasoning (numerous, created
-- continuously, one per login/refresh) — not categories' BIGINT.
--
-- The raw refresh token is never stored: token_hash is a SHA-256 hex digest
-- of it. A fast digest (not BCrypt) is deliberate — see ADR-008's "Refresh
-- Token" section for why a 256-bit random token doesn't need a slow,
-- human-password-oriented hash.
--
-- family_id groups every session descended from one original login through
-- however many rotations. Reuse detection (RefreshSessionService) revokes an
-- entire family at once when an already-revoked session's token is presented
-- again, so family_id must survive rotation, not just the single row.
--
-- user_id cascades on delete: a refresh session has no meaning independent
-- of the account it authenticates (see ADR-008's "cascading deletion"
-- decision) — no user-deletion endpoint exists in any milestone yet, but the
-- schema is correct in anticipation of one.
CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_session_id UUID REFERENCES refresh_sessions (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,

    CONSTRAINT refresh_sessions_token_hash_key UNIQUE (token_hash),
    CONSTRAINT refresh_sessions_token_hash_not_blank CHECK (length(btrim(token_hash)) > 0)
);

-- Supports "find all sessions for this user" (not used by any endpoint yet,
-- but the natural first query a future "active sessions" or admin-revoke
-- feature would need) and the FK's own lookups.
CREATE INDEX refresh_sessions_user_id_idx ON refresh_sessions (user_id);

-- Supports the reuse-detection bulk revoke: "revoke every session in this
-- family" (RefreshSessionService.revokeFamily).
CREATE INDEX refresh_sessions_family_id_idx ON refresh_sessions (family_id);

-- Supports a future expired-session cleanup job (none exists yet — out of
-- scope for this milestone) without a full table scan.
CREATE INDEX refresh_sessions_expires_at_idx ON refresh_sessions (expires_at);
