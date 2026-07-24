# Task 018: User Registration Foundation

## Objective

Give HFX Connect its first persisted account type: schema, entity, password
hashing, validation, duplicate-email prevention, and a `POST /api/v1/auth/register`
endpoint — with no login, tokens, or protected routes yet.

## Context

Part of Milestone 5A (User Registration Foundation), the first of three lettered
sub-milestones splitting Milestone 5 (Authentication) the same way Milestone 3 was
split into 3A/3B/3C — registration, password hashing, and duplicate-account
prevention are substantial enough on their own (and security-sensitive enough) to
warrant their own review cycle before login/tokens (5B) and roles/authorization
(5C) build on top of them.

## Scope

- `V4__create_users_table.sql` — the `users` table (UUID primary key, `normalized_email`
  uniqueness, `role`/`status` check constraints).
- `Role`, `AccountStatus` enums (persisted as strings).
- `User` entity, `UserRepository`.
- `RegistrationValidation` — pure email/password normalization and format validation.
- `RegistrationRequest`, `UserResponse` DTOs.
- `UserConflictException`.
- `PasswordEncoderConfig` — a single `BCryptPasswordEncoder` (strength 12) bean, via
  `spring-security-crypto` only (not the full Spring Security starter — see
  [ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md)).
- `RegistrationService`, `AuthController` (`POST /api/v1/auth/register`).
- 37 new tests (11 repository/database, 8 service, 18 API integration).
- Full manual verification against the real docker-compose database.

## Out of Scope

Login, access tokens, refresh tokens/cookies, logout, password reset, email
delivery/verification, frontend registration UI, route protection, authorization
annotations, admin/organization/moderator management. See ADR-007 and the milestone
document for the account-status and role design this deliberately does not build on
top of yet.

## Acceptance Criteria

- `POST /api/v1/auth/register` returns `201` with a safe account body (no password
  or hash) for a valid, unique email/password.
- Duplicate email (including a differently-cased duplicate) returns `409 USER_CONFLICT`.
- Missing/malformed email, missing/weak/common password, and malformed JSON all
  return `400 VALIDATION_ERROR`/`400 MALFORMED_REQUEST` with no internal detail leaked.
- A `role` (or any other privilege) field submitted in the request body has no effect
  — every account is created as `USER`/`ACTIVE`/unverified regardless.
- The stored password is a genuine BCrypt hash (`$2a$12$...`), never the plaintext.
- Existing Category/Resource APIs, `/actuator/health`, and `/v3/api-docs` remain fully
  functional, unaffected by the new dependency and table.
- `./mvnw verify` passes in full (186 tests total).

## Technical Approach

Followed the category/resource layering pattern exactly (entity → repository →
service → controller → DTOs → domain exceptions), and its two established
race-safety idioms: `saveAndFlush` (not `save`) for the synchronous
duplicate-constraint catch, since `User.id` is a Hibernate-generated `UUID` like
`CommunityResource.id`; and an application-level `existsByNormalizedEmail`
pre-check backed by a real database `UNIQUE` constraint as the actual authority.

Added only `spring-security-crypto` (the `PasswordEncoder`/`BCryptPasswordEncoder`
classes), not `spring-boot-starter-security` — the full starter auto-secures every
endpoint by default, which would have broken the explicit regression requirement
that the still-unauthenticated Category/Resource APIs keep working. See ADR-007 for
the full reasoning and the account-status (`ACTIVE`, not `PENDING_VERIFICATION`)
decision.

Privilege escalation through the request body is prevented structurally, not just
by omission: `RegistrationRequest` has no `role` field for a value to bind to, and
is annotated `@JsonIgnoreProperties(ignoreUnknown = true)` so a submitted `role`
(or `status`) field is deterministically ignored rather than relying on whatever
Jackson's project-wide default happens to be.

## Testing Requirements

`./mvnw test`, `./mvnw verify`. Manual: register a valid account; inspect the
database directly to confirm a genuine BCrypt hash is stored and plaintext is
absent; attempt the same email differently cased (409); attempt a weak password
(400); attempt malformed JSON (400); attempt a `role: ADMIN` payload (created as
`USER` regardless); confirm Category/Resource APIs, health, and OpenAPI are
unaffected — all performed against the real docker-compose database with exact
commands and output recorded in the development log.

## Result

Completed. 37 new tests (11 repository, 8 service, 18 API) pass alongside the
existing 149 from earlier milestones (186 total). Manual verification against the
real database matched automated test behavior exactly.

## Related Commits

`build: add user account schema`, `feat: add user registration service`,
`feat: expose secure registration endpoint`, `test: add registration and account
constraint coverage`, `docs: document Milestone 5A user registration`.
