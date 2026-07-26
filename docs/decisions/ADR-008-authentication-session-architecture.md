# ADR-008: Authentication Session Architecture (Access Tokens, Refresh Tokens, Cookies)

## Status

Accepted — 2026-07-26

## Context

Milestone 5A gave HFX Connect a `users` table, password hashing, and a registration
endpoint that does not authenticate the caller. Milestone 5B needs to let a
registered user actually log in, stay logged in across requests without
re-submitting a password, and log out — while deciding, before writing any code,
what an "authenticated session" even means for this project: what a client holds
between requests, how it's transmitted, how it expires, how it's revoked, and how
much of Spring Security's infrastructure (if any) is required to do this safely.

Constraints carried over from Milestone 5A and its own decisions:

- No `spring-boot-starter-security` filter chain exists yet — only
  `spring-security-crypto` for `BCryptPasswordEncoder` (see
  [ADR-007](ADR-007-user-identity-and-password-hashing.md)). Endpoint-level
  authorization (locking down routes, enforcing roles) is explicitly Milestone 5C's
  responsibility, not this one's.
- The Category and Resource APIs must remain fully public and functional throughout.
- The project's real deployment target (per
  [ADR-006](ADR-006-frontend-backend-connectivity.md)) is a genuinely cross-site
  pair of origins in production — a Vercel frontend and a Render backend, different
  registrable domains, not just different ports of the same host the way local dev
  is.

## Decision

### Token Model: Short-Lived Signed JWT Access Token + Opaque Rotating Refresh Token

A successful login or refresh returns a **signed JWT access token** in the JSON
response body (15-minute default lifetime, `JWT_ACCESS_TOKEN_TTL`) and sets an
**opaque, high-entropy refresh token** as an `HttpOnly` cookie (30-day default
lifetime, `JWT_REFRESH_TOKEN_TTL`). The access token is what a future authenticated
request (Milestone 5C) would present as `Authorization: Bearer <token>` — it is
never placed in a cookie, so it is never implicitly sent by the browser and never
subject to CSRF the way a cookie-based credential would be. The refresh token is
never returned in JSON and is never readable by JavaScript, so it cannot be
exfiltrated by an XSS payload the way a JSON-body or `localStorage`-held token
could be. This is the standard, well-understood split precisely because each token
type's transport matches the threat it's actually exposed to.

**Access token: JWT, HS256, `io.jsonwebtoken` (JJWT) 0.12.x.** JJWT was chosen over
hand-rolling JWT parsing (explicitly disallowed by this milestone's brief — ad hoc
signature verification is a well-documented source of real vulnerabilities, e.g.
the historical "alg: none" and algorithm-confusion attacks) and over pulling in
`spring-security-oauth2-resource-server` (which exists specifically to *validate*
externally-issued JWTs as part of a full Spring Security filter chain — this
project isn't ready to adopt that filter chain yet, and doesn't need to: it both
issues and validates its own tokens, a narrower problem JJWT solves directly with
no Spring Security dependency at all). JJWT is actively maintained, is the
de facto standard pure-Java JWT library, and its `Jwts.builder()`/`Jwts.parser()`
API forces an explicit algorithm and a valid signature on every parse — there is no
"trust this unsigned token" code path to accidentally use.

**Claims: `sub` (user ID), `role`, `iss`, `iat`, `exp`, `jti`.** No `email` claim —
the access token doesn't need it for anything this milestone or Milestone 5C's
authorization checks require, and every unnecessary field in a token that can end
up in browser memory, logs, or a proxy's access log is unnecessary exposure. `role`
is included because Milestone 5C's authorization is explicitly the very next
milestone and will need it; including it now against a known near-term consumer is
not speculative the way an unused field would be. `jti` (a random UUID per token)
costs nothing and gives every issued token a stable identifier for log correlation,
but **this milestone does not implement an access-token revocation/blacklist** —
`jti` is not currently checked against anything. This is a real, honest limitation:
an access token, once issued, is valid until it expires no matter what happens to
its owning refresh session afterward. The 15-minute lifetime is the actual bound on
that exposure window, not a token-revocation mechanism.

