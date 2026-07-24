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
> "Preventing Privilege Escalation Structurally, Not by Convention" below.

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
  `NotFoundException` (404), `ConflictException` (409), and `BadRequestException`
  (400) are the concrete bases domain exceptions extend.
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
runtime check that could later be forgotten or bypassed. It is additionally
annotated `@JsonIgnoreProperties(ignoreUnknown = true)`, so a client submitting an
unrecognized field (a stray `role`, `status`, or anything else) is deterministically
ignored by Jackson rather than depending on whatever the project's global
`ObjectMapper` configuration happens to do (which this project has never
customized). `RegistrationService` always constructs a `User` via the two-argument
constructor that hardcodes `Role.USER`/`AccountStatus.ACTIVE` — there is no
constructor overload, setter, or code path that accepts a caller-supplied role at
all. Any future privilege-adjacent input (e.g. an admin-only field on some other
endpoint) should default to this same structural approach — no field to bind to,
not a field plus a runtime guard — before reaching for a runtime check as a
second line of defense.

## OpenAPI

Endpoints are documented with standard `springdoc-openapi` annotations
(`@Operation`, `@ApiResponses`, `@Schema`) directly on the controller and DTOs — no
separate documentation framework or hand-maintained spec file. The generated document
is always available at `/v3/api-docs` (JSON) and `/swagger-ui.html` (interactive) from
a running backend, and is verified by an integration test
(`CategoryApiIntegrationTest.openApiDocumentIncludesTheCategoryEndpoints`) rather than
only inspected manually.
