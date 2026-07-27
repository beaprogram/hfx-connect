# Task 021: Request Authentication and Security Filter Chain

## Objective

Give HFX Connect its first real Spring Security filter chain: validate a
Bearer access token on every request, load the current account safely, and
reject unauthenticated/unauthorized requests with this project's standard
error shape — the foundation Task 022's route-level authorization rules
build on.

## Context

The first of three tasks completing Milestone 5C (Request Authentication,
Role Authorization, and Protected Frontend Routes). Builds directly on
Milestone 5B's `AccessTokenService` (unchanged) and adds
`spring-boot-starter-security` for the first time in this project — Milestone
5A/5B deliberately used only `spring-security-crypto`.

## Scope

- `spring-boot-starter-security` dependency.
- `com.hfxconnect.security.JwtAuthenticationFilter` — a `OncePerRequestFilter`
  that validates the Bearer token via the existing `AccessTokenService`,
  re-loads the account by its `sub`, checks its current `ACTIVE` status, and
  attaches a minimal `CurrentUserPrincipal` (userId/email/role) — never the
  `User` entity, never a stale role claim.
- `com.hfxconnect.security.SecurityConfig` — `SecurityFilterChain`: stateless
  session policy, form login/HTTP Basic disabled, CSRF disabled (reviewed,
  see ADR-009), the authentication filter wired ahead of
  `UsernamePasswordAuthenticationFilter`.
- `com.hfxconnect.security.ApiAuthenticationEntryPoint`/
  `ApiAccessDeniedHandler` — write this project's standard `ApiError` shape
  for 401/403, since both run outside `GlobalExceptionHandler`'s reach.
- `WebCorsConfig` converted to a `CorsConfigurationSource` bean (required for
  `HttpSecurity.cors()`); `Authorization` added to `allowedHeaders`.
- `HfxConnectApplication` excludes `UserDetailsServiceAutoConfiguration`.

## Out of Scope

Route-level authorization rules (Task 022), `/users/me` (Task 022), frontend
integration (Task 023).

## Acceptance Criteria

- [x] A valid Bearer token authenticates a request; a missing token on a
      public route has no effect.
- [x] Invalid tokens (malformed, wrong signature, expired, wrong issuer) are
      rejected — the request is simply left unauthenticated, not given a
      500 or an inconsistent error shape.
- [x] The account's current `status`/`role` are used — re-loaded from the
      database on every request, never trusted from the token's own claims.
- [x] 401/403 responses use the project's standard `ApiError` shape, with no
      stack trace, parser detail, token content, or internal class name.
- [x] No `UserDetailsService` auto-configuration warning appears at startup.
- [x] CORS preflight allows the `Authorization` header for the configured
      origin.

## Technical Approach

`JwtAuthenticationFilter` is constructed directly inside `SecurityConfig`
rather than registered as a Spring bean — a `@Component`-annotated `Filter`
would additionally be auto-registered as an ordinary servlet filter by
Spring Boot, running it a second time per request outside the security
chain. The filter never performs authorization (role checks) itself and
never queries refresh sessions — both are Task 022's and `RefreshSessionService`'s
concerns respectively, not this filter's.

## Testing Requirements

`./mvnw test`. Token-validation edge cases (missing/malformed/invalid
signature/expired/wrong issuer/deleted user/disabled account) and the
current-role-not-stale-claim guarantee are covered by
`AuthorizationMatrixApiIntegrationTest` (Task 022, since it also needs the
route matrix to exist to exercise fully). CORS regression:
`CorsConfigurationIntegrationTest`.

## Result

Completed. `spring-boot-starter-security` added; the whole `security`
package (filter, principal, config, entry point, access-denied handler)
implemented; `WebCorsConfig` converted to a `CorsConfigurationSource` bean.
See Task 022 for the route-matrix tests that exercise this layer fully.

## Related Commits

`build: add Spring Security request authentication`.
