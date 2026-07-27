# ADR-009: Request Authentication, Role Authorization, and Frontend Session Design

## Status

Accepted — 2026-07-27

## Context

Milestone 5B gave HFX Connect a real login/refresh/logout session, but explicitly
left every route unauthenticated: no endpoint, including the auth endpoints
themselves, ever validates an `Authorization` header (see
[ADR-008](ADR-008-authentication-session-architecture.md) and
`docs/architecture/security-architecture.md`'s "What Exists Today" table).
Milestone 5C has to decide, before writing any code:

- How (and whether) to introduce `spring-boot-starter-security` for the first
  time in this project — every prior milestone deliberately avoided it (see
  [ADR-007](ADR-007-user-identity-and-password-hashing.md),
  [ADR-008](ADR-008-authentication-session-architecture.md)) because no route
  needed protecting yet.
- Where the source of truth for a request's role lives — the JWT's own `role`
  claim (set at login/refresh time) or the database row as it exists *right
  now* — given that a role can change (e.g. an admin demotes a moderator)
  between a token's issuance and its expiration.
- How the frontend holds an access token without ever letting a stored copy
  become an XSS target, while still surviving a full page reload without
  forcing a fresh login every time.
- How "the current user" is represented inside a request without dragging in
  entity state (password hash, refresh sessions) that authorization code never
  needs and should never be able to accidentally expose.

Constraints carried over from 5A/5B: `Role` (`USER`/`ORGANIZATION`/`MODERATOR`/
`ADMIN`) and `AccountStatus` (`ACTIVE`/`PENDING_VERIFICATION`/`SUSPENDED`/
`DEACTIVATED`) already exist on `User`; `AccessTokenService.validate` already
verifies signature/expiration/issuer and returns `AccessTokenClaims(userId,
role)`; `RefreshCookieConfig.COOKIE_NAME` and the whole refresh-session
mechanism are refresh-only concerns this milestone must not touch.

## Decision

### Adopt `spring-boot-starter-security`, Configured Explicitly, Stateless

Hand-rolling route-matching and 401/403 dispatch as a bespoke servlet filter
was rejected: Spring Security's `SecurityFilterChain` is the standard,
well-reviewed way to express "these paths are public, everything else needs
authentication, these specific paths also need a role" as data (an ordered
list of matchers) rather than as scattered `if` statements a future
route addition could easily miss. The starter is added, but every one of its
defaults that assumes a browser-form/session world is explicitly turned off:
`SessionCreationPolicy.STATELESS` (no `HttpSession` is ever created — every
request re-authenticates from its own Bearer token, consistent with the
access token already being self-contained per ADR-008), `formLogin().disable()`,
`httpBasic().disable()`, and no default in-memory generated password (which
Spring Security would otherwise print to the console on every boot the moment
the starter is on the classpath with no other `UserDetailsService` configured
— avoided here because this project never uses `UserDetailsService`/
`AuthenticationManager` at all; see "What This Project Does Not Use" below).

### Public/Protected Route Matrix, Expressed as Explicit Matchers

```
Public (no token required):
  POST /api/v1/auth/register, /login, /refresh, /logout
  GET  /api/v1/categories, /api/v1/categories/**
  GET  /api/v1/resources,  /api/v1/resources/**
  GET  /actuator/health
  /v3/api-docs/**, /swagger-ui/**, /swagger-ui.html   (local/dev tooling)

Protected (Bearer access token required):
  GET  /api/v1/users/me            — any authenticated ACTIVE account
  POST /api/v1/categories          — ADMIN only
  POST /api/v1/resources           — ADMIN or MODERATOR

Everything else: authenticated() by default (fail closed, not fail open).
```