**Signing secret: `JWT_SECRET`, HMAC-SHA256, externalized, with no fallback
default anywhere in tracked configuration.** Unlike `DB_PASSWORD`/
`CORS_ALLOWED_ORIGINS` (which do have working defaults in
`application.properties`, matching this project's established local-dev
convenience convention), `app.jwt.secret=${JWT_SECRET}` has deliberately no
`:default` — if `JWT_SECRET` is unset, Spring fails to resolve the property and
the application refuses to start, with a clear error naming the missing
property. This is a deliberate departure from the DB/CORS convenience
convention, corrected after initial review: a database password or CORS origin
default is low-risk (a real production database has its own required
credentials regardless of what a tracked file says), but a JWT signing secret
with a working default baked into a public repository's history is a latent
authentication bypass — if a real deployment ever forgot to set it, the
application would silently sign tokens with a secret anyone reading the source
could use to forge valid access tokens. `.env.example` ships an *obviously*
insecure placeholder value, but only as something a developer must explicitly
export — never as something `application.properties` falls back to on its own.
Separately, `Keys.hmacShaKeyFor` (JJWT's own key construction, called from
`AccessTokenService`'s constructor) rejects any secret under 256 bits (32 bytes)
immediately, so a genuinely too-short secret is caught at startup too, not just
a missing one — see `docs/architecture/backend-architecture.md`'s note on why
this project relies on that library behavior directly rather than duplicating
the check by hand. The secret is never regenerated at startup — a new random
secret every boot would silently invalidate every outstanding access token and
refresh session on every deploy, which is a worse failure mode than requiring
the operator to set one real secret
once.

### Refresh Token: Opaque, SHA-256-Hashed at Rest, Rotated on Every Use

A refresh token is 256 bits of `SecureRandom` output, Base64URL-encoded (no
padding) — not a JWT, not database-ID-derived, and not sequential. Its only
purpose is "prove you are the same client the server previously issued this to,"
which a large random value with no decodable structure does perfectly, with no
need for the parsing/signing machinery a JWT would add for no benefit here.

**Stored as a SHA-256 hash, not the raw value — but not BCrypt.** BCrypt is
deliberately slow and salted to defend *user-chosen, low-entropy* passwords against
offline brute-force guessing (see ADR-007). A refresh token is the opposite case:
256 bits of uniformly random, high-entropy data that was never chosen by a human
and can't be meaningfully "guessed" faster by an attacker who steals the *hash* —
the only realistic attack is stealing the raw token itself (from the cookie store,
a compromised client, or a logging mistake), against which hashing at rest
protects the database (a leaked `refresh_sessions` table reveals no usable tokens),
not the token's own guessability. A fast, deterministic digest (SHA-256) is the
correct tool for "is this the same secret," the same reasoning most session-token
implementations use industry-wide; BCrypt's deliberate slowness here would only
cost CPU on every single refresh request for no additional protection.
**Lookup, not manual comparison:** the presented token's hash is looked up via the
`token_hash` column's `UNIQUE` index — the database performs an exact-match lookup
the same way `normalized_email` already does, not a loop-based string comparison,
so there is no per-guess timing side channel to defend against with a
constant-time-compare utility the way there would be if comparison happened
byte-by-byte in application code.

**Rotation: every successful refresh issues a brand-new refresh token and
invalidates the one just used.** The presented session is marked `revoked_at = now()`
and linked via `replaced_by_session_id` to the newly-created session, which shares
the original session's `family_id`. This is the standard refresh-token-rotation
model: a stolen refresh token is only useful to an attacker until its legitimate
owner's next real refresh, at which point rotation invalidates the stolen copy —
*unless* the attacker uses it first, which reuse detection below is what actually
protects against.

**Reuse detection: presenting an already-`revoked_at`-set session's token again
revokes the entire `family_id`.** Concretely: if a refresh request's token hash
matches a `refresh_sessions` row that is *already* revoked (whether revoked by a
prior rotation, a prior reuse-detection event, or logout), every still-active
session sharing that `family_id` is revoked immediately, and the request fails with
`REFRESH_TOKEN_REUSED`. The scenario this defends is exactly the "stolen refresh
token" case: if an attacker captures a refresh token and uses it before its
legitimate owner does, the legitimate owner's *next* attempted refresh (using the
same, now-already-consumed token) is what's detected as reuse — at which point
every session descended from that original login is killed, forcing a fresh login
everywhere. This is strictly better than doing nothing, at the cost of one extra
indexed `UPDATE ... WHERE family_id = ?` — a worthwhile trade.

