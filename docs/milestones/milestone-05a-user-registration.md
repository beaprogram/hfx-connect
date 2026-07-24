# Milestone 5A: User Registration Foundation

## Objective

Implement a secure, production-oriented registration foundation for HFX Connect:
user persistence, password hashing, server-side validation, duplicate-account
prevention, a registration API, consistent errors, OpenAPI documentation, and
meaningful automated tests. Login, tokens, refresh cookies, roles, and route
protection are deliberately not part of this milestone — see Out of Scope.

## Product Value

Registration is the entry point every later authenticated capability (saved
resources, submissions, organization management, moderation) depends on. Splitting
it out as its own sub-milestone — the first of three (5A/5B/5C), mirroring how
Milestone 3 split into 3A/3B/3C — keeps this security-sensitive slice small enough
to review properly before login/tokens (5B) and roles/authorization (5C) build on
top of it in later sessions.

## Technical Scope

- Flyway migration `V4__create_users_table.sql`.
- `Role` (`USER`, `ORGANIZATION`, `MODERATOR`, `ADMIN`) and `AccountStatus`
  (`ACTIVE`, `PENDING_VERIFICATION`, `SUSPENDED`, `DEACTIVATED`) enums, persisted as
  strings.
- JPA entity `User` (setter-free, mirrors `Category`/`CommunityResource`'s pattern —
  see `docs/architecture/backend-architecture.md`).
- `UserRepository` (two focused query methods).
- `RegistrationValidation` — pure email/password normalization and format
  validation, mirroring `ResourceValidation`'s shape.
- `RegistrationRequest`, `UserResponse` DTOs.
- `RegistrationService` — normalization, password hashing, duplicate detection
  (application pre-check + database-constraint-violation translation via
  `saveAndFlush`), transactions.
- `AuthController` — `POST /api/v1/auth/register` under `/api/v1/auth`.
- `PasswordEncoderConfig` — a single `BCryptPasswordEncoder` (strength 12) bean via
  `spring-security-crypto` only, not the full `spring-boot-starter-security`.
- [ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md) — user
  primary-key type, password hashing, and account-status design.
- 37 new automated tests across 3 test classes (up from 149 tests at the end of
  Milestone 4; 186 total).

## Out of Scope

Login, access tokens, refresh tokens/cookies, logout, password reset, email
delivery or verification, frontend registration UI, protected routes, authorization
annotations, admin/organization/moderator account management, search, operating
hours, maps, geospatial search. No `GET /api/v1/users/{id}` endpoint exists yet —
see Design Decisions for what that means for the registration response.

## Design Decisions

Full rationale: [ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md).
Summary:

- **`UUID` primary key**, not `BIGINT IDENTITY` — users are numerous and
  self-registered over time, the same reasoning ADR-005 already applied to
  `resources`, not `categories`.
- **BCrypt, strength 12**, via `spring-security-crypto` only (not
  `spring-boot-starter-security`) — gets a modern adaptive password hash without
  auto-securing the existing unauthenticated Category/Resource routes a full
  milestone before Milestone 5B/5C actually needs a real filter chain.
- **Newly-registered accounts are `ACTIVE` with `emailVerified = false`**, not
  `PENDING_VERIFICATION` — no email-delivery mechanism exists yet, and gating new
  accounts behind a status that can never be left without one would make them
  permanently unusable. `emailVerified` exists as an independent column precisely so
  a real verification flow can flip it later without a schema or status change.
- **No `Location` response header on `POST /api/v1/auth/register`** — unlike
  Category/Resource creation, there is no `GET /api/v1/users/{id}` endpoint in this
  milestone to point one at, and fabricating a route that doesn't exist would be
  worse than omitting the header.
- **Privilege escalation prevented structurally, not by convention** —
  `RegistrationRequest` has no `role`/`status` field at all (there is no value for
  one to bind to), and is `@JsonIgnoreProperties(ignoreUnknown = true)` so a
  submitted `role` is deterministically ignored rather than depending on
  project-wide Jackson defaults.

## Security Considerations

- Passwords are never stored in plaintext; only a BCrypt hash (`$2a$12$...`,
  strength 12) is persisted — verified directly against the real database in
  manual testing, not just asserted by test code.
