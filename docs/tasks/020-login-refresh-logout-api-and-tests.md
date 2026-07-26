# Task 020: Login, Refresh, and Logout API and Security Testing

## Objective

Expose Task 019's token/session services as three real endpoints —
`POST /api/v1/auth/login`, `/refresh`, `/logout` — with a secure, environment-aware
cookie policy, credentialed CORS, and full HTTP-layer test coverage including
security-regression checks.

## Context

Completes Milestone 5B, depending on Task 019's `AccessTokenService`/
`RefreshSessionService`. This is where the design becomes something an HTTP client
(eventually the frontend, in a later milestone) can actually use.

## Scope

- `AuthenticationService` (login — generic-failure/timing-mitigation logic),
  `RefreshService`, `LogoutService`.
- `LoginRequest`, `LoginResponse` DTOs; `RefreshCookieConfig`.
- `AuthController` extended with `login`/`refresh`/`logout` (alongside Milestone
  5A's existing `register`).
- `WebCorsConfig.allowCredentials` flipped `false → true` (see ADR-008) — required
  for the browser to send/receive the refresh cookie cross-origin at all.
- Environment configuration: `JWT_SECRET`/`JWT_ISSUER`/`JWT_ACCESS_TOKEN_TTL`/
  `JWT_REFRESH_TOKEN_TTL`/`AUTH_COOKIE_SECURE`/`AUTH_COOKIE_SAME_SITE`, with
  development-only defaults matching this project's established convention.
- 40 new tests: 27 full HTTP-layer (`AuthSessionApiIntegrationTest`), 6
  (`AuthenticationServiceTest`), 3 (`RefreshServiceTest`), 3 (`LogoutServiceTest`),
  and 1 new CORS-credentials regression assertion.
- Full manual verification against the real docker-compose database.

## Out of Scope

Frontend login/registration UI, request-level authorization, protected routes
(Milestone 5C), password reset, email verification, rate limiting (see ADR-008's
honest limitations section), multi-factor/OAuth.

## Acceptance Criteria

- [x] Login: unknown email and wrong password return the exact same
      `401 AUTHENTICATION_FAILED` body, and take approximately the same time (a
      dummy-hash `PasswordEncoder.matches` comparison runs even when no account
      exists — verified by a dedicated Mockito interaction test).
- [x] Login/refresh never return a refresh token in JSON; it is set only via
      `Set-Cookie`, `HttpOnly`, path-scoped to `/api/v1/auth`.
- [x] Refresh rotates on every success; the previously-presented token becomes
      permanently unusable; presenting an already-used token revokes every
      session descended from the same login (verified against the real database,
      not just mocks — see Task 019's transaction-rollback discovery).
- [x] Logout revokes the matching session, clears the cookie, and is idempotent
      and safe regardless of whether a valid session was presented.
- [x] A correct-credentials login (or an in-progress refresh) against a
      non-`ACTIVE` account returns `403 ACCOUNT_UNAVAILABLE` without revealing why.
- [x] CORS allows credentials only for the explicit configured origin; an
      unconfigured origin still receives no CORS headers at all.
- [x] Registration, Category, Resource, health, and OpenAPI all remain fully
      functional (regression-tested directly).
- [x] `./mvnw verify` passes in full — 272/272, including every test from earlier
      milestones (197 before this milestone).

## Technical Approach

`AuthenticationService.login` always calls `passwordEncoder.matches(...)` exactly
once, against either the real stored hash or a fixed, startup-computed dummy
BCrypt hash when no account exists — closing the timing side channel a naive
"skip the check for unknown emails" implementation would otherwise leave open.

Cookie `Secure`/`SameSite` are environment-configured rather than hardcoded,
because the correct values genuinely differ between local development
(`localhost:3000`/`:8080` — different origins, same site) and this project's real
production topology (Vercel/Render — genuinely cross-site, per ADR-006), which
requires `SameSite=None`+`Secure=true` for the cookie to be sent on cross-site
`fetch` at all. See ADR-008 for the full reasoning.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. Manual: registered a real account, logged in,
inspected the JWT payload (decoded locally, claims only), inspected the raw
`Set-Cookie` header's attributes, performed a live refresh-rotate-reuse-detect
sequence with `curl` and `psql` in parallel to watch `refresh_sessions` change in
real time, logged out and confirmed the cookie was cleared and the session
revoked, and confirmed Category/Resource/health/OpenAPI were unaffected — all
against the real docker-compose database, with exact commands and output recorded
in the development log.

## Result

Completed. 40 new tests (27 API integration, 6+3+3 service unit, 1 CORS
regression) pass alongside the 232 from Milestone 5A and Task 019 combined
(197 pre-existing + 36 from Task 019, minus one speculative test found and
removed during this milestone's self-review), for **272 total**.

## Related Commits

`feat: add login refresh and logout endpoints`,
`test: add authentication session security coverage`,
`docs: document Milestone 5B authentication sessions`.