**Concurrency:** the entire rotate-or-reject sequence (lookup → validate → revoke
old → create new) runs inside one `@Transactional` method. Two genuinely
simultaneous refresh requests presenting the *same* token both attempt to update
the same already-non-revoked row; the database's own row-level locking under
`READ_COMMITTED` (this project's configured isolation level — see
`docs/database/README.md`) serializes the two updates, so only one request
observes the row as "not yet revoked" and wins the rotation — the other observes
it as already revoked once its own transaction proceeds and is correctly treated as
a reuse attempt. This is a real, if unlikely, false-positive path (two legitimate
concurrent tabs refreshing at once could trigger family-wide revocation), documented
as a known limitation rather than solved with more complex request coalescing,
which this milestone's scope doesn't justify.

### Refresh-Session Schema

`V5__create_refresh_sessions_table.sql`, `UUID` primary key (same reasoning as
`users`/`resources` — numerous, per-user, created continuously). Deliberately
minimal columns: `user_id` (FK, `ON DELETE CASCADE`), `token_hash` (unique),
`family_id`, `expires_at`, `revoked_at` (nullable), `replaced_by_session_id`
(nullable, self-referential FK), `created_at`, `last_used_at` (nullable). No
`user_agent`/`ip_address` columns — nothing in this milestone's product
requirements needs device or location tracking, and collecting it without a
present use would be exactly the kind of unjustified data collection this
project's own security posture argues against elsewhere; it can be added with its
own migration if a real need (e.g. a "your active sessions" UI) emerges.

**User deletion: cascading.** `ON DELETE CASCADE` on `user_id` — a refresh session
has no meaning or value independent of the account it authenticates, so deleting
the account should simply remove its sessions rather than orphaning them or
requiring a separate cleanup step. (No user-deletion endpoint exists yet in any
milestone; this is a forward-looking constraint the schema satisfies now rather
than needing a later migration for.)

### Cookie Policy

Name: `hfx_refresh_token`. `HttpOnly` (always — never readable by JavaScript).
`Path=/api/v1/auth` (scoped to the only routes that ever need to read it — refresh
and logout — rather than sent on every request to the API). `Max-Age` matches
`JWT_REFRESH_TOKEN_TTL`. Cleared (empty value, `Max-Age=0`) on logout.

**`Secure` and `SameSite` are environment-configured (`AUTH_COOKIE_SECURE`,
`AUTH_COOKIE_SAME_SITE`), not hardcoded, because the correct value genuinely
differs between local development and this project's actual production
deployment target.** Locally, the frontend (`localhost:3000`) and backend
(`localhost:8080`) are different *origins* but the same *site* (browsers compare
registrable domain, not port) — `SameSite=Lax` cookies are sent on
same-site cross-origin `fetch`/XHR requests, and plain HTTP is normal for local
dev, so the local defaults are `AUTH_COOKIE_SAME_SITE=Lax`,
`AUTH_COOKIE_SECURE=false`. In the production topology ADR-006 already committed
to (Vercel frontend, Render backend — genuinely different registrable domains,
i.e. cross-*site*, not just cross-origin), `SameSite=Lax` cookies are **not** sent
on cross-site `fetch`/XHR at all (only on top-level navigation), which would
silently break refresh/logout in production. This is exactly the case this
milestone's brief calls out as legitimate grounds for `SameSite=None`: production
configuration must set `AUTH_COOKIE_SAME_SITE=None` with `AUTH_COOKIE_SECURE=true`
(browsers reject `SameSite=None` without `Secure`), which is only safe because it's
paired with the explicit-origin-allowlist CORS policy below — never with a
wildcard origin.

