# Security Architecture

> Status: written once Milestone 5B gave the project its first real
> authentication session. Describes the security posture of the backend as it
> actually exists today — not a target state — and will be updated again once
> Milestone 5C adds request-level authorization and protected routes.

## What Exists Today

| Capability | Status |
|---|---|
| Password hashing | BCrypt, strength 12 (Milestone 5A — [ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md)) |
| Registration | `POST /api/v1/auth/register` — creates a `USER`/`ACTIVE`/unverified account only; privilege escalation structurally impossible (Milestone 5A) |
| Login | `POST /api/v1/auth/login` — generic failure response, timing-mitigated (Milestone 5B — [ADR-008](../decisions/ADR-008-authentication-session-architecture.md)) |
| Access tokens | Short-lived (15 min default) signed JWT (HS256), issued and validated by `AccessTokenService`, **not yet checked by any endpoint** |
| Refresh sessions | Opaque, SHA-256-hashed, rotating, reuse-detected, revocable (Milestone 5B) |
| Logout | Revokes the matching refresh session; idempotent | 
| CORS | Explicit origin allowlist, credentials enabled only for it, no wildcard |
| Request-level authorization | **None** — no route, including the auth endpoints themselves, requires or checks anything. Milestone 5C |
| Rate limiting | **None** — login accepts unlimited attempts |
| Access-token revocation | **None** — a compromised access token is valid until it naturally expires |

## Authentication Model

A registered user obtains a session by logging in (or refreshing an existing
session). A session consists of two independently-transported tokens with
deliberately different lifetimes, storage, and threat models:

