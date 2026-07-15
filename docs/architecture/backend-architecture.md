# Backend Architecture

> Status: written once a real domain slice (Category, Milestone 3A) established the
> pattern in practice. Describes what the codebase actually does, and is the
> reference every later domain (resources, organizations, events, ...) should follow
> for consistency, not a new design each time.

## Layering

Each domain (`category`, and later `resource`, `event`, etc.) is one package under
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

`Category` has no setters. It's built once via its constructor and only read
afterward, because Milestone 3A deliberately has no `PATCH`/`PUT` endpoint (not
justified by anything in scope yet). When a real update requirement exists for some
entity, that entity gains the specific setters or update methods it actually needs at
that point — not generic ones added speculatively ahead of time.

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
(`CategoryService.MAX_PAGE_SIZE`), and return an explicit page DTO rather than
serializing Spring's `Page` type directly. This was a deliberate choice over
`Pageable` auto-binding specifically so invalid input (negative page, oversized page
size) produces the project's own consistent `400 INVALID_PAGINATION` error instead of
whatever Spring's default resolver does with out-of-range values.

## OpenAPI

Endpoints are documented with standard `springdoc-openapi` annotations
(`@Operation`, `@ApiResponses`, `@Schema`) directly on the controller and DTOs — no
separate documentation framework or hand-maintained spec file. The generated document
is always available at `/v3/api-docs` (JSON) and `/swagger-ui.html` (interactive) from
a running backend, and is verified by an integration test
(`CategoryApiIntegrationTest.openApiDocumentIncludesTheCategoryEndpoints`) rather than
only inspected manually.
