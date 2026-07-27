# Task 022: Role Authorization and Current-User API

## Objective

Enforce the actual authorization policy on top of Task 021's authentication
layer: role-gated category/resource creation, a safe current-user endpoint,
and an exhaustive test matrix proving every role against every protected
route.

## Context

The second of three tasks completing Milestone 5C. Depends on Task 021's
`SecurityConfig`/`JwtAuthenticationFilter`.

## Scope

- `SecurityConfig`'s route matrix: `POST /api/v1/categories` → `ADMIN`;
  `POST /api/v1/resources` → `ADMIN` or `MODERATOR`; `GET /api/v1/users/me`
  → any authenticated `ACTIVE` account; all previously-public `GET` routes,
  `/actuator/health`, and OpenAPI/Swagger remain public; everything else
  `authenticated()`.
- `com.hfxconnect.user.CurrentUserController` — `GET /api/v1/users/me`,
  reusing the existing `UserResponse` DTO via `@AuthenticationPrincipal
  CurrentUserPrincipal`.
- `CategoryController`/`ResourceController`/`AuthController` Javadoc and
  OpenAPI descriptions updated to reflect the new requirements (removing the
  "temporary security limitation" language from Milestones 3A/3C).
- `OpenApiConfig` — `bearerAuth` security scheme, referenced via
  `@SecurityRequirement` on each protected operation.
- `backend/src/test/java/com/hfxconnect/user/TestUserFactory.java` — builds
  a persisted test account with an arbitrary role/status, since
  registration's public API only ever produces `USER`/`ACTIVE` accounts.
- `com.hfxconnect.security.AuthorizationMatrixApiIntegrationTest` — the full
  role × route matrix, token-validation edge cases, the stale-role-claim
  proof, and the disabled-account-after-issuance proof.
- Fixed three pre-existing tests broken by the new authorization requirement
  (`CategoryApiIntegrationTest`, `ResourceApiIntegrationTest` — now issue a
  real `ADMIN` Bearer token per test; `GlobalExceptionHandlerIntegrationTest`
  — an unauthenticated request to an unmapped route is now rejected `401`
  before Spring MVC's own routing runs, so its 404 assertions now use an
  authenticated request instead).
- Fixed a pre-existing, order-dependent assumption in
  `ResourceServiceIntegrationTest` (asserted the resources table was
  globally empty after a rejected create — only ever true by incidental test
  ordering against the shared Testcontainers database) to compare a
  before/after count instead.

## Out of Scope

Frontend integration (Task 023), object-level/ownership authorization
(Milestone 10), role-management API/UI.

## Acceptance Criteria

- [x] `POST /api/v1/categories`: unauthenticated → 401; `USER`/
      `ORGANIZATION`/`MODERATOR` → 403; `ADMIN` → 201.
- [x] `POST /api/v1/resources`: unauthenticated → 401; `USER`/`ORGANIZATION`
      → 403; `MODERATOR`/`ADMIN` → 201.
- [x] `GET /api/v1/users/me` returns only the caller's own safe account
      fields; no user-ID parameter exists.
- [x] A role change made directly in the database takes effect on the
      *next* request from an already-issued token — proven live, not just
      asserted.
- [x] OpenAPI document declares `bearerAuth` and marks the three protected
      operations as requiring it, without marking any public `GET` as
      protected.
- [x] All 272 inherited backend tests still pass, alongside 23 new ones
      (295 total).

## Technical Approach

Request matchers (not `@PreAuthorize`/`@EnableMethodSecurity`) express the
whole policy in `SecurityConfig`, since every rule here is "this method+path
needs this role" with no per-object logic yet — see
[ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md)
for the full alternatives-considered discussion. `TestUserFactory` uses
`User`'s package-private full constructor (living in the same package for
exactly that reason) since there is no way to obtain a non-`USER`/`ACTIVE`
account through the public HTTP API.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. Manual: the full role × route matrix and the
stale-claim/disabled-account scenarios reproduced against the real
docker-compose database, with roles changed directly via `psql` (no
role-management endpoint exists to do this any other way) — recorded in the
development log.

## Result

Completed. 23 new tests (21 in `AuthorizationMatrixApiIntegrationTest`, 1 new
`GlobalExceptionHandlerIntegrationTest` case, 1 new
`CorsConfigurationIntegrationTest` case) pass alongside the 272 from
Milestones 3A-5B (295 total, authoritative per `./mvnw clean verify`).

## Related Commits

`feat: enforce backend role authorization`,
`test: add authentication and authorization coverage`.
