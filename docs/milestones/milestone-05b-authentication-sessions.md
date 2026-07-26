# Milestone 5B: Login, Token Refresh, and Logout

## Objective

Implement a secure authentication-session foundation: a registered user can log
in, receive a short-lived access token and a rotating refresh session, refresh
that session, and log out — building directly on Milestone 5A's user schema,
password hashing, and email normalization. Request-level authorization and
protected routes remain explicitly out of scope (Milestone 5C).

## Product Value

Login is the second of three lettered sub-milestones splitting Milestone 5
(Authentication), the same way Milestone 3 split into 3A/3B/3C. Every later
authenticated capability the product plans (saved resources, submissions,
organization management, moderation) needs a real session, not just a registered
account, to build on.

## Technical Scope

- `V5__create_refresh_sessions_table.sql` — refresh-session schema (`UUID`
  primary key, `user_id` FK `ON DELETE CASCADE`, unique `token_hash`, `family_id`,
  `expires_at`, `revoked_at`, `replaced_by_session_id`, `created_at`,
  `last_used_at`).
- `com.hfxconnect.auth` package: `RefreshSession`(+repository),
  `RefreshTokenGenerator`, `AccessTokenService`, `RefreshSessionService`,
  `AuthenticationService`, `RefreshService`, `LogoutService`, `LoginRequest`/
  `LoginResponse`, `RefreshCookieConfig`, and six new exception types
  (`AuthenticationFailedException`, `AuthenticationRequiredException`,
  `InvalidRefreshTokenException`, `RefreshTokenExpiredException`,
  `RefreshTokenReusedException`, `AccountUnavailableException`) extending two new
  base types in `common.error` (`UnauthorizedException`/`ForbiddenException`).
- `AuthController` extended with `POST /api/v1/auth/login`, `/refresh`, `/logout`.
- `com.hfxconnect.common.text.EmailNormalizer` — extracted from
  `RegistrationValidation` for reuse by login (second real consumer of the same
  normalization rule — same pattern as `SlugGenerator`'s own extraction history).
- JJWT 0.12.6 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) — access-token signing/
  validation. No `spring-boot-starter-security` — consistent with ADR-007.
- `WebCorsConfig.allowCredentials` `false → true` (required for the browser to
  send/receive the refresh cookie cross-origin).
- [ADR-008](../decisions/ADR-008-authentication-session-architecture.md) — the
  full authentication-session design.
- 76 new tests (see Testing below), bringing the backend suite to 272 total.

## Out of Scope

Frontend login/registration UI, request-level authorization, protected
Category/Resource routes, ADMIN/MODERATOR/ORGANIZATION route protection, password
reset, email verification, multi-factor/OAuth, a session-management dashboard, a
revoke-all-sessions endpoint, rate limiting (see Known Limitations), search, maps,
geospatial work. Milestone 5C.

## Design Decisions

Full rationale: [ADR-008](../decisions/ADR-008-authentication-session-architecture.md).
Summary:

- **Short-lived signed JWT access token (HS256, 15 min default) in the JSON
  response + opaque, SHA-256-hashed, rotating refresh token in an `HttpOnly`
  cookie (30-day default).** Each token type's transport matches the threat it's
  actually exposed to — the access token is never in a cookie (so a future
  protected-route filter can read it as `Authorization: Bearer`), the refresh
  token is never in JSON or reachable by JavaScript.
- **BCrypt for passwords, SHA-256 for refresh tokens — not the same hash for
  both.** BCrypt's deliberate slowness defends a low-entropy, human-chosen
  secret; a 256-bit random refresh token has neither property, so a fast digest
  is the correct, lower-overhead tool for exact-match lookup.
- **Refresh-token rotation with family-wide reuse detection.** Every successful
  refresh issues a new token and revokes the old one; presenting an
  already-revoked token again (a strong signal of a stolen token in play) revokes
  every session descended from the same original login, not just the one
  presented.
- **`spring-security-crypto`/JJWT only, still no `spring-boot-starter-security`.**
  Consistent with ADR-007 — the full starter's auto-secured-by-default filter
  chain is Milestone 5C's concern, not this one's.
- **Cookie `Secure`/`SameSite` are environment-configured, not hardcoded** — local
  dev (`Lax`, not `Secure`) and this project's actual cross-site production
  topology (Vercel/Render, per ADR-006 — needs `None`+`Secure=true`) genuinely
  need different values.
- **Unknown email and wrong password are always the exact same response, with a
  timing-mitigating dummy-hash comparison** — verified both by an automated
  Mockito interaction test and live `curl` timing behavior.

## Security Considerations

- Raw refresh tokens exist only long enough to become a `Set-Cookie` header and
  a SHA-256 hash — never logged, never persisted, never returned in JSON
  (verified by a dedicated integration test asserting the response body never
  contains `refreshToken`/`password`/`hash` substrings).