### CORS: Credentials Enabled, Origin Allowlist Unchanged

`WebCorsConfig`'s `allowCredentials` flips from `false` to `true` — necessary for
the browser to send/receive the refresh cookie on cross-origin `fetch` calls to
`/api/v1/auth/**` at all (`credentials: 'include'` on the frontend has no effect
against a CORS policy that doesn't allow credentials). This is **not** a
weakening: the allowed-origins list stays an explicit, environment-configured
allowlist (`app.cors.allowed-origins`) with no `"*"` — the one combination Spring's
own CORS handling refuses to allow at runtime (`allowCredentials(true)` with a
wildcard origin throws), so this project was never at risk of that particular
misconfiguration. `CorsConfigurationIntegrationTest` gains a regression assertion
that the configured origin now receives `Access-Control-Allow-Credentials: true`
while an unconfigured origin still receives no CORS headers at all, same as
before.

## Alternatives Considered

- **Session cookies only (no JWT), server-side session store.** Rejected: would
  need a shared session store (Redis or a database-backed session table) for the
  access-token-equivalent check on every request, adding infrastructure this
  milestone's scope doesn't require yet, and Milestone 5C's authorization work
  benefits from a self-contained, statelessly-verifiable access token it can check
  without a datastore round trip per request.
- **Both tokens in cookies (no JSON access token).** Rejected: an access token
  meant to be sent as `Authorization: Bearer` in Milestone 5C's future protected
  requests needs to be readable by the frontend's request code, which an
  `HttpOnly` cookie deliberately prevents; a non-`HttpOnly` cookie for the access
  token would be no more protected from XSS than returning it in JSON, while adding
  cookie-specific complexity (path/domain scoping, size limits) for no benefit
  over a JSON body the client already has to read anyway.
- **Refresh token as a JWT too (self-contained, no database row).** Rejected: the
  entire point of server-side revocation (logout, reuse detection) requires the
  server to know a given refresh token's current validity independent of what the
  token itself claims — a self-contained JWT refresh token can't be revoked before
  its embedded expiration without a separate revocation-list datastore anyway, at
  which point the opaque-token-plus-database-row model is simpler and gives
  exact-match lookup instead of JWT parsing on every refresh.
- **BCrypt for refresh-token hashing, for "consistency" with password hashing.**
  Rejected — see Decision above: BCrypt's deliberate slowness defends against
  guessing a *low-entropy, human-chosen* secret; a refresh token has neither
  property, and paying BCrypt's cost on every refresh request would be pure
  overhead with no corresponding security gain.
- **`SameSite=Strict` for the refresh cookie.** Rejected: `Strict` also withholds
  the cookie on cross-site top-level navigations (e.g., a user following a link
  into the app from another site), which is unnecessary caution for a refresh
  endpoint that isn't itself a navigation target, and `Lax` already prevents the
  cookie from being sent on cross-site `fetch`/XHR the way this project's actual
  local-dev same-site-different-port setup needs it to work.
- **A distributed rate limiter (Redis-backed) for login attempts.** Rejected for
  this milestone — explicitly out of scope per the brief. Documented as a known,
  honest limitation (see the milestone document) rather than either building
  unjustified infrastructure or silently claiming brute-force protection that
  doesn't exist.

## Consequences

- Milestone 5C's authorization work can validate the `Authorization: Bearer`
  access token statelessly (signature + expiration + issuer, no datastore lookup)
  and read `role` directly from its claims — this ADR's claim set was chosen with
  that specific near-term consumer in mind.
- No access-token revocation exists: a compromised access token remains valid
  until it naturally expires (≤15 minutes), regardless of any refresh-session
  revocation that happens afterward. If a future milestone needs immediate
  access-token invalidation (not just refresh-session revocation), it will need
  its own mechanism (a short-lived denylist keyed by `jti`, or shortening the
  access-token lifetime further) — this ADR does not solve that.
- The concurrent-refresh false-positive path (two simultaneous legitimate refreshes
  triggering reuse detection) is accepted as a known limitation; if it proves
  disruptive in practice, a documented follow-up would be a short grace window
  (accepting the immediately-prior token once) rather than removing reuse
  detection entirely.
- Production deployment must set `JWT_SECRET`, `AUTH_COOKIE_SECURE=true`, and
  `AUTH_COOKIE_SAME_SITE=None` explicitly — the local-dev defaults are correct for
  local dev and actively wrong for the real cross-site production topology; this
  is documented in `.env.example` and `backend/README.md`, not left implicit.

## Implementation Note: `noRollbackFor` Did Not Prevent the Rollback — `REQUIRES_NEW` Does

`RefreshSessionService.rotate()`'s reuse-detection and account-unavailable branches
both revoke a session (or an entire family) and then throw — a write that must
survive even though the method is about to signal failure. The first
implementation used `@Transactional(noRollbackFor = {RefreshTokenReusedException.class,
AccountUnavailableException.class})` on `rotate()` itself, the standard Spring
mechanism for exactly this situation. **Verified empirically against the real
running application and the real database** (this class's own mocked unit tests
cannot exercise genuine Spring transaction demarcation at all, so they could not
have caught this) that the revocation was still being rolled back: a live
`login → refresh → present the same token again` sequence showed
`REFRESH_TOKEN_REUSED` returned correctly, but a direct `psql` inspection of
`refresh_sessions` immediately afterward showed the rotated-but-not-yet-reused
sibling session still active (`revoked_at IS NULL`) — i.e., the bulk revoke had
executed (confirmed by temporarily logging its returned row count) but did not
persist. `noRollbackFor` was not effective here for reasons not further
root-caused, given this milestone's scope; rather than trust an annotation whose
observed behavior contradicted its documented contract, the fix uses explicit,
unambiguous **programmatic** transaction control instead: a `TransactionTemplate`
configured with `PROPAGATION_REQUIRES_NEW`, invoked from `rotate()`'s own
(suspended, unaffected) transaction, so the revocation commits immediately and
independently the moment its callback returns — regardless of anything that
happens afterward, including the exception `rotate()` throws next. This is a
concrete example of why this milestone's own manual, full-stack verification step
(not just unit tests, and not just trusting a well-known annotation to behave as
documented) is required, not optional — see `docs/milestones/milestone-05b-authentication-sessions.md`'s
manual verification section for the exact reproduction.

## Honest Limitations Not Solved By This ADR

- **No rate limiting exists.** Login is not brute-force protected in any way —
  the same email/password pair can be attempted an unlimited number of times with
  no lockout, backoff, or CAPTCHA. A robust implementation needs infrastructure
  (a shared counter store surviving multiple backend instances — Redis, or
  equivalent) this milestone's scope does not include; building an in-memory
  single-instance limiter would create false confidence (it provides zero
  protection the moment more than one backend instance runs, which any real
  deployment eventually does) without being clearly labeled as such, so none was
  added. This is deferred, honestly, to a later security/deployment milestone.
- **No automated dependency-vulnerability scanning is configured in this
  project** (not introduced by this milestone, and not present in any earlier
  one either) — `./mvnw dependency:tree` was reviewed manually for this
  milestone's two new dependencies (`io.jsonwebtoken:jjwt-*` and the
  already-present `spring-security-crypto`), but no `dependency-check-maven` (or
  equivalent) plugin exists to run automatically. Adding one is a reasonable
  future improvement, out of scope here.