This is written directly as `HttpSecurity.authorizeHttpRequests` matchers
(request-matcher style), not `@PreAuthorize`. **Why not `@PreAuthorize`:**
method security is a second, separate configuration surface
(`@EnableMethodSecurity`) that would need to coexist with the filter-chain
matchers for the same three write endpoints — two places expressing one
policy is exactly the "duplicated conflicting rules across layers" the
milestone brief warns against. Request matchers alone are sufficient here
because every rule in this milestone is expressible as "this HTTP
method+path needs this role," with no per-argument or per-entity-owner logic
(no route needs "the resource I'm updating belongs to me" — that's
Milestone 10's organization-ownership concern, explicitly out of scope now).
If a future milestone needs object-level authorization, `@PreAuthorize` can be
introduced then, for that specific need, without retrofitting these three
routes.

**ORGANIZATION accounts cannot create resources yet**, even though the role
exists — organization ownership/verification (Milestone 10) doesn't exist, so
there is no way to attribute a created resource to an organization correctly.
Granting `POST /api/v1/resources` to ORGANIZATION now would let an
organization account create resources indistinguishable from an ADMIN's,
which is a real capability this project isn't ready to support correctly, not
a conservative default — it stays 403 until Milestone 10.

### Current-Request Identity: Validate Token, Then Re-Load the Account, Every Time

The authentication filter does not treat the JWT's claims as sufficient by
themselves. On every authenticated request it: (1) validates the token via
the existing `AccessTokenService.validate` (signature/expiration/issuer —
unchanged from 5B), (2) loads the `User` row by the token's `sub` (the
account might have been deleted since the token was issued), (3) checks the
row's *current* `status` (not a claim — the account might have been
suspended since the token was issued), and (4) builds the authenticated
principal's authority from the row's *current* `role` (not the token's `role`
claim — the account's role might have been changed since the token was
issued).

**This means a real database read on every authenticated request** — a
genuine cost, and the reason JWTs are usually chosen specifically to *avoid*
a per-request datastore hit. This project accepts that cost deliberately: a
stale-role or stale-status JWT claim is a real security gap (a demoted
moderator, or a suspended account, would otherwise keep every privilege of
their old state for up to 15 minutes — the access-token lifetime), and this
project has exactly one user table behind a connection pool, not a
distributed system where a per-request lookup is prohibitively expensive.
**Do not treat the token's `role` claim as authoritative** — it remains in
the JWT (kept from ADR-008, since login/refresh responses and any future
stateless-only consumer still benefit from it being present), but the
authorization decision on every 5C-protected route uses the freshly-loaded
row, never the claim. This is the single most important authorization
decision this ADR makes, and is the direct answer to the "not just believe
the JWT" review question the milestone brief poses explicitly.

### Account-Status Policy: Only `ACTIVE` Authenticates