- JWT access tokens carry no password, hash, refresh-token material, or email —
  only `sub`/`role`/`iss`/`iat`/`exp`/`jti` (verified by decoding a real issued
  token's payload and asserting the absence of sensitive substrings).
- The signing secret is externalized (`JWT_SECRET`), never regenerated at
  startup, and construction fails immediately (via JJWT's own `Keys.hmacShaKeyFor`)
  for any secret under 256 bits — a genuinely too-short secret cannot silently
  produce a crackable token.
- A real transaction-management bug was found and fixed during this milestone's
  own integration testing: `noRollbackFor` did not prevent Spring from rolling
  back a security-critical revocation immediately before the exception
  signaling that revocation was thrown — see ADR-008's implementation note for
  the full account, including how it was diagnosed (live `psql` inspection
  between requests, not assumption).
- CORS credentials are enabled only for the explicit, environment-configured
  origin allowlist — never a wildcard; verified by a dedicated regression test.
- **No rate limiting exists.** Login is not brute-force protected. This is an
  honest, documented limitation (see ADR-008), not a claim of protection that
  doesn't exist.

## Acceptance Criteria

**Database**

- [x] `V5` creates the refresh-session schema; earlier migrations unmodified.
- [x] `user_id` FK enforced (`ON DELETE CASCADE`); `token_hash` unique; raw
      tokens never stored; expiration required; revocation and rotation lineage
      representable.

**Access tokens**

- [x] Signed (HS256), externalized secret, fixed algorithm, expiration/issuer
      enforced, minimal claims, no sensitive data — all verified by dedicated
      unit tests and a live decoded-payload inspection.

**Login**

- [x] Email normalized identically to registration; password verified via the
      configured `PasswordEncoder`; unknown email and wrong password
      indistinguishable in both response and timing; account status enforced;
      access token returned; refresh token set only as a cookie; raw refresh
      token never persisted; no password/token ever logged.

**Refresh**

- [x] Reads the cookie only (never body/query/path); rotates on every success;
      the old token becomes permanently unusable; missing/invalid/expired/
      revoked/reused tokens rejected with distinct, stable codes; the whole
      operation is transactional (and the write-then-signal-failure paths verified
      to actually commit, after the `noRollbackFor` discovery above).

**Logout**

- [x] Revokes the matching session, clears the cookie, is idempotent and safe
      for a missing or unknown token, and does not require an access token.

**Cookies and CORS**

- [x] `HttpOnly`, path-scoped to `/api/v1/auth`, `Max-Age` matching refresh TTL,
      environment-aware `Secure`/`SameSite`; CORS credentials enabled only for
      the explicit allowed-origin list, no wildcard introduced.

**Testing**

- [x] 76 new tests pass (10 repository, 10 access-token, 7 refresh-token-generator,
      9 refresh-session-service, 6 authentication-service, 3 refresh-service,
      3 logout-service, 27 full API integration, 1 CORS regression), alongside
      the existing 197 (272 total) — authoritative per `./mvnw clean verify`.
- [x] Registration, Category, Resource, health, and OpenAPI regression-tested
      directly and confirmed unaffected.

**Manual verification** — all performed against the real docker-compose database:

- [x] Register, log in, decode the JWT locally (claims only, no sensitive data).
- [x] Wrong password and unknown email produce identical responses.
- [x] Refresh rotates; the old cookie is rejected; presenting it again after a
      legitimate rotation revokes the newer, still-valid session too (family-wide
      reuse detection), confirmed via direct `psql` inspection between requests.
- [x] Logout revokes the session (confirmed in the database) and clears the
      cookie; refresh after logout is rejected; repeated logout stays safe.
- [x] A suspended account is rejected at both login (`403`) and mid-refresh
      (`403`, and the presented token is consumed either way).
- [x] Category/Resource APIs, health, and OpenAPI all confirmed unaffected.
- [x] Logs inspected directly — no password or raw token value ever appears.

## Known Limitations (as of Milestone 5B)

- No rate limiting — login accepts unlimited attempts (see ADR-008).
- No access-token revocation — a compromised access token remains valid until it
  naturally expires (≤15 minutes); only refresh sessions are revocable.
- No request-level authorization anywhere yet — every route, including the new
  auth endpoints themselves, remains reachable without a valid access token
  (Milestone 5C).
- No frontend integration — login/refresh/logout exist only as backend
  endpoints.
- A narrow, accepted concurrency edge case: two genuinely simultaneous
  legitimate refresh requests presenting the same token can trigger a
  false-positive family-wide revocation (see ADR-008's "Concurrency" section).
- No automated dependency-vulnerability scanning is configured in this project
  (not introduced by, or specific to, this milestone).

## Risks

| Risk | Mitigation |
|---|---|
| `@Transactional(noRollbackFor = ...)` silently failing to prevent rollback of a security-critical write, discovered only through live testing | Diagnosed via direct `psql` inspection of `refresh_sessions` between real HTTP requests against the actual running application, not assumed from the annotation's documented contract; fixed with explicit `TransactionTemplate`/`PROPAGATION_REQUIRES_NEW`, verified to persist correctly afterward with the same live reproduction — see ADR-008 |
| Cross-site cookie delivery breaking silently in production while working in local development (different `SameSite` requirements for same-site-different-port vs. genuinely cross-site origins) | Made `Secure`/`SameSite` explicit environment variables rather than hardcoding either value, and documented the exact production override required in `.env.example` and `backend/README.md`, rather than discovering it only after a real deployment |
| A speculative, never-called repository finder method (`findByFamilyIdAndRevokedAtIsNull`) was added during initial implementation, matching a schema-level relationship that "seemed useful" rather than an actual code path | Caught during this milestone's own self-review pass against the project's established "no speculative finder methods" convention; removed along with its test before this branch was pushed |

## Completion Summary

All planned Milestone 5B deliverables were completed and verified twice — once via
272 automated tests (197 pre-existing plus 76 new, spanning database, token,
service, and full HTTP-layer integration coverage, the latter against a real
database and real `BCryptPasswordEncoder`/JJWT), and again via a full manual pass
against the actual local docker-compose database, including a live reproduction of
the refresh-rotation and reuse-detection sequence with direct database inspection
between requests. A real transaction-management defect was found and fixed during
that process, not assumed away. No frontend integration, request-level
authorization, or protected routes were introduced, consistent with the
milestone's explicit scope — those remain later work (frontend integration and
Milestone 5C).
