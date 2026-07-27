# Milestone 5C: Request Authentication, Role Authorization, and Protected Frontend Routes

## Objective

Complete the blueprint's authentication stage: authenticate API requests using
Bearer access tokens, enforce role-based backend authorization on the
category/resource write endpoints, expose a safe current-user endpoint, and
add real frontend login/registration/dashboard pages with an in-memory
session and a client-side protected-route guard — while being explicit that
the guard is a UX convenience, not the security boundary the backend already
is.

## Product Value

This is the third and final lettered sub-milestone of Milestone 5
(Authentication), the same way Milestone 3 split into 3A/3B/3C. Milestones 5A
and 5B gave the product real accounts and real sessions; this milestone is
what makes those sessions *matter* — before it, every route (including the
new auth endpoints themselves) was reachable by anyone regardless of whether
they had ever logged in. Every later authenticated capability the product
plans (saved resources, submissions, organization management, moderation)
needs a real, enforced authorization boundary to build on, not just a
session.

## Technical Scope

**Backend:**

- `spring-boot-starter-security` added (Milestone 5A/5B deliberately used
  only `spring-security-crypto`) — every session/form-login/HTTP-Basic
  default it would otherwise apply is explicitly disabled.
- `com.hfxconnect.security` package: `JwtAuthenticationFilter`
  (`OncePerRequestFilter`, constructed directly rather than as a bean — see
  ADR-009), `CurrentUserPrincipal`, `SecurityConfig` (the whole route
  matrix), `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler`.
- `com.hfxconnect.user.CurrentUserController` — `GET /api/v1/users/me`,
  reusing the existing `UserResponse` DTO.
- `WebCorsConfig` converted from a `WebMvcConfigurer` to a
  `CorsConfigurationSource` bean (required for `HttpSecurity.cors()`);
  `Authorization` added to `allowedHeaders`.
- `HfxConnectApplication` excludes `UserDetailsServiceAutoConfiguration` (this
  project never uses `UserDetailsService`; without the exclusion, Spring Boot
  would auto-configure a default user with a random generated password
  printed to the console on every startup).
- [ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md)
  — the full design: why request matchers over `@PreAuthorize`, why the
  current database role/status is authoritative over the JWT's own claim,
  why account-status enforcement admits only `ACTIVE`, the CORS/CSRF review,
  and the frontend session design.
- 23 new backend tests (see Testing below), bringing the suite to 295 total.
- No new Flyway migration — `Role`/`AccountStatus` already existed on the
  `users` table since Milestone 5A.

**Frontend:**

- `lib/api/client.ts` gains `postJson`/`postNoContent` alongside the
  existing `getJson`, plus an optional `accessToken` parameter for a Bearer
  header.
- `lib/api/auth.ts` — `register`/`login`/`refreshSession`/`logout`/
  `getCurrentUser`.
- `lib/validation/schemas.ts` gains `userResponseSchema`/`loginResponseSchema`/
  `roleSchema`/`accountStatusSchema`.
- `lib/auth/auth-provider.tsx` — `AuthProvider`/`useAuth`: in-memory access
  token, mount-time session restoration via the refresh cookie, single-flight
  refresh coalescing, proactive expiry tracking.
- `components/auth/` — `login-form.tsx`, `register-form.tsx`,
  `dashboard-content.tsx`, `protected-route.tsx`, `auth-nav.tsx`.
- `app/login/page.tsx`, `app/register/page.tsx`, `app/dashboard/page.tsx`.
- `components/site-header.tsx`/`components/navigation/mobile-nav.tsx`
  updated to show Log in/Dashboard/Log out based on session state.
- 38 new frontend tests, bringing the suite to 120 total.

## Out of Scope

Password reset, email verification, MFA, OAuth/social login,
role-management API/UI, user-profile editing, a revoke-all-sessions UI, a
session-management dashboard, organization verification/ownership, resource
update/delete UI, category admin UI, saved resources, submissions, reports,
moderation, search, operating hours, maps, geospatial search, Milestone 6.

## Design Decisions

Full rationale: [ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md).
Summary:

- **The current database role/status authorizes every request — never the
  JWT's own `role` claim.** A stale-claim scenario (an administrator
  promotes or suspends an account after a token was already issued) is
  closed by re-loading the account on every authenticated request. This
  costs one extra indexed lookup per request and is accepted deliberately at
  this project's scale.