- **Access token** — a signed JWT, returned in the JSON response body, meant to
  be presented as `Authorization: Bearer <token>` on a future authenticated
  request (Milestone 5C's concern; nothing validates it yet). Short-lived
  (15 minutes by default) so a leaked copy has a small exposure window. Never
  placed in a cookie — this keeps it out of automatic browser transmission
  (no CSRF exposure the way a cookie-based credential has) and lets a future
  request-authentication filter read it explicitly rather than implicitly.
- **Refresh token** — a 256-bit random value, set only as an `HttpOnly` cookie
  (`hfx_refresh_token`), never returned in JSON, never readable by JavaScript.
  Long-lived (30 days by default) but rotated on every use and revocable
  server-side, which is what makes the longer lifetime acceptable.

See [ADR-008](../decisions/ADR-008-authentication-session-architecture.md) for
the complete design rationale, alternatives considered, and consequences.

## Token Details

### Access Token (JWT)

- **Algorithm:** HS256 (HMAC-SHA256), explicit — `AccessTokenService` never
  accepts an unsigned token or a token using a different algorithm; JJWT's
  `parseSignedClaims` structurally cannot be tricked into either.
- **Claims:** `sub` (user ID), `role`, `iss` (`hfx-connect` by default), `iat`,
  `exp`, `jti` (random, for log correlation only — not checked against a
  revocation list; none exists). No email, no password/hash, no refresh-token
  material.
- **Signing secret:** `JWT_SECRET`, externalized, never regenerated at
  startup. Construction fails immediately (via JJWT's `Keys.hmacShaKeyFor`) if
  the configured secret is under 256 bits. The shipped default is a
  development-only placeholder — see `.env.example`.
- **No revocation:** once issued, an access token is valid until `exp`
  regardless of what happens to its owning refresh session afterward. This is
  an accepted trade-off for statelessness, not an oversight — see ADR-008's
  consequences section for what a future revocation mechanism would need.

### Refresh Token (Opaque)

- **Generation:** 256 bits from `SecureRandom`, Base64URL-encoded, no embedded
  user/database ID, no predictable sequence.
- **Storage:** only a SHA-256 hex digest (`refresh_sessions.token_hash`) — the
  raw value is never persisted or logged, and exists in memory only long
  enough to become a `Set-Cookie` header and a hash.
- **Rotation:** every successful refresh revokes the presented session and
  issues a new one in the same `family_id`. The old token is permanently
  unusable afterward.
- **Reuse detection:** presenting an already-revoked token (rotated away,
  logged out, or genuinely stolen and replayed) revokes every session in that
  family — not just the one presented.

## Cookie Policy

| Attribute | Value | Rationale |
|---|---|---|
| Name | `hfx_refresh_token` | |
| `HttpOnly` | always `true` | Never readable by JavaScript — the entire point of using a cookie over returning it in JSON |
| `Path` | `/api/v1/auth` | Scoped to the endpoints that actually need it, not sent on every API request |
| `Max-Age` | matches `JWT_REFRESH_TOKEN_TTL` | The cookie should never outlive the session it carries |
| `Secure` | `AUTH_COOKIE_SECURE` (env-configured; `false` for local dev) | Local dev is plain HTTP; browsers reject `Secure` cookies over HTTP entirely |
| `SameSite` | `AUTH_COOKIE_SAME_SITE` (env-configured; `Lax` for local dev) | Local dev (`localhost:3000`/`:8080`) is cross-origin but same-*site*, where `Lax` still works; production (Vercel/Render — genuinely cross-site) needs `None`+`Secure=true` |

**Production deployment must explicitly set** `AUTH_COOKIE_SECURE=true` and
`AUTH_COOKIE_SAME_SITE=None` — the local-dev defaults are wrong for the real
topology. See `.env.example` and `backend/README.md`.

## CORS

`WebCorsConfig` allows `/api/v1/**` requests only from the explicit,
environment-configured origin allowlist (`CORS_ALLOWED_ORIGINS`) — never a
`"*"` wildcard. As of Milestone 5B, `allowCredentials` is `true` (required for
the browser to send/receive the refresh cookie cross-origin at all), which
Spring only permits alongside an explicit origin list in the first place — a
wildcard combined with credentials fails at startup, so this project was never
at risk of that specific misconfiguration.

## Account Status Enforcement

Both login and refresh check the owning account's `status`
(`com.hfxconnect.user.AccountStatus`): only `ACTIVE` accounts may authenticate
or refresh. A correct-credentials login (or an in-progress refresh) against a
non-`ACTIVE` account returns `403 ACCOUNT_UNAVAILABLE` — a real, honest
response, distinct from `401 AUTHENTICATION_FAILED`, but one that never
explains *why* the account is unavailable (suspended vs. deactivated vs.
pending), to avoid revealing internal account state beyond "this account
cannot authenticate right now."

## Error Responses

Authentication failures never reveal whether a specific email is registered:
unknown email and wrong password return the exact same `401
AUTHENTICATION_FAILED` body, and `AuthenticationService` performs a real
`PasswordEncoder.matches` comparison against a fixed dummy hash even when no
account exists, closing the timing side channel a naive implementation would
otherwise leave open. Refresh-token failures (`INVALID_REFRESH_TOKEN`,
`REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED`, `AUTHENTICATION_REQUIRED`) are
distinguishable from each other — the refresh token itself is a high-entropy
secret only its legitimate holder could present, so this is a different
category of information than the login-email-enumeration case. See
`docs/api/README.md` for the full error-shape/status-code reference.

## Honest Limitations

- **No rate limiting.** Login accepts unlimited attempts. A robust
  implementation needs a shared counter store (Redis or equivalent) surviving
  multiple backend instances — out of this milestone's scope. Deferred,
  honestly, to a later security/deployment milestone; not claimed to exist.
- **No access-token revocation.** A compromised access token is valid until it
  naturally expires (≤15 minutes), independent of refresh-session revocation.
- **No request-level authorization anywhere.** No route — including the auth
  endpoints themselves — requires or validates an access token yet. This is
  Milestone 5C's entire purpose.
- **No automated dependency-vulnerability scanning** is configured in this
  project (not introduced by, or specific to, Milestone 5B).
- **A narrow, accepted concurrency edge case:** two genuinely simultaneous
  legitimate refresh requests presenting the same token can trigger a
  false-positive family-wide revocation. See
  [ADR-008](../decisions/ADR-008-authentication-session-architecture.md)'s
  "Concurrency" section.

## See Also

- [ADR-007: User Identity and Password Hashing](../decisions/ADR-007-user-identity-and-password-hashing.md)
- [ADR-008: Authentication Session Architecture](../decisions/ADR-008-authentication-session-architecture.md)
- [ADR-006: Frontend-Backend Connectivity (CORS)](../decisions/ADR-006-frontend-backend-connectivity.md)
- [Backend Architecture](backend-architecture.md)
- [API Documentation](../api/README.md)
