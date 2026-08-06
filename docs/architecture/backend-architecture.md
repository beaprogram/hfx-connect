# Backend Architecture

> Status: written once a real domain slice (Category, Milestone 3A) established the
> pattern in practice. Describes what the codebase actually does, and is the
> reference every later domain (resources, organizations, events, ...) should follow
> for consistency, not a new design each time. Updated in Milestone 3B, which added a
> second domain (`resource`) and, deliberately, no controller for it yet, and again in
> Milestone 3C, which added that controller — see "Business Layer Can Precede the HTTP
> Layer" below. Updated again in Milestone 5A, which added a third domain (`user`)
> and the project's first security-adjacent dependency — see "Adding a
> Security-Adjacent Capability Without Adding Security's Auto-Configuration" and
> "Preventing Privilege Escalation Structurally, Not by Convention" below. Updated
> again in Milestone 5B, which added a fourth domain (`auth`) and a real
> transaction-management discovery — see "A Write That Must Survive an Exception
> Needs `PROPAGATION_REQUIRES_NEW`, Not `noRollbackFor`" below. Updated again in
> Milestone 5C, which added a fifth package (`security`, not a domain in the
> same sense as the others — it has no entity of its own) and this project's
> first real `SecurityFilterChain` — see "Request Authentication and
> Authorization: The `security` Package" below. Updated again in Milestone
> 6A, which added public keyword search and, with it, this project's first
> query consolidating two independent optional filters into one — see
> "Consolidating Independent Optional Filters Into One Query" below.
> Updated again in Milestone 8A, which added a sixth domain
> (`savedresource`) and this project's first genuinely private,
> per-account data — see "Focused Entities Over Generic Frameworks"
> below. Updated again in Milestone 8B, which added a seventh and
> eighth domain (`resourcesubmission`, `correctionreport`) and a real
> Hibernate flush-ordering discovery — see "Flush Ordering Within a
> Single Transaction Is Not Call Order" below.

## Layering

Each domain (`category`, `resource`, and later `event`, etc.) is one package under
`com.hfxconnect`, containing:

| Class | Responsibility |
|---|---|
| `{Domain}` (entity) | JPA mapping only. No setters beyond what's needed for the entity's actual lifecycle — see "No Update Endpoint Yet" below. |
| `{Domain}Repository` | `JpaRepository` extension with only the query methods the domain's service actually needs — no speculative finder methods. |
| `{Domain}Service` | All business logic: input normalization, validation the database can't express, duplicate/conflict handling, transaction boundaries, mapping entities to response DTOs. |
| `{Domain}Controller` | HTTP concerns only — routing, status codes, the `Location` header. No business logic. |
| `{Domain}CreateRequest`, `{Domain}Response`, `{Domain}PageResponse` (records) | The public API contract. Never the entity itself — see "DTOs, Not Entities" below. |
| `{Domain}NotFoundException`, `{Domain}ConflictException` | Domain-specific exceptions extending the shared `com.hfxconnect.common.error` base types, so they're handled by the one global exception handler automatically. |