- **Request matchers, not `@PreAuthorize`.** This milestone's whole policy is
  two rules (`ADMIN` for categories; `ADMIN`/`MODERATOR`, named explicitly,
  for resources) with no per-object logic yet — a second configuration
  surface would only duplicate one policy.
- **`ORGANIZATION` cannot create resources yet**, even though the role
  exists — organization ownership/verification doesn't exist (Milestone 10),
  so granting this now would create a real, unattributable capability, not a
  conservative default.
- **Account-status enforcement admits only `ACTIVE`.** Registration has only
  ever created `ACTIVE` accounts (Milestone 5A), so this has zero effect on
  any account that exists today; it is the fail-closed default for a status
  this milestone wasn't asked to special-case, not an invented verification
  requirement.
- **Missing/invalid token and non-`ACTIVE` account both return `401
  AUTHENTICATION_REQUIRED`** — deliberately indistinguishable, for the same
  information-hiding reason login's generic failure already is.
- **Frontend access token lives only in memory** — never
  `localStorage`/`sessionStorage`/a JS-set cookie. Session restoration on
  load exchanges the `HttpOnly` refresh cookie for a fresh token; failure is
  an ordinary signed-out outcome.
- **Single-flight refresh** — concurrent refresh attempts share one in-flight
  promise, since refresh-token rotation means parallel refreshes could
  trigger a false-positive family-wide revocation.