- Passwords and password hashes are never logged (no logging statement in this
  milestone's code references either) and never appear in an API response
  (`UserResponse` has no field that could carry either).
- Email addresses are normalized (trimmed, lowercased) before the uniqueness check
  and before the stored `normalized_email` uniqueness constraint, preventing
  `User@Example.org` and `user@example.org` from registering as two accounts.
- Email uniqueness is enforced at the database level (`users_normalized_email_key`)
  as the race-safe authority; the application-level `existsByNormalizedEmail`
  pre-check exists only to produce a fast, clear `409` in the common case — see
  `docs/architecture/backend-architecture.md`'s "Database Constraints Are
  Authoritative" section, which this milestone follows exactly.
- The registration endpoint cannot be used to create `ADMIN`, `MODERATOR`, or
  `ORGANIZATION` accounts — verified both by code inspection (no code path accepts
  a caller-supplied role) and by an automated test and a manual `curl` request that
  submits `"role":"ADMIN"` and confirms the created account is `USER` regardless.
- No access token, refresh token, or session is created by registration — a
  registered account is not an authenticated one until Milestone 5B exists.
- Error responses never leak stack traces, SQL, table names, or internal class
  names — verified by a dedicated integration test assertion on the malformed-JSON
  response body, matching the existing project-wide pattern.

## Acceptance Criteria

**Database**

- [x] `V4__create_users_table.sql` creates the `users` table without modifying any
      earlier migration.
- [x] `normalized_email`, `password_hash` are `NOT NULL`; `normalized_email` is
      `UNIQUE`.
- [x] `role`/`status` are constrained to a fixed, valid set of string values via
      `CHECK` constraints — persisted as strings, never ordinals.
- [x] Hibernate schema validation succeeds at startup (`ddl-auto=validate`).

**Domain**

- [x] `User` entity maps cleanly to the migration's columns; no setters beyond the
      entity's actual lifecycle.
- [x] `UserRepository` has two focused methods, no speculative ones.
- [x] DTOs are explicit; the entity is never returned from or accepted by the API.
- [x] `AuthController` contains no business logic.
- [x] `RegistrationService` owns normalization, hashing, duplicate handling,
      transactions, and DTO mapping.
- [x] Duplicate conflicts are handled at both the application level (fast, clear
      error) and the database level (authoritative, race-safe via `saveAndFlush`).

**API**

- [x] `POST /api/v1/auth/register` — `201`, safe response body (no password/hash).
- [x] `400 VALIDATION_ERROR` for missing/malformed email, missing/weak/common
      password, and malformed JSON.
- [x] `409 USER_CONFLICT` for a duplicate (including differently-cased) email.
- [x] A submitted `role`/privilege field never changes the created account's role.
- [x] Error shape is consistent with every other endpoint in the project.
- [x] OpenAPI documents the endpoint (verified live and by test).

**Testing**

- [x] Repository/database tests pass (11 tests, real PostgreSQL/PostGIS via
      Testcontainers).
- [x] Service tests pass (8 tests, mocked repository/password encoder).
- [x] API integration tests pass (18 tests, full HTTP layer, real database, real
      `BCryptPasswordEncoder`).
- [x] Database constraints are tested directly (raw SQL, not only through the
      service).
- [x] `./mvnw verify` passes — 186/186, including every test from earlier
      milestones (149 before this milestone).
- [x] No existing migration modified; Category/Resource APIs remain functional
      (regression-tested directly in `UserApiIntegrationTest`).

**Manual verification** — all performed against the real docker-compose database:

- [x] Database container healthy; backend starts; Flyway applies V4 against an
      already-V1/V2/V3 database.
- [x] Registration succeeds; database inspection confirms a genuine BCrypt hash
      (`$2a$12$...`) is stored and plaintext never appears.
- [x] Differently-cased duplicate email → `409`.
- [x] Weak password → `400`.
- [x] Malformed JSON → `400`, no internal detail leaked.
- [x] Submitting `"role":"ADMIN"` still creates a `USER` account.
- [x] Existing Category and Resource endpoints, `/actuator/health`, and
      `/v3/api-docs` all remain functional.

**Documentation** — schema, API, architecture, ADR, milestone, task,
development-log, and career-evidence documents all updated; see Documentation
Requirements below.

**Git**

- [x] Branch contains only Milestone 5A work.
- [x] Commits are coherent, conventional, and reviewed via `git diff --staged`
      before each one.
- [x] No secrets tracked; no generated output tracked.

## Documentation Requirements

`docs/api/README.md`, `docs/database/README.md`,
`docs/architecture/backend-architecture.md`,
[ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md) (new), this
milestone document, `docs/tasks/018-user-registration-foundation.md` (new),
a development-log entry, `docs/development-workflow.md` (Milestone 5 roadmap row
split into 5A/5B/5C), root `README.md`, and career-evidence/interview-notes updates.

## Known Limitations (as of Milestone 5A)

- No login, tokens, or sessions exist — a registered account cannot yet
  authenticate (Milestone 5B).
- No role-based authorization or protected routes exist — every endpoint, including
  the ones from earlier milestones, remains unauthenticated (Milestone 5C).
- No email-verification delivery mechanism exists — `emailVerified` is always
  `false` after registration, by design (see ADR-007), not a bug.
- No password-reset, account-update, or account-deletion endpoint exists.
- No `GET /api/v1/users/{id}` (or any other read) endpoint exists yet — only
  registration is exposed.

## Risks

| Risk | Mitigation |
|---|---|
| Adding password hashing without accidentally pulling in Spring Security's default-secured filter chain, which would break the existing unauthenticated Category/Resource APIs this milestone must not regress | Added only `spring-security-crypto` (the `PasswordEncoder` classes), not `spring-boot-starter-security` — verified empirically with `UserApiIntegrationTest.categoryApiRemainsFunctionalAlongsideRegistration`/`resourceApiRemainsFunctionalAlongsideRegistration`, both against the real database, not assumed |
| `User.id` being a Hibernate-generated `UUID` (like `CommunityResource.id`, unlike `Category.id`) means a plain `save()` would not reliably surface the duplicate-email race condition synchronously | Used `saveAndFlush`, the same fix `ResourceService.create()` already established for exactly this reason — and caught a real test bug during development where a repository test used plain `save()` and the constraint-violation assertion silently failed to trigger until switched to `saveAndFlush` |
| A locally native PostgreSQL install already listening on port 5432 (pre-existing on this machine, previously undocumented as encountered in this specific session) intercepted the backend's connection to the docker-compose database during manual verification, surfacing as `role "hfx_connect" does not exist` | Diagnosed via `lsof -nP -iTCP:5432 -sTCP:LISTEN` per `backend/README.md`'s already-documented troubleshooting entry for this exact symptom, then verified with the documented `POSTGRES_PORT`/`DB_PORT` override rather than guessing |

## Post-Milestone Security Correction — 2026-07-24

Before merging, a pre-merge security review found that the password-length policy
was enforced with `password.length() > 72` — a Java `char` count, not a byte count.
BCrypt's underlying algorithm accepts at most 72 **bytes**; a Unicode password with
72 or fewer characters made of multibyte characters (accented letters, CJK
characters, emoji, ...) could exceed 72 UTF-8 bytes while passing the old check,
reach `BCryptPasswordEncoder.encode(...)` uncaught, and throw
`IllegalArgumentException` there — which falls through to a generic `500
INTERNAL_ERROR` rather than the intended `400 VALIDATION_ERROR`. Confirmed
empirically (a real `BCryptPasswordEncoder` call with such a password did throw)
before fixing. Corrected to compare the actual UTF-8-encoded byte length; see
ADR-007's 2026-07-24 correction for full detail, alternatives, and reasoning.

The same review also asked whether privilege-bearing fields (`role`, `status`,
`emailVerified`, `passwordHash`) submitted in the registration request body should
be rejected with `400` instead of silently ignored. Strict rejection
(`@JsonIgnoreProperties(ignoreUnknown = false)`) was implemented and verified
empirically to have no effect on this project's Jackson 3.x/Spring Boot 4.1 stack —
achieving it would require a global Jackson configuration change affecting the
Category and Resource APIs too, out of scope for this focused fix. The original
ignore-and-discard behavior was kept (privilege escalation remains structurally
impossible regardless) and is now explicitly documented and tested for all four
fields — see ADR-007's correction and `UserApiIntegrationTest`.

11 new tests were added by this correction (5 in the new `RegistrationValidationTest`
for the byte-limit fix; 6 net new in `UserApiIntegrationTest` — 2 for the byte limit
at the full-API level, and the single old role-escalation test replaced by 5 that
document and lock in the ignore-and-discard privilege-field behavior for `role`,
`status`, `emailVerified`, and `passwordHash` individually plus a no-leakage check),
bringing the backend suite from 186 to **197**, per `./mvnw clean verify`'s
authoritative report.

## Completion Summary

All planned Milestone 5A deliverables were completed and verified twice — once via
automated tests (repository/database, service, and API integration, either against
the real `postgis/postgis:17-3.5` image or a real `BCryptPasswordEncoder`) alongside
the full pre-existing suite, and again via a full manual pass against the actual
local docker-compose database, including direct database inspection to confirm
password hashing and the absence of any plaintext. A pre-merge security review
caught and fixed a real password-byte-limit defect (see the correction above) before
this branch was merged. No login, tokens, roles, or protected routes were
introduced, consistent with the milestone's explicit scope — those remain 5B and 5C.