Milestone 5A already decided that registration creates only `ACTIVE`
accounts (`emailVerified=false`, with no way to ever flip it yet — see
`User`'s own Javadoc) — no code path in this project has ever created a
`PENDING_VERIFICATION` account. The authentication filter allows exactly one
status through: `ACTIVE`. `SUSPENDED`, `DEACTIVATED`, and (for completeness,
though currently unreachable) `PENDING_VERIFICATION` are all rejected
identically. This has zero observable effect on any account that exists
today — it is the conservative, fail-closed default for a status this
milestone was never asked to special-case, not an invented verification
requirement: no account is worse off than before, and if email verification
is built later, this is the exact line that would change to admit
`PENDING_VERIFICATION` for some reduced set of actions.

Both the missing-token case and the wrong-status case return **401
`AUTHENTICATION_REQUIRED`** — not 403 — for the same information-hiding
reason logins already use a single generic failure (per ADR-008): telling an
unauthenticated caller "your account exists but is suspended" (403, a
distinct code) would confirm the account's existence and state to anyone
holding a stale or forged-looking token, which this project's login endpoint
already goes out of its way to avoid for credentials. A 403 in this
milestone is reserved for a *successfully authenticated* caller whose role
doesn't permit a specific action (`ACCESS_DENIED`) — a categorically
different, already-identity-confirmed situation.

### Custom `AuthenticationEntryPoint` / `AccessDeniedHandler`, Not Spring's Defaults

Spring Security's built-in entry point/denied-handler produce a plain-text or
HTML body, not this project's `ApiError` shape — leaving them in place would
mean every other endpoint returns the established JSON shape except these two
failure paths, breaking the "one consistent shape across every endpoint"
rule `docs/api/README.md` already documents. Both handlers write `ApiError`
directly (matching `GlobalExceptionHandler`'s existing shape) rather than
throwing and routing back through Spring MVC's exception resolution, because
Spring Security's entry point/denied-handler run *outside* the
`DispatcherServlet`/`@RestControllerAdvice` machinery entirely — by the time
either is invoked, `GlobalExceptionHandler` is no longer in the call path for
this specific request.

### Role → Authority Naming: `ROLE_<name>`, No Hierarchy

`Role.USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` map to Spring Security
authorities `ROLE_USER`/`ROLE_ORGANIZATION`/`ROLE_MODERATOR`/`ROLE_ADMIN` —
the `ROLE_` prefix is Spring Security's own convention for
`hasRole("X")`/`hasAnyRole("X", "Y")` to work (they add the prefix
internally; using it explicitly when constructing the
`GrantedAuthority` avoids the classic double-prefix or missing-prefix
confusion). **No role hierarchy** (e.g. "ADMIN implies MODERATOR") is
configured: this milestone's entire policy is exactly two rules (`ADMIN` for
categories; `ADMIN` *or* `MODERATOR`, listed explicitly, for resources), and
a hierarchy would only replace two explicit, testable rules with one implicit
one for no real simplification — not worth the indirection for this small a
policy. If a third or fourth role-gated route appears with genuinely
overlapping needs, a hierarchy can be introduced then and tested explicitly,
per the milestone brief's own guidance.

### `CurrentUserPrincipal`: A Focused Projection, Not the JPA Entity

The authenticated principal attached to `SecurityContext` is a small record
(`userId`, `email`, `role`) — never the `User` entity itself. This mirrors
`UserResponse`'s own reasoning (never expose more than a consumer needs): no
authorization decision in this milestone needs the password hash, refresh
sessions, or any other entity-only field, and attaching the live entity would
make it trivially easy for some future controller to reach into
`authentication.getPrincipal()` and leak the hash accidentally. `/users/me`
still returns the fuller (but still safe) `UserResponse` — it does so by
re-loading the user via the principal's `userId`, the same lookup the
authentication filter itself just performed, not by broadening the principal.

### Frontend: Access Token In Memory Only; Refresh Cookie Restores It On Load

The access token lives only in a React context's in-memory state — never
`localStorage`, `sessionStorage`, or a non-`HttpOnly` cookie set from
JavaScript. This is the direct, load-bearing consequence of ADR-008's own
design: an access token in any JavaScript-readable storage is exactly as
exfiltrable by an XSS payload as a cookie without `HttpOnly` would be, which
is the entire reason ADR-008 chose *not* to put it in a cookie in the first
place. The trade-off this accepts is that **a full page reload always
discards the in-memory token** — the app calls `POST /api/v1/auth/refresh`
(which the browser sends with `credentials: "include"`, presenting the
`HttpOnly` `hfx_refresh_token` cookie) once on initial load specifically to
re-establish it, rather than reading it back from anywhere JavaScript wrote
it. A failed restoration attempt (no cookie, or an expired/reused one) simply
leaves the app in its signed-out state — it is not an error to show the
user, since "not currently logged in" is an entirely ordinary state for a
fresh visit.

### Frontend Route Guard Is UX-Layer Only — Not a Security Boundary

`frontend/AGENTS.md` and this milestone's brief both flag that Next.js
middleware cannot reliably authenticate a browser session in this project's
direct-frontend-to-backend architecture (per
[ADR-006](ADR-006-frontend-backend-connectivity.md), the browser calls the
backend directly — there is no Next.js server-side proxy layer that could
ever see or validate an `Authorization` header or the refresh cookie's
contents on the Next.js server itself). `/dashboard`'s protection is
therefore a **client-side component guard**: render a loading state while
session restoration is attempted, then either render the real content or
redirect to `/login` — implemented entirely in the browser, after the JS
bundle has already loaded. This is explicitly **not** a security boundary:
directly requesting the dashboard's HTML bypasses nothing on the backend,
because the backend never trusted the frontend's routing in the first place
— every protected API call (`/users/me`, the write endpoints) is
independently authorized server-side regardless of what the frontend
rendered or hid. The guard's only real job is preventing a flash of
protected-looking UI before a redirect a user without a session was always
going to hit anyway.

### Single-Flight Refresh, Not Naive Parallel Retries

Because refresh tokens rotate on every use (ADR-008) and a reused token
revokes its entire family, two near-simultaneous 401-triggered refresh
attempts from the same tab (e.g. two components' queries failing together)
would race: the first to complete rotates the cookie, and the second then
presents the now-already-consumed old token, triggering false-positive
family-wide revocation of the session it just legitimately established. The
frontend API client therefore coalesces concurrent refresh attempts into one
in-flight promise that every caller awaits, rather than each caller issuing
its own `POST /api/v1/auth/refresh`. Token expiry is tracked from the
login/refresh response's own `expiresIn` (seconds from issuance, converted to
an absolute expiry instant with a small clock-skew buffer) — the frontend
never decodes or trusts arbitrary fields from the JWT itself, since the
response already hands over the one number that matters.

## What This Project Does Not Use

- **No `UserDetailsService`/`AuthenticationManager`.** Both are Spring
  Security abstractions for username/password authentication *at the filter
  chain layer* — this project's actual password check happens once, inside
  `AuthenticationService.login` (unchanged since 5B), and is already
  complete before Spring Security is ever involved. Introducing a
  `UserDetailsService` here would mean re-expressing a check that already
  works, through an abstraction built for a login flow this project doesn't
  use (Spring Security never handles the login *request* — only requests
  presenting an already-issued Bearer token).
- **No `oauth2ResourceServer().jwt()`.** That configuration validates
  externally-issued JWTs against a JWKS endpoint — this project issues and
  validates its own tokens with its own symmetric secret (`AccessTokenService`,
  unchanged from 5B), which is a narrower problem a custom
  `OncePerRequestFilter` solves directly, consistent with ADR-008's original
  reasoning for choosing JJWT over that same module.
- **No CSRF token/framework.** See "CSRF Review" below.

## CSRF Review

Spring Security's CSRF protection defends session-*cookie*-authenticated
state-changing requests — the classic threat is a malicious page causing a
victim's browser to submit a request the victim's own cookie authenticates
without the victim's intent. This project's protected routes
(`/users/me`, category/resource `POST`) authenticate via the `Authorization`
header, which a cross-site page cannot set on a request it forges (unlike a
cookie, headers are never sent automatically by the browser) — the standard
CSRF threat model does not apply to them, so Spring Security's CSRF filter is
disabled for the API (`csrf(AbstractHttpConfigurer::disable)`), consistent
with every stateless-JWT-API reference configuration.

**The refresh/logout endpoints are the one place a real cookie is
involved**, and were reviewed specifically: both are `POST`-only (already
true since 5B), scoped to `SameSite=Lax` in local dev / `None`+`Secure` in
production (ADR-008), and the CORS allowlist (below) means no origin other
than the configured frontend can complete a *credentialed* cross-origin
request that would even receive the response — a bare cross-site form
POST (not using `fetch`/XHR) could still reach `/refresh` with the cookie
attached under `SameSite=Lax` (`Lax` allows top-level navigation), but its
JSON response is unreadable to the attacking page (no CORS grant, and the
navigation itself leaves the app), and a rotation forced this way still
requires the legitimate cookie to already exist and simply advances the
token rotation — it does not authenticate as, or expose data to, the
attacker. This is a narrow, accepted residual — **not** claimed to be
eliminated — documented here rather than solved with a dedicated CSRF-token
framework, which the milestone brief explicitly asks not to introduce for a
risk this limited.

## CORS: `Authorization` Header Allowed, Still No Wildcard

`WebCorsConfig.allowedHeaders` gains `Authorization` (previously only
`Content-Type`) — required for the browser to even attempt sending the
Bearer header cross-origin; without it, the CORS preflight itself would
reject the real request before it ever reached the backend. `WebCorsConfig`
is converted from a `WebMvcConfigurer.addCorsMappings` implementation to a
`CorsConfigurationSource` bean, because Spring Security's
`HttpSecurity.cors()` needs a `CorsConfigurationSource` to delegate to and
does not read `WebMvcConfigurer` registrations at all; the same bean is
wired into both Spring Security's filter chain and (via
`UrlBasedCorsConfigurationSource`'s Spring MVC integration) ordinary MVC
handling, so there remains exactly one CORS policy definition, not two that
could drift apart. The allowed-origin allowlist, `allowCredentials(true)`,
and the "no wildcard" invariant from ADR-008 are all unchanged.

## Alternatives Considered

- **Keep trusting the JWT's `role` claim, skip the per-request database
  load.** Rejected — see "Current-Request Identity" above: this is the
  single gap the milestone brief calls out most explicitly, and this
  project's scale doesn't justify accepting it for a performance win.
- **`@PreAuthorize` + `@EnableMethodSecurity` instead of request matchers.**
  Rejected for this milestone's specific policy shape — see "Public/Protected
  Route Matrix" above. Not rejected permanently; a future milestone with
  genuine object-level rules is the right trigger to introduce it.
- **A role hierarchy (`ADMIN > MODERATOR > USER`).** Rejected as premature
  for two explicit rules — see "Role → Authority Naming" above.
- **Storing the access token in a non-`HttpOnly` cookie instead of memory.**
  Rejected: equally readable by an XSS payload as `localStorage`, while
  adding cookie-specific complexity (size, path scoping, an extra
  `Secure`/`SameSite` decision) for no protective benefit over in-memory
  storage.
- **Retrying every 401 by refreshing and re-issuing the original request.**
  Considered, but the milestone brief explicitly permits deferring this in
  favor of proactive expiry tracking if it adds excessive complexity; this
  project ships proactive expiry tracking (refresh shortly before the known
  `expiresIn` elapses) plus a single controlled refresh-and-sign-out-on-
  failure path, not blind retry-on-401, keeping the single-flight logic
  simple and avoiding any risk of a retry loop.

## Consequences

- Every authenticated request costs one additional indexed primary-key
  lookup (`UserRepository.findById`) beyond what 5B required — acceptable at
  this project's scale, and the direct, deliberate cost of not trusting a
  potentially-stale JWT claim.
- A role or status change made by an administrator takes effect on the
  *next* request from the affected account, not merely after that account's
  current access token naturally expires — a real security improvement over
  trusting the claim, and a natural consequence of the per-request reload.
- The frontend cannot survive a page reload with an already-authenticated
  UI painted instantly — every reload shows a brief restoration-loading
  state first. This is treated as acceptable, not worked around with any
  form of client-side token persistence, because avoiding exactly that
  persistence is this ADR's whole point.
- No route in this project is protected by anything the frontend does. If a
  future audit ever needs to confirm a route is truly protected, the correct
  place to look is `SecurityConfig`'s matcher list and the corresponding
  backend test matrix — not any frontend code.

## Honest Limitations Not Solved By This ADR

- **No access-token revocation still exists** (unchanged from ADR-008): a
  compromised access token remains valid for up to 15 minutes regardless of
  what happens to the account afterward — the per-request status/role reload
  this ADR adds catches a suspended/demoted account on its *next* request,
  but cannot invalidate an already-issued, not-yet-expired token immediately.
- **No rate limiting** on any newly-protected route (unchanged from
  ADR-008's login-specific limitation, now also true of `/users/me` and the
  write endpoints).
- **The CSRF residual risk described above is accepted, not eliminated.**
- **No object-level/ownership authorization** — this ADR's rules are entirely
  role-based; "this resource belongs to this organization" does not exist
  as a concept yet (Milestone 10).