- **The `/dashboard` guard is a client-side UX convenience, not a security
  boundary** — Next.js middleware/Proxy cannot validate this project's
  refresh cookie (it belongs to the backend's own origin, per ADR-006), so
  there is no way to authenticate the browser before this component runs.
  The backend's `SecurityConfig` is authoritative regardless.

## Security Considerations

- Every 401 (missing token, invalid token, disabled account) and every 403
  (wrong role) uses the project's standard `ApiError` shape, written by
  `ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler` — never Spring
  Security's default HTML/plain-text response, and never a stack
  trace/parser-exception detail/token content/signing-key/internal class
  name.
- CORS's `allowedHeaders` now includes `Authorization`; `allowCredentials`
  remains `true` for the explicit origin allowlist only — no wildcard, and
  Spring refuses to combine the two at startup regardless.
- CSRF protection is disabled for the API — Bearer-header authentication
  isn't vulnerable to the threat it defends against; the refresh/logout
  cookie-based endpoints were reviewed specifically (see ADR-009's "CSRF
  Review") and a narrow, accepted residual documented rather than solved
  with a framework the milestone doesn't need.
- The access token is never logged (verified: grepped a live backend's log
  output for the raw issued JWT and the raw refresh token — neither
  appears).
- No `UserDetailsService`/generated-password auto-configuration exists —
  explicitly excluded, verified by confirming no such warning appears on
  startup.

## Acceptance Criteria

**Backend authentication**

- [x] A valid Bearer token authenticates; missing, malformed, wrong-signature,
      expired, and wrong-issuer tokens are all rejected with `401
      AUTHENTICATION_REQUIRED`.
- [x] The current database role/status is used for authorization — proven
      live: a token issued as `USER`, followed by a database-level promotion
      to `ADMIN` with the token unchanged, successfully authorizes an
      `ADMIN`-only action on its next use.
- [x] An account suspended after token issuance loses access on its very
      next request, with the same still-unexpired token.
- [x] No password hash or refresh-session data is ever attached to the
      authenticated principal.

**Backend authorization**

- [x] `POST /api/v1/categories`: unauthenticated → 401; `USER`/
      `ORGANIZATION`/`MODERATOR` → 403; `ADMIN` → 201.
- [x] `POST /api/v1/resources`: unauthenticated → 401; `USER`/`ORGANIZATION`
      → 403; `MODERATOR`/`ADMIN` → 201.
- [x] `GET /api/v1/users/me`: requires authentication; returns only the safe
      fields for the caller's own account.
- [x] All previously-public `GET` routes, `health`, and OpenAPI/Swagger
      remain public and unaffected.

**Frontend**

- [x] `/login`, `/register`, `/dashboard` exist with accessible forms
      (labels, correct `autocomplete`, generic/field-level validation
      messages, loading/disabled states).
- [x] The access token is never written to `localStorage`/`sessionStorage`
      (verified by `git grep` across `frontend/src` and by a dedicated
      `AuthProvider` test spying on `Storage.prototype.setItem`).
- [x] A page reload restores the session via the refresh cookie, or lands on
      `"unauthenticated"` without error.
- [x] Logout clears frontend state and the backend session.
- [x] `/dashboard` shows only safe account fields and a logout control — no
      fabricated features.

**Testing**

- [x] 295 backend tests pass (272 inherited + 23 new) — authoritative per
      `./mvnw clean verify`.
- [x] 120 frontend tests pass (82 inherited + 38 new) — authoritative per
      `npm test`.

**Manual verification** — all performed against the real docker-compose
database and real running frontend/backend:

- [x] Full role × route matrix (`USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` ×
      category/resource creation) reproduced live via `curl`, with roles
      changed directly in the database (no role-management endpoint exists).
- [x] Stale-role-claim and disabled-account-after-issuance scenarios
      reproduced live, each confirmed by direct `psql` inspection alongside
      the HTTP responses.
- [x] Refresh rotation and reuse detection re-confirmed still work
      end-to-end (unchanged from Milestone 5B, now alongside the new filter
      chain).
- [x] CORS preflight confirmed to allow the `Authorization` header for the
      configured origin and reject an unconfigured one.
- [x] OpenAPI document confirmed to declare the `bearerAuth` security scheme
      and mark `/users/me` and the two write endpoints as requiring it.
- [x] Backend log output grepped for secrets/tokens — clean.
- [x] Milestone 4's public browsing (categories/resources GET, homepage,
      `/resources`) confirmed unaffected.

## Known Limitations (as of Milestone 5C)

- No rate limiting on any route (unchanged from Milestone 5B).
- No access-token revocation — a compromised token is valid until it
  naturally expires (≤15 minutes).
- No object-level/ownership authorization; every rule is role-based.
  `ORGANIZATION` accounts cannot create resources yet.
- The frontend's protected-route guard is UX-layer only, not a security
  boundary.
- No role-specific dashboards, creation forms, saved resources, submissions,
  moderation, or organization tooling.
- No password reset, email verification, MFA, or OAuth/social login.
- No automated dependency-vulnerability scanning; `npm audit` reports
  pre-existing transitive vulnerabilities in the frontend toolchain unrelated
  to this milestone's own changes.
- Frontend verification remained code-review- and `curl`-based — no
  browser-automation tool was available in this development environment.

## Risks

| Risk | Mitigation |
|---|---|
| Adding a full `SecurityFilterChain` for the first time could silently secure routes this milestone didn't intend to protect, or leave others open by omission | Every route matcher was written explicitly and verified both by an automated authorization-matrix test class and a live manual pass against the real database for every role × route combination, rather than relying on Spring Security's defaults alone |
| Spring Boot auto-configuring a default `UserDetailsService` with a random generated password (since this project never defines one) | Explicitly excluded `UserDetailsServiceAutoConfiguration`; confirmed no such warning appears in a real startup log |
| Existing pre-5C tests (`CategoryApiIntegrationTest`, `ResourceApiIntegrationTest`) silently breaking once writes required a token | Caught immediately by the full regression run; fixed by issuing a real `ADMIN` test account and Bearer token per test class (`TestUserFactory`), not by weakening the new authorization requirement |
| A pre-existing, order-dependent test assumption (`ResourceServiceIntegrationTest` asserting the resources table was globally empty) broke once new authorization tests added real rows to the shared Testcontainers database | Fixed the assertions to compare a before/after count instead of assuming a clean table — a genuine improvement to test isolation, not a workaround |

## Completion Summary

All planned Milestone 5C deliverables were completed and verified three
ways: 295 automated backend tests (including a dedicated authorization-matrix
class proving the stale-JWT-claim and disabled-account-after-issuance
guarantees against the real database), 120 automated frontend tests, and a
full manual pass against the real running backend/frontend/database —
including the complete role × route matrix reproduced live via `curl` and
direct database role changes, since no role-management endpoint exists to
do this any other way. HFX Connect's authentication stage (Milestones
5A-5C) is now complete: registration, login/refresh/logout, and enforced,
tested, request-level authorization.
