# Security Architecture

> Status: written once Milestone 5B gave the project its first real
> authentication session; updated in Milestone 5C, which added request-level
> authentication, role-based authorization, and the first protected frontend
> routes. Describes the security posture of the backend and frontend as they
> actually exist today — not a target state.

## What Exists Today

| Capability | Status |
|---|---|
| Password hashing | BCrypt, strength 12 (Milestone 5A — [ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md)) |
| Registration | `POST /api/v1/auth/register` — creates a `USER`/`ACTIVE`/unverified account only; privilege escalation structurally impossible (Milestone 5A) |
| Login | `POST /api/v1/auth/login` — generic failure response, timing-mitigated (Milestone 5B — [ADR-008](../decisions/ADR-008-authentication-session-architecture.md)) |
| Access tokens | Short-lived (15 min default) signed JWT (HS256), issued by `AccessTokenService`, **now validated on every protected request** (Milestone 5C) |
| Refresh sessions | Opaque, SHA-256-hashed, rotating, reuse-detected, revocable (Milestone 5B) |
| Logout | Revokes the matching refresh session; idempotent |
| CORS | Explicit origin allowlist, credentials enabled only for it, `Authorization` header allowed (Milestone 5C), no wildcard |
| Request-level authentication | `com.hfxconnect.security.JwtAuthenticationFilter` + `SecurityConfig` (Milestone 5C) — see below |
| Role-based authorization | `SecurityConfig`'s route matrix (Milestone 5C) — see below |
| Rate limiting | **None** — login accepts unlimited attempts |
| Access-token revocation | **None** — a compromised access token is valid until it naturally expires |
| Frontend session | In-memory access token only, refresh-cookie-based restoration (Milestone 5C) — see below |

## Authentication Model

A registered user obtains a session by logging in (or refreshing an existing
session). A session consists of two independently-transported tokens with
deliberately different lifetimes, storage, and threat models:

- **Access token** — a signed JWT, returned in the JSON response body,
  presented as `Authorization: Bearer <token>` on every authenticated
  request. Short-lived (15 minutes by default) so a leaked copy has a small
  exposure window. Never placed in a cookie.
- **Refresh token** — a 256-bit random value, set only as an `HttpOnly`
  cookie (`hfx_refresh_token`), never returned in JSON, never readable by
  JavaScript. Long-lived (30 days by default) but rotated on every use and
  revocable server-side.

See [ADR-008](../decisions/ADR-008-authentication-session-architecture.md)
for the token design, and
[ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md)
for how a presented access token is turned into an authorization decision
(Milestone 5C).

## Request Authentication (Milestone 5C)

Every request passes through `com.hfxconnect.security.JwtAuthenticationFilter`
(a `OncePerRequestFilter`, wired into `SecurityConfig`'s `SecurityFilterChain`
ahead of `UsernamePasswordAuthenticationFilter`) before reaching a controller:

1. If no `Authorization: Bearer <token>` header is present, the request
   continues unauthenticated — whether that's acceptable is entirely
   `SecurityConfig`'s route-matcher decision, not this filter's.
2. If present, the token is validated via the existing
   `AccessTokenService.validate` (signature, expiration, issuer — unchanged
   since Milestone 5B). An invalid token (bad signature, expired, wrong
   issuer, malformed) also leaves the request unauthenticated.
3. **The account is re-loaded from the database by the token's `sub`, and
   its *current* `status` and `role` are used — never the JWT's own `role`
   claim.** A suspended/deactivated account, or one that no longer exists,
   is rejected even with a technically-valid, unexpired signature. This is
   the single most important authorization decision this milestone makes;
   see ADR-009's "Current-Request Identity" section for the full reasoning,
   and `AuthorizationMatrixApiIntegrationTest` for the live-database proof
   (issuing a token as `USER`, promoting the account to `ADMIN` afterward,
   and confirming the *same, unchanged* token now authorizes an `ADMIN`
   action).
4. Only `ACTIVE` accounts authenticate — `SUSPENDED` and `DEACTIVATED` do
   not. `PENDING_VERIFICATION` is currently unreachable (registration has
   only ever created `ACTIVE` accounts since Milestone 5A) but is rejected
   too, for completeness.
