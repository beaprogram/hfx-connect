# ADR-007: User Primary Key, Password Hashing, and Account Status at Registration

## Status

Accepted — 2026-07-24

## Context

Milestone 5A introduces the first `users` row and needs to settle four questions
before writing the migration: what type of primary key to use, how to hash
passwords, what a newly-registered account's status should be given that no real
email-verification delivery mechanism exists yet, and how to add password hashing
without accidentally pulling in a security filter chain that would secure (or break)
the existing unauthenticated Category and Resource APIs before Milestone 5B/5C
actually builds authentication.

## Decision

**Primary key: `UUID`**, not `BIGINT IDENTITY`. Applying
[ADR-005](ADR-005-category-identifiers-and-normalization.md)'s own established
reasoning ("stable/admin-managed → numeric ID, content entities → UUID"): users are
numerous, self-registered over time by many independent actors, and — unlike
categories — a sequential integer ID would let one account holder estimate the
total user count or enumerate other accounts by incrementing an ID in a URL or
token. `CommunityResource` already set this precedent for exactly this reason.
Following `ResourceService.create()`'s own established pattern,
`UserRepository.saveAndFlush(...)` (not `save(...)`) is used where the
duplicate-email race condition must be caught synchronously, since a
Hibernate-generated UUID (like `CommunityResource.id`) is assigned in memory and
does not force an immediate `INSERT` the way `Category`'s `IDENTITY` column does.

**Password hashing: BCrypt via `spring-security-crypto`'s `BCryptPasswordEncoder`,
strength 12** — not the default strength 10. BCrypt is the well-established, modern,
adaptive hash for this purpose (self-salting, deliberately slow, tunable). Strength
12 was chosen over the default 10 as a deliberate, benchmarked trade-off: measured
locally at ~250-300ms per hash on the development machine, which is an acceptable
registration/login latency cost for a meaningfully higher brute-force cost than the
default. Argon2id (also available via `spring-security-crypto`) was considered and
rejected for now — see Alternatives.

**Only `spring-security-crypto` is added as a dependency, not
`spring-boot-starter-security`.** The crypto module ships `PasswordEncoder` and its
implementations with no Spring Security auto-configuration, no filter chain, and no
side effects on existing routes. Adding the full starter today would auto-secure
every endpoint (including the already-public `GET`/`POST /api/v1/categories` and
`/api/v1/resources`) behind Spring Security's default form-login behavior, which
would both break this milestone's explicit regression requirement (existing APIs
must keep working, unauthenticated) and implement authentication logic
(`SecurityFilterChain`, session/token handling) that is explicitly out of scope
until Milestone 5B. The full starter is deferred to whichever of 5B/5C first needs
a real filter chain.

**Account status at registration: `ACTIVE`, with `email_verified = false`.**
Email verification is intentionally deferred — no email-delivery mechanism exists
in this project yet (SMTP/transactional-email provider is not part of any milestone
scoped so far), and a fabricated verification flow (a fake token nobody can act on)
would be worse than none. Gating new accounts behind `PENDING_VERIFICATION` would
create accounts that can never leave that state without a real verification
mechanism, making the account permanently unusable — the opposite of "the current
API behavior remains usable" this decision is required to satisfy. `email_verified`
still exists as its own column (distinct from `status`) specifically so a real
verification flow can be added later — flip that one column true — without a schema
change or a status migration.

## Alternatives Considered

- **`BIGINT IDENTITY` primary key, for consistency with `categories`.** Rejected:
  users are exactly the "numerous, independently created, potentially
  publicly-referenced" case ADR-005 already identified as the UUID case, not the
  categories case.
- **Argon2id (`Argon2PasswordEncoder`) instead of BCrypt.** Considered — Argon2id is
  the more modern OWASP-recommended default for new systems with no legacy hash
  constraint. Not chosen for this milestone: BCrypt is simpler to tune correctly
  with a single `strength` parameter and has substantially more Spring-ecosystem
  precedent to verify configuration choices against; Argon2's memory/parallelism
  parameters add more ways to misconfigure it than this milestone's scope
  justifies. This can be revisited (a `DelegatingPasswordEncoder` with a versioned
  hash prefix supports migrating algorithms later without invalidating existing
  hashes) if a real need emerges.
- **`spring-boot-starter-security` now, accepting the default-secured behavior and
  explicitly permitting the existing routes.** Rejected: it would require writing a
  real `SecurityFilterChain` — the actual subject of Milestone 5B/5C — a full
  milestone early, and risks a misconfigured permit-all rule silently widening
  later once real authorization exists. Introducing the filter chain exactly when
  Milestone 5B needs it (alongside login/tokens, which give it something real to
  protect) keeps this decision and that one in the same milestone that owns the
  risk.
- **`PENDING_VERIFICATION` status with a placeholder/fake verification endpoint.**
  Rejected outright — explicitly disallowed by this milestone's brief, and would
  present a security feature that does not actually verify anything.
- **A single `active BOOLEAN` column instead of a `status` enum-as-string
  column.** Rejected: the product will need `SUSPENDED`/`DEACTIVATED` states later
  (moderation, Milestone 9) that are not simply "not active" — collapsing them into
  one boolean now would need a breaking migration later. A `VARCHAR` status column
  with a `CHECK` constraint (mirroring `resources.verification_status`) costs
  nothing extra today and avoids that migration.

## Consequences

- A real email-verification flow, if built later, sets `email_verified = true`
  without needing a `status` transition — `status` and `email_verified` are
  deliberately independent, not one collapsed field.
- `SUSPENDED`/`DEACTIVATED` status values are reserved by the database `CHECK`
  constraint now (see `V4__create_users_table.sql`) even though nothing in
  Milestone 5A can set them yet — no application code produces those values
  until moderation (Milestone 9) or account self-service exists.
- Login (Milestone 5B) must decide what an authentication attempt against a
  non-`ACTIVE` account does; this ADR does not answer that, since 5A never
  produces one.
- If Argon2id is adopted later, `spring-security-crypto`'s
  `DelegatingPasswordEncoder` is the documented migration path — existing BCrypt
  hashes keep validating (BCrypt hashes self-identify their algorithm via their
  `$2a$`/`$2b$` prefix) while new hashes use the new algorithm, avoiding a
  disruptive mass password reset.