Controllers depend on services; services depend on repositories. Nothing skips a
layer (controllers never call repositories directly), and nothing points backwards
(repositories don't know about services or DTOs).

## DTOs, Not Entities

JPA entities are never returned from a controller and never accepted as a
`@RequestBody`. Every endpoint has an explicit request/response record. This means:

- The database schema and the public API contract can change independently.
- Internal-only fields (like `Category.normalizedName`, which exists purely to
  enforce uniqueness) never leak into a response.
- Bean Validation annotations live on the request DTO, not the entity — the entity
  doesn't need to defend against invalid data it will never actually receive, because
  the service only ever constructs it from already-normalized, already-validated
  values.

## No Update Endpoint Yet — No Setters

`Category` and `CommunityResource` have no general-purpose setters. Each gains only
the specific, focused mutation methods a real, current need justifies — not generic
ones added speculatively ahead of time. `CommunityResource` has `updateDetails(...)`
and `deactivate()` because Milestone 3B's service layer genuinely needs both.
`Category` gained `deactivate()` in Milestone 3B — not for its own API (it still has
no update/deactivate endpoint), but because the `resource` domain's own tests
genuinely needed a way to produce an inactive category and none existed. Neither
entity has a full field-by-field setter API, and neither will until a real update
endpoint is built that needs one.

## Centralized Error Handling

`com.hfxconnect.common.error` defines the error-handling pattern every domain reuses:

- `ApiException` (abstract) carries an HTTP status and a stable string code.
  `NotFoundException` (404), `ConflictException` (409), `BadRequestException`
  (400), `UnauthorizedException` (401), and `ForbiddenException` (403 — added in
  Milestone 5B, the first domain to need "authentication succeeded but the
  account isn't permitted" as distinct from "we don't know who you are") are the
  concrete bases domain exceptions extend.
- `ValidationException` is the exception form of a validation failure that Bean
  Validation annotations can't express (for example, "the name isn't blank, but
  normalizes to an empty slug") — it carries the same `fieldErrors` shape a failed
  `@Valid` request produces, so the client sees one consistent validation error shape
  regardless of which layer caught the problem.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) is the *only* place that
  translates exceptions into HTTP responses. Adding a new domain never means adding a
  new `@ExceptionHandler` method for routine cases — only `ApiException` subclasses
  need to exist; the generic handler picks them up automatically.

`GlobalExceptionHandler` also handles Spring's own `NoResourceFoundException`
(returning `404 NOT_FOUND`) — added in Milestone 3B after discovering an unmapped
route (`GET /api/v1/resources`, before any resource controller existed) fell through
to the generic handler and incorrectly returned `500`. This is genuinely global, not
resource-specific: it now applies to any path that matches no controller and no
static resource, for every domain.

See `docs/api/README.md` for the resulting response shape and stable error codes.

## Database Constraints Are Authoritative, Application Checks Are for Better Errors

Every uniqueness rule enforced by the application (for example,
`CategoryRepository.existsByNormalizedName`) is backed by a real database `UNIQUE`
constraint. The application-level check exists to produce a fast, clear error message
in the common case; it does not close the race window between two concurrent
requests. When the database constraint is what actually rejects a concurrent
duplicate (a `DataIntegrityViolationException`), the service catches it and
translates it into the same `409 CATEGORY_CONFLICT` shape the pre-check would have
produced — see `CategoryService.create()`. A service is never trusted to be the sole
guarantor of a uniqueness rule the database can enforce directly.

## Pagination

List endpoints accept plain `page`/`size` query parameters (not Spring's `Pageable`
auto-binding), validated explicitly against a documented maximum
(`CategoryService.MAX_PAGE_SIZE`/`ResourceService.MAX_PAGE_SIZE`), and return an
explicit page DTO rather than serializing Spring's `Page` type directly. This was a
deliberate choice over `Pageable` auto-binding specifically so invalid input (negative
page, oversized page size) produces the project's own consistent `400
INVALID_PAGINATION` error instead of whatever Spring's default resolver does with
out-of-range values.

Resources additionally accept a `sort` parameter, but not as free-form Spring `Sort`
binding either: `ResourceService` validates it against a fixed allowlist (`name`,
`createdAt`) and rejects anything else with `400 INVALID_SORT`
(`InvalidSortException`). The same reasoning as pagination applies — arbitrary
caller-specified sort would leak internal field names into the public contract and let
callers request sorts with no index support; categories don't have this parameter at
all yet simply because nothing has needed it, not because of a different design
philosophy.

## Business Layer Can Precede the HTTP Layer

`resource` had a complete entity/repository/service layer with no `{Domain}Controller`
at all for one full milestone (3B) — `ResourceService` was exercised directly by
integration tests, not through HTTP. Its business-layer create/update input and
read-model types (`CreateResourceCommand`, `UpdateResourceCommand`, `ResourceDetails`,
`ResourcePage`) were plain records, not `{Domain}CreateRequest`/`{Domain}Response` HTTP
DTOs — there was no `@RequestBody`/`@Valid` boundary to design them around yet.

Milestone 3C added `ResourceController`, confirming this played out exactly as
planned: new HTTP-facing DTOs (`ResourceCreateRequest`, with Bean Validation
annotations; `ResourceResponse`; `ResourceSummaryResponse`; `ResourcePageResponse`)
were added *alongside* the existing business-layer types, converting to/from them
(`ResourceCreateRequest.toCommand()`, `ResourceResponse.from(ResourceDetails)`) —
`CreateResourceCommand`, `ResourceDetails`, etc. did not change shape retroactively.
Note that `ResourceController` still doesn't expose `ResourceService.update`/
`deactivate` — a business-layer method existing and being fully tested does not
obligate a controller to expose it; that remains its own scope decision per endpoint,
made when (and if) a real HTTP need exists.

Not every business-layer type became an HTTP DTO one-for-one: `ResourceResponse`
embeds a `CategorySummaryResponse` (id/name/slug) built from
`ResourceDetails.categoryName`/`categorySlug` fields that exist specifically to
support that embedding — see "Fetch-Join to Avoid N+1..." below — while
`ResourceSummaryResponse` (used only in list results) is deliberately smaller than
`ResourceDetails`, omitting fields that only matter once a specific resource has been
opened. The business-layer/HTTP-layer split is real, not just a formality: they are
allowed to diverge in shape when the presentation need differs from the domain need.

## Fetch-Join to Avoid N+1 When a Response Embeds a Lazy Association

`CommunityResource.category` is `FetchType.LAZY`. Calling `.getId()` on a lazy proxy
is free (JPA proxies know their own ID without a query), but Milestone 3C's
`ResourceResponse`/`ResourceSummaryResponse` also need `category.getName()`/
`getSlug()` — calling those on an uninitialized proxy would trigger one extra query
*per resource*, turning a single paginated list request into 1+N queries. Rather than
accept that or reach for an unrelated caching layer, `ResourceRepository` has explicit
`JOIN FETCH` query variants (`findByIdWithCategory`,
`findBySlugAndActiveTrueWithCategory`, `findByActiveWithCategory`,
`findByCategoryIdAndActiveWithCategory`) used specifically by `ResourceService`'s
public read methods (`getActiveById`, `getActiveBySlug`, `listActive`,
`listActiveByCategory`) — a `ManyToOne` fetch join never multiplies result rows, so
it's safe to combine with `Pageable`, unlike a `OneToMany`/`ManyToMany` fetch join
would be. The plain (non-fetch) repository methods remain for paths that never touch
`category`'s name/slug (`create`'s duplicate check, `update`/`deactivate`'s lookup by
ID).

## Shared Pure Utilities Are Extracted on Second Use, Not Preemptively

`SlugGenerator` (`com.hfxconnect.common.text`) started as `category.CategorySlugGenerator`
(package-private, Milestone 3A). It moved to `common.text` (public) in Milestone 3B
only once `resource` needed byte-for-byte the same algorithm — a genuine second
consumer, not a speculative generalization. The same reasoning applies to any future
shared logic: duplicate it locally until a second real domain needs it, then extract.

The same pattern played out again in Milestone 5B: email normalization (trim,
lowercase) started inline inside `user.RegistrationValidation`. It moved to
`common.text.EmailNormalizer` (public) only once `auth.AuthenticationService`
(login) needed byte-for-byte the same rule — login and registration must normalize
identically, or `User@Example.org` could register successfully but fail to log
back in as `user@example.org`.

## Focused Entities Over Generic Frameworks

`SavedResource` (Milestone 8A) has exactly the fields the saved-resources
feature needs (`userId`, `resource`, `createdAt`) and nothing more — no
generic `Bookmarkable`/`SavedItem` base entity, no polymorphic
target-type column anticipating a second kind of "saved thing" that
doesn't exist yet. It also has no bidirectional collection on `User` or
`CommunityResource`: the relationship is queried exclusively from the
`SavedResource` side (`findByUserId`-style methods), so a `@OneToMany`
back-reference on either owning entity would only add a lazy-loading
footgun (every `User`/`CommunityResource` load would need to remember
never to touch it) for a navigation direction nothing in the codebase
actually uses — the same reasoning already applied to
`ResourceOperatingHours.resourceId` (Milestone 6B) and
`RefreshSession.userId` (Milestone 5B). See
[ADR-014](../decisions/ADR-014-saved-resources-design.md) for the full
rationale. The general pattern: build the narrowest entity the current
feature needs; let a second, genuinely similar feature (if one ever
arrives) justify a shared abstraction, rather than designing one in
advance for a single known use case.

## Cross-Domain Dependencies Are Direct, Not Hidden Behind an Abstraction

`ResourceService` depends directly on `CategoryRepository` (not a `CategoryService`
method, and not an abstraction layer between domains) to check that a resource's
category exists and is active. This is a deliberate, minimal choice: `resource`
legitimately needs to read `category` data, or the entire enforcement of Milestone
3B's most important business rules would be theatrical rather than real, and the
project has no repeated pattern yet that would justify a shared cross-domain
abstraction. If a third domain develops the same kind of dependency on `category` (or
on `resource`), that repetition — not this first instance — is the signal to consider
whether an abstraction is actually warranted.

That repetition arrived in Milestone 8B: `ResourceSubmissionService`
depends directly on `CategoryRepository` (the identical "does this
category exist and is it active" check `ResourceService` already
performs), and `CorrectionReportService` depends directly on
`ResourceRepository` (the identical "does this resource exist and is
it active" check `SavedResourceService` already performs, Milestone
8A). Even with a third and fourth real instance of this exact pattern
now in the codebase, no shared abstraction was introduced — each
check is two lines calling a repository method that already exists,
and a "resource visibility" or "category visibility" service would add
an indirection layer without removing any real duplication. The
signal to actually build one would be the check's *logic* diverging
into something nontrivial enough to be worth centralizing, not merely
its being called from a fourth place.

## `saveAndFlush`, Not `save`, When the Race-Condition Catch Must Be Synchronous

`CategoryService.create()` can safely use `categoryRepository.save(category)` and
catch `DataIntegrityViolationException` synchronously because `Category.id` is
`GenerationType.IDENTITY` — Hibernate has no choice but to execute the `INSERT`
immediately inside `save()` to learn the database-assigned id. `CommunityResource.id`
is a Hibernate-generated `UUID`, assigned in memory before persistence; Hibernate has
no such forcing requirement and may defer the physical `INSERT` to a later flush,
which would happen *after* a plain `save()` call's try/catch has already exited.
`ResourceService.create()` therefore uses `resourceRepository.saveAndFlush(resource)`
specifically where it needs to catch the constraint violation synchronously. Any
future `UUID`-keyed entity needing the same synchronous-conflict-catch pattern should
use `saveAndFlush` for the same reason, not `save`.

`SavedResourceService.save()` (Milestone 8A) also uses `saveAndFlush` even
though `SavedResource.id` is `GenerationType.IDENTITY` (like `Category`,
not `CommunityResource`) — for `IDENTITY` entities, plain `save()` already
forces a synchronous `INSERT` to learn the assigned id, so `saveAndFlush`
is not strictly required here the way it is for `ResourceService`. It was
kept anyway for this specific call site: the whole point of the
surrounding try/catch is to guarantee the constraint violation surfaces
*before* the method returns, and stating that guarantee explicitly at the
call site (rather than relying on a reader already knowing `IDENTITY`'s
flush timing) was judged clearer than the small redundancy costs.

## Flush Ordering Within a Single Transaction Is Not Call Order

`ResourceSubmissionService.withdraw()`/`CorrectionReportService.withdraw()`
(Milestone 8B) both call `saveAndFlush` immediately after mutating the
entity, rather than leaving the change to Hibernate's implicit
auto-flush. This was discovered necessary, not assumed: a service-layer
test chained "withdraw a submission" immediately followed by "resubmit
the identical name/category" inside one shared test transaction, and
the resubmission unexpectedly hit the duplicate-pending unique
constraint — even though the withdrawal (an `UPDATE`) had already
executed, in program order, before the resubmission (an `INSERT`).
Hibernate's flush action queue does not preserve call order across
different kinds of pending changes; by default it processes entity
*insertions* before entity *updates* within one flush, regardless of
which was dirty-checked or persisted first in the calling code. In a
single shared session (this specific test's own transactional setup;
in production, two genuinely separate HTTP requests would each get
their own transaction and never share a flush at all), the new row's
`INSERT` could physically reach the database before the withdrawal's
`UPDATE` did, so the unique index still saw the old row as
`PENDING_REVIEW` at insert time. Calling `saveAndFlush` on the
withdrawal forces its `UPDATE` to commit to the database immediately,
independent of whatever runs next — the same "make the state
change synchronous, don't trust default flush timing" reasoning
`saveAndFlush` already gets used for elsewhere in this codebase (see
"`saveAndFlush`, Not `save`, When the Race-Condition Catch Must Be
Synchronous" below), applied to an `UPDATE` this time rather than an
`INSERT`. **The lesson for any future write where a later query's
correctness depends on an earlier write already being visible in the
same session:** don't assume Hibernate flushes pending changes in the
order your code made them — verify with a real test that chains both
operations, the same way this one was actually found.

## A Write That Must Survive an Exception Needs `PROPAGATION_REQUIRES_NEW`, Not `noRollbackFor`

`RefreshSessionService.rotate()` (Milestone 5B) has two branches that revoke a
session (or an entire rotation family) and then throw — a write that must commit
even though the method is signaling failure. The first implementation used
`@Transactional(noRollbackFor = {RefreshTokenReusedException.class, AccountUnavailableException.class})`
on `rotate()` itself, the standard Spring mechanism for exactly this case. **It
did not work** — verified only by running the real application against the real
database and inspecting `refresh_sessions` directly with `psql` between requests
(this class's own mocked unit tests cannot exercise genuine Spring transaction
demarcation and could not have caught it): the revocation executed but was still
rolled back. The fix uses explicit, programmatic transaction control instead — a
`TransactionTemplate` configured with `PROPAGATION_REQUIRES_NEW`, invoked from
inside `rotate()`'s own (suspended, unaffected) transaction — so the write commits
the moment its callback returns, independent of anything that happens afterward,
including the exception thrown right next to it. See ADR-008's implementation
note for the full reproduction. **The lesson for any future write-then-throw
case:** don't trust `noRollbackFor` without verifying against a real transaction
manager and a real datastore — a mocked unit test genuinely cannot tell you
whether it works.

## Adding a Security-Adjacent Capability Without Adding Security's Auto-Configuration

Milestone 5A needed password hashing (`PasswordEncoder`/`BCryptPasswordEncoder`)
without needing — or wanting — the behavior that normally comes attached to it.
`spring-boot-starter-security` auto-configures a default `SecurityFilterChain` that
secures every endpoint by default; adding it a full milestone before Milestone
5B/5C actually builds authentication would have auto-secured the still-public
Category/Resource APIs this project's own milestones intentionally left open, and
implemented the wrong thing at the wrong time. `spring-security-crypto` (the same
Spring Security project, but only its hashing/crypto classes) has no such
auto-configuration and no filter chain — see `PasswordEncoderConfig` and
[ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md). The general
pattern: pull in the narrowest module that provides the class you actually need,
not the "starter" that bundles it with unrelated auto-configuration, when the
auto-configuration's side effects would outrun the current milestone's scope.

## Preventing Privilege Escalation Structurally, Not by Convention

`RegistrationRequest` (Milestone 5A) has no `role` or `status` field at all — there
is no field for a caller-supplied privilege value to bind to, so the question "can
a request escalate its own privilege" is answered by the DTO's shape, not by a
runtime check that could later be forgotten or bypassed. `RegistrationService`
always constructs a `User` via the two-argument constructor that hardcodes
`Role.USER`/`AccountStatus.ACTIVE` — there is no constructor overload, setter, or
code path that accepts a caller-supplied role at all. This structural guarantee is
what actually matters; it holds regardless of how the surrounding JSON is parsed.

`RegistrationRequest` is also annotated `@JsonIgnoreProperties(ignoreUnknown =
true)` — self-documenting, but not what's doing the work here. A stricter
`ignoreUnknown = false` (rejecting an unrecognized field with `400` instead of
silently discarding it) was tried and verified empirically to have **no effect** on
this project's Jackson 3.x (`tools.jackson`)/Spring Boot 4.1 stack: Spring Boot's
Jackson auto-configuration globally disables `FAIL_ON_UNKNOWN_PROPERTIES` by
default, and for record-based request bodies specifically, that global default was
not overridden by the per-class annotation the way it reliably would be for a
classic Jackson 2 bean. See ADR-007's 2026-07-24 correction for the full
investigation. The practical lesson: don't assume a per-class Jackson annotation
is controlling behavior just because it's present and looks correct — verify
against a real request, especially on a newer Jackson major version.

Any future privilege-adjacent input (e.g. an admin-only field on some other
endpoint) should default to this same structural approach — no field to bind to,
not a field plus a runtime guard — before reaching for a runtime check as a
second line of defense.

## Request Authentication and Authorization: The `security` Package

Milestone 5C introduces `com.hfxconnect.security` — deliberately not named
after a domain entity (there is no `Security` table), because it holds
cross-cutting infrastructure every other domain's protected routes rely on,
not a business concept of its own:

- `JwtAuthenticationFilter` — a plain `OncePerRequestFilter`, constructed
  directly inside `SecurityConfig` rather than registered as a Spring bean
  (a `@Component`-annotated `Filter` would additionally be auto-registered
  as an ordinary servlet filter by Spring Boot, running it a second time per
  request outside the security chain — a real gotcha avoided here, not a
  stylistic choice).
- `CurrentUserPrincipal` — a minimal record (`userId`, `email`, `role`)
  attached to `SecurityContext`, following the same "never expose more than
  a consumer needs" reasoning as every `{Domain}Response` DTO elsewhere in
  this document — it is not the `User` entity, and never gains a password
  hash or refresh-session reference.
- `ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler` — translate Spring
  Security's 401/403 outcomes into this project's one `ApiError` shape.
  These run *outside* `DispatcherServlet`, so `GlobalExceptionHandler` (see
  "Centralized Error Handling" above) is never in the call path for either —
  they are a second, necessary place the same response shape has to be
  produced by hand, not a gap in the "one exception handler" rule.
- `SecurityConfig` — the one place the entire route matrix (public vs.
  authenticated vs. role-gated) is expressed, as `HttpSecurity
  .authorizeHttpRequests` request matchers rather than `@PreAuthorize` +
  `@EnableMethodSecurity`. This milestone's whole policy is two rules
  (`ADMIN` for category creation; `ADMIN` or `MODERATOR`, named explicitly,
  for resource creation) with no per-object/ownership logic yet, so a second
  configuration surface would only duplicate one policy in two places — see
  [ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md)
  for the full reasoning and for when `@PreAuthorize` would become the right
  call instead.

**The single most important decision this package makes:** every
authenticated request re-loads the account row by the token's `sub` and
authorizes using its *current* `role`/`status` — never the JWT's own `role`
claim, which can go stale the moment an administrator changes an account
after the token was already issued. This trades one extra indexed
`UserRepository.findById` per authenticated request for closing that gap,
a trade this project's scale makes easily worth it — see ADR-009's
"Current-Request Identity" section, and
`AuthorizationMatrixApiIntegrationTest`'s
`aRoleChangeAfterTokenIssuanceTakesEffectOnTheNextRequestNotTheStaleClaim`
test, which proves it against the real database rather than asserting it
from documentation alone.

`WebCorsConfig` changed shape in this milestone too: it now exposes a
`CorsConfigurationSource` bean instead of implementing
`WebMvcConfigurer.addCorsMappings`, because `SecurityConfig`'s
`HttpSecurity.cors()` needs exactly that bean to delegate to (Spring
Security does not read `WebMvcConfigurer` registrations) — one CORS policy
definition, referenced from the one place that now actually enforces it for
every request, not two definitions that could silently drift apart.

## Consolidating Independent Optional Filters Into One Query

Before Milestone 6A, the public resource listing had two service/repository
methods: one for "all active resources" and one for "active resources in
this category." Adding keyword search as a *second* independent optional
filter would have meant either four method combinations (list /
listByCategory / search / searchByCategory) or an ever-growing combinatorial
surface as more optional filters arrive later (cost type, verification
status, ...). `ResourceService.search`/`ResourceRepository.search` replace
both prior methods with one: `categoryId` and the keyword pattern are each
expressed as a `(:param IS NULL OR ...)` predicate in a single parameterized
JPQL query, rather than as separate methods per combination.

This is the same "generalize on a genuine second need, not preemptively"
reasoning `SlugGenerator`'s and `EmailNormalizer`'s extraction history
already established in this document (see "Shared Pure Utilities Are
Extracted on Second Use, Not Preemptively" above) — applied here to a query
shape instead of a utility function. The old, narrower methods
(`findByActiveWithCategory`/`findByCategoryIdAndActiveWithCategory`,
`listActive`/`listActiveByCategory`) were removed outright, not deprecated
in place: they had no callers outside the code being replaced, and keeping
orphaned methods around after their only caller changes is exactly the kind
of speculative surface this project's conventions elsewhere reject. See
[ADR-010](../decisions/ADR-010-keyword-search-design.md) for the full
design, including the wildcard-escaping mechanism and the deliberate
decision not to add a `pg_trgm`/GIN index yet.

Milestone 6B extended this same query with two more `costType`/
`verificationStatus` predicates and a correlated `EXISTS` subquery for
`openNow` — bind parameters only (the caller's Halifax "now," computed once
per request), never a second query layer. See
[ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md) for why
this stayed one query rather than migrating to Spring Data
`Specification`s, and for the batch-loading (`findByResourceIdIn`) pattern
that keeps the operating-hours lookup at one additional query per list
request rather than N+1.

Milestone 7A's `ResourceRepository.findNearby` follows the identical
shape, expressed as a **native** SQL query instead of JPQL — a correlated
`ST_DWithin`/`ST_Distance` geography predicate has no JPQL equivalent —
returning a closed interface projection rather than an entity, since
`location` is deliberately never Hibernate-mapped (see
[ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md)). The
`openNow` `EXISTS` subquery is re-expressed with raw SQL column names but
is otherwise the identical logic `search`'s JPQL version already uses —
one algorithm, two syntaxes, not two divergent implementations.

## Native SQL Instead of an Unverified ORM-Mapping Dependency

Not every persisted column needs a Hibernate-mapped entity field.
`resources.location` (a PostGIS `geography` column, Milestone 7A) is
deliberately **not** mapped on `CommunityResource` at all — every read and
write goes through native `@Query`/`@Modifying` methods on
`ResourceRepository` instead. This was a considered choice, not a gap:
`hibernate-spatial` (the version matching this project's actual
`hibernate-core`) maps through `org.geolatte.geom`, not
`org.locationtech.jts.geom` (the more commonly-documented option online),
and adopting it would mean either learning a second geometry library or an
unverified JTS integration with no precedent in this project's dependency
history — exactly the "copy an older example without checking
compatibility" risk this project's conventions elsewhere warn against
(see ADR-012 for the full reasoning). `spring.jpa.hibernate.ddl-auto
=validate` (this project's schema-authority rule) only validates columns
an entity actually maps — it does not require every table column to have
one, so an unmapped column is fully compatible with that rule, not a
workaround for it.

## Deterministic Time via Injected `Clock`

Anywhere business logic needs "the current date/time" for a calculation
(not just an entity timestamp — see `CommunityResource`'s/`User`'s
`@PrePersist`/`@PreUpdate` use of `Instant.now()` directly, which is fine
for a stamp nobody calculates against), a `java.time.Clock` is injected
rather than calling `Instant.now()`/`LocalTime.now()` inline.
`OpenNowCalculator` (Milestone 6B — see
[ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md)) is the
first and, as of this milestone, only consumer: production gets a single
`Clock.systemUTC()` bean (`com.hfxconnect.common.config.ClockConfig`),
converted to `America/Halifax` inside the calculator itself; tests supply
`Clock.fixed(...)` for fully deterministic same-day/overnight/DST-boundary
assertions. The same "production randomness/time, deterministic tests"
split this project already applies to `SecureRandom` for refresh tokens.

## OpenAPI

Endpoints are documented with standard `springdoc-openapi` annotations
(`@Operation`, `@ApiResponses`, `@Schema`) directly on the controller and DTOs — no
separate documentation framework or hand-maintained spec file. The generated document
is always available at `/v3/api-docs` (JSON) and `/swagger-ui.html` (interactive) from
a running backend, and is verified by an integration test
(`CategoryApiIntegrationTest.openApiDocumentIncludesTheCategoryEndpoints`) rather than
only inspected manually.

## Row-Level Locking for Multi-Step Decisions That Must Not Race

Milestone 9A's moderation review is the first place in this codebase
where a single request must atomically decide "is this the winning
transaction" *and* perform a multi-step mutation (revalidate, publish
or apply, mark reviewed, record audit) as one unit no concurrent
request can partially interleave with. `ResourceSubmissionRepository
.findByIdForReview`/`CorrectionReportRepository.findByIdForReview`
acquire `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR
UPDATE`) on the contribution row for the whole transaction, rather than
using JPA optimistic locking (`@Version`) with a retry/exception path.
A second concurrent caller's own `SELECT ... FOR UPDATE` blocks until
the first transaction commits or rolls back, then observes the
now-current status and fails fast — the entire review transaction is
serialized, not just its final `UPDATE` statement, and there is no
separate "catch the optimistic-lock exception and translate it" code
path to get wrong. See
[ADR-016](../decisions/ADR-016-moderation-workflow-design.md)'s
"Concurrency Control" section for the full rationale, including why
correction application locks a *second*, independent row (the target
resource, via `ResourceRepository.findByIdForUpdate`) on top of the
report's own lock — two different pending reports can target the same
resource, which a lock on the report row alone would not protect
against.

## A Schema/Entity Change Can Compile and Pass Entity-Layer Tests While Never Reaching a Sibling Response DTO

Milestone 9A added `CommunityResource.lastVerifiedAt` and threaded it
correctly through the entity, `ResourceService`, and the
*moderation-facing* audit snapshots — all of which compiled cleanly and
passed every automated test written against them. It was never added
to the *public* `ResourceResponse` DTO (`GET /api/v1/resources/{id}`
and `.../slug/{slug}`), because no automated test asserted that DTO's
actual field set end-to-end; the gap was only caught by a manual
end-to-end pass reading a live HTTP response body. The lesson, not
specific to this one field: when a new column is added to an entity
that already backs more than one response DTO ("full resource detail"
in this codebase is both `ResourceResponse` and the moderation-facing
detail responses), every DTO claiming to expose that data needs to be
checked explicitly — entity/service-layer test coverage does not imply
response-DTO coverage. See ADR-016's "Verification Policy" section for
the full account and the regression test now guarding against it
recurring.
