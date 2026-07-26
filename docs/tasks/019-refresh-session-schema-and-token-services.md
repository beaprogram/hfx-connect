# Task 019: Refresh-Session Schema and Token Services

## Objective

Give HFX Connect its authentication-session foundation: a refresh-session database
table, an access-token (JWT) service, and an opaque refresh-token generation/rotation
service — the primitives login, refresh, and logout (Task 020) are built on top of.

## Context

Part of Milestone 5B (Login, Token Refresh, and Logout), the second of three
lettered sub-milestones splitting Milestone 5 (Authentication). Builds directly on
Milestone 5A's `users` table, password hashing, and email normalization.

## Scope

- `V5__create_refresh_sessions_table.sql` — `refresh_sessions` table (`UUID` primary
  key, `user_id` FK `ON DELETE CASCADE`, unique `token_hash`, `family_id`,
  `expires_at`, `revoked_at`, `replaced_by_session_id`, `created_at`,
  `last_used_at`).
- `RefreshSession` entity, `RefreshSessionRepository`.
- `RefreshTokenGenerator` — pure opaque-token generation (256-bit `SecureRandom`,
  Base64URL) and hashing (SHA-256 hex).
- `AccessTokenService` — JWT issuance and validation via JJWT 0.12.6 (HS256, claims
  `sub`/`role`/`iss`/`iat`/`exp`/`jti`).
- `RefreshSessionService` — session issuance, rotation, reuse detection (family-wide
  revocation), and revocation.
- `UnauthorizedException`/`ForbiddenException` base exception classes (401/403),
  and the six specific auth exceptions extending them.
- `com.hfxconnect.common.text.EmailNormalizer` — extracted from
  `RegistrationValidation` for reuse by login (Task 020).
- [ADR-008](../decisions/ADR-008-authentication-session-architecture.md) — the full
  authentication-session design.
- JJWT dependency (`jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6) added to `pom.xml` —
  no `spring-boot-starter-security`, consistent with ADR-007's narrow-dependency
  precedent.
- 36 new tests: 10 repository/database (`RefreshSessionRepositoryIntegrationTest`),
  10 token unit tests (`AccessTokenServiceTest`), 7 (`RefreshTokenGeneratorTest`),
  9 (`RefreshSessionServiceTest`).

## Out of Scope

Login/refresh/logout endpoints (Task 020), cookies, CORS changes, request-level
authorization, protected routes (Milestone 5C).

## Acceptance Criteria

- [x] `V5` creates the schema without modifying any earlier migration.
- [x] Raw refresh tokens are never persisted — only a SHA-256 hex digest.
- [x] `AccessTokenService` issues HS256-signed JWTs with exactly the documented
      claims, rejects wrong-key/expired/wrong-issuer/malformed tokens.
- [x] `RefreshSessionService.rotate` validates, rotates, and detects reuse
      correctly — verified against the real database, not just mocks (see the
      transaction-rollback discovery documented in ADR-008's implementation note).
- [x] `RefreshSessionRepository` has only the query methods actually used —
      an initially-added, never-called finder method was found and removed during
      self-review, consistent with this project's "no speculative finder methods"
      convention.

## Technical Approach

Followed the category/resource/user layering pattern (entity → repository → service),
and the established `saveAndFlush`-for-UUID-keys race-safety idiom.

The most significant discovery: `@Transactional(noRollbackFor = ...)` on
`RefreshSessionService.rotate()` did not actually prevent Spring from rolling back
the family-wide (or single-session) revocation that must survive the method
throwing `RefreshTokenReusedException`/`AccountUnavailableException` right after
performing it — confirmed only by running the real application against the real
database and inspecting `refresh_sessions` directly with `psql` between requests,
since mocked unit tests cannot exercise genuine transaction demarcation. Fixed with
explicit `TransactionTemplate`/`PROPAGATION_REQUIRES_NEW` instead of the annotation.
See ADR-008's implementation note for the full account.

## Testing Requirements

`./mvnw test`. Token unit tests cover signature/expiration/issuer validation and
claim minimality (including a black-box check that the decoded JWT payload never
contains password/email/refresh-token substrings). Repository tests exercise the
real schema's constraints and cascade-delete behavior directly against
`postgis/postgis:17-3.5`. Service tests use a mocked repository plus a real
`SimpleTransactionStatus`-backed `PlatformTransactionManager` mock so the
`TransactionTemplate` code path actually executes during the test.

## Result

Completed. 36 new tests (10 repository, 10 access-token, 7 refresh-token-generator,
9 refresh-session-service) pass. See Task 020 for the endpoint-level tests that
exercise this layer end to end.

## Related Commits

`build: add refresh-session schema and token dependencies`,
`feat: add access and refresh token services`.