5. A missing token on a protected route, an invalid token, or a
   non-`ACTIVE` account all produce the identical `401
   AUTHENTICATION_REQUIRED` response — deliberately indistinguishable, for
   the same information-hiding reason login's generic failure already is
   (revealing "this account exists but is suspended" to an anonymous caller
   would confirm the account's existence and state).

The filter never performs authorization (role checks) itself and never
queries refresh sessions — both would blur a boundary `SecurityConfig`/
`RefreshSessionService` already own cleanly.

## Role-Based Authorization (Milestone 5C)

`SecurityConfig`'s `authorizeHttpRequests` is the single place the route
matrix is expressed:

| Route | Requirement |
|---|---|
| `POST /api/v1/auth/{register,login,refresh,logout}` | Public |
| `GET /api/v1/categories`, `/api/v1/categories/**` | Public |
| `GET /api/v1/resources`, `/api/v1/resources/**` | Public |
| `GET /actuator/health` | Public |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Public (local/dev tooling) |
| `GET /api/v1/users/me` | Any authenticated, `ACTIVE` account |
| `POST /api/v1/categories` | `ADMIN` only |
| `POST /api/v1/resources` | `ADMIN` or `MODERATOR` |
| `PUT /api/v1/resources/{id}/operating-hours` | `ADMIN` or `MODERATOR` (Milestone 6B) |
| `PUT /api/v1/resources/{id}/location` | `ADMIN` or `MODERATOR` (Milestone 7A) |
| Everything else | `authenticated()` — fail closed by default |

`ORGANIZATION` accounts cannot create resources yet, even though the role
exists: organization ownership/verification doesn't exist (Milestone 10), so
there is no way to attribute a created resource to an organization
correctly — granting this now would create a real capability this project
isn't ready to support, not a conservative default.

Roles map to Spring Security authorities as `ROLE_USER`/`ROLE_ORGANIZATION`/
`ROLE_MODERATOR`/`ROLE_ADMIN`. No role hierarchy is configured — this
milestone's entire policy is two explicit rules, which a hierarchy would
only obscure. Request matchers were chosen over `@PreAuthorize` +
`@EnableMethodSecurity` specifically because every rule here is expressible
as "this method+path needs this role," with no per-object/ownership logic
yet — see ADR-009 for the full alternatives-considered discussion.

An authenticated caller whose role doesn't authorize an action receives
`403 ACCESS_DENIED` with a generic message ("You do not have permission to
perform this action.") — never the specific authorization expression or
role that was required.

## Custom Entry Point and Access-Denied Handler

Spring Security's own default 401/403 responses are plain HTML/text, not
this project's `ApiError` shape. `com.hfxconnect.security.
ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler` write the standard
shape directly — they run outside `DispatcherServlet`, so
`GlobalExceptionHandler` is never in the call path for either. Neither ever
includes a stack trace, JWT parser detail, token contents, signing-key
information, or internal class names.

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

`WebCorsConfig` now exposes a `CorsConfigurationSource` bean (previously a
`WebMvcConfigurer.addCorsMappings` implementation), because Spring
Security's `HttpSecurity.cors()` needs exactly that bean to delegate to and
does not read `WebMvcConfigurer` registrations — one CORS policy
definition, referenced from the one place (`SecurityConfig`) that now
enforces it for every request. `/api/v1/**` allows requests only from the
explicit, environment-configured origin allowlist (`CORS_ALLOWED_ORIGINS`)
— never a `"*"` wildcard. `allowedHeaders` now includes `Authorization`
(Milestone 5C), required for the browser to send the Bearer access token
cross-origin at all. `allowCredentials` remains `true` (Milestone 5B), which
stays legal only because the origin allowlist has no wildcard.

## CSRF

Spring Security's CSRF protection defends session-*cookie*-authenticated
state-changing requests against a forged cross-site request — this
project's protected routes authenticate via the `Authorization` header,
which a cross-site page cannot set on a request it forges (unlike a cookie).
The standard CSRF threat model doesn't apply to them, so CSRF protection is
disabled for the API (`csrf(AbstractHttpConfigurer::disable)`), consistent
with stateless-JWT-API reference configurations.

The refresh/logout endpoints are the one place a real cookie is involved,
and were reviewed specifically: both are `POST`-only, `SameSite`-scoped, and
the CORS allowlist means no unconfigured origin can complete a credentialed
cross-origin request that would receive the response. A narrow residual (a
bare cross-site form `POST` reaching `/refresh` under `SameSite=Lax`,
unable to read the response or authenticate as the victim) is accepted, not
eliminated — see [ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md)'s
"CSRF Review" for the full analysis.

## Account Status Enforcement

Login, refresh, and now every authenticated request check the owning
account's `status` (`com.hfxconnect.user.AccountStatus`): only `ACTIVE`
accounts may authenticate, refresh, or make an authenticated request. Login/
refresh against a non-`ACTIVE` account return `403 ACCOUNT_UNAVAILABLE`
(credentials/token were valid, but the account can't hold a session); a
request-level authentication failure (missing token, invalid token, or
non-`ACTIVE` account on an already-issued token) returns `401
AUTHENTICATION_REQUIRED` instead — see "Request Authentication" above for
why these are deliberately the same response regardless of the specific
reason.

## Frontend Session Architecture (Milestone 5C)

- The access token lives only in an in-memory React context
  (`lib/auth/auth-provider.tsx`) — never `localStorage`, `sessionStorage`,
  or a cookie set from JavaScript.
- Session restoration on page load calls `POST /api/v1/auth/refresh` with
  `credentials: "include"`, exchanging the `HttpOnly` cookie (which this
  frontend's own code can never read) for a fresh access token. Failure is
  an ordinary signed-out outcome, not an error.
- Concurrent refresh attempts are coalesced into a single in-flight request
  (single-flight), because refresh-token rotation means two simultaneous
  refreshes could trigger a false-positive family-wide revocation.
- `/dashboard` is guarded by a client-side component
  (`components/auth/protected-route.tsx`), not Next.js middleware/Proxy —
  this project's direct-frontend-to-backend architecture
  ([ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md)) means
  the refresh cookie belongs to the backend's own origin, which a Next.js
  server-side check could never validate anyway. **This guard is a UX
  convenience only; the backend's `SecurityConfig` is the actual security
  boundary**, regardless of what the frontend renders or hides.

Full design: [ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md).

## Error Responses

Authentication failures never reveal whether a specific email is
registered: unknown email and wrong password return the exact same `401
AUTHENTICATION_FAILED` body. Refresh-token failures
(`INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED`,
`AUTHENTICATION_REQUIRED`) remain distinguishable from each other — a
refresh token is a high-entropy secret only its legitimate holder could
present, a different category of information than login-email enumeration.
Request-authentication failures (missing/invalid token, disabled account)
are collapsed into one `401 AUTHENTICATION_REQUIRED`, and authorization
failures (wrong role) into one `403 ACCESS_DENIED` — see "Request
Authentication" and "Role-Based Authorization" above. See
`docs/api/README.md` for the full error-shape/status-code reference.

## Honest Limitations

- **No rate limiting.** Login accepts unlimited attempts. Deferred, as
  before, to a later security/deployment milestone.
- **No access-token revocation.** A compromised access token is valid until
  it naturally expires (≤15 minutes), independent of any account role/status
  change — the per-request database reload (Milestone 5C) catches a
  suspended/demoted account on its *next* request, but cannot invalidate an
  already-issued, not-yet-expired token immediately.
- **The CSRF residual described above is accepted, not eliminated.**
- **No object-level/ownership authorization exists.** Every 5C rule is
  role-based; "this resource belongs to this organization" isn't a concept
  yet (Milestone 10).
- **No automated dependency-vulnerability scanning is configured** in this
  project.
- **A narrow, accepted concurrency edge case** (unchanged from ADR-008): two
  genuinely simultaneous legitimate refresh requests presenting the same
  token can trigger a false-positive family-wide revocation.

## See Also

- [ADR-007: User Identity and Password Hashing](../decisions/ADR-007-user-identity-and-password-hashing.md)
- [ADR-008: Authentication Session Architecture](../decisions/ADR-008-authentication-session-architecture.md)
- [ADR-009: Request Authentication and Role Authorization](../decisions/ADR-009-request-authentication-and-role-authorization.md)
- [ADR-006: Frontend-Backend Connectivity (CORS)](../decisions/ADR-006-frontend-backend-connectivity.md)
- [Backend Architecture](backend-architecture.md)
- [Frontend Architecture](frontend-architecture.md)
- [API Documentation](../api/README.md)
