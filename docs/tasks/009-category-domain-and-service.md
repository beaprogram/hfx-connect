# Task 009: Category Domain and Service Layer

## Objective

Implement the `Category` entity, repository, DTOs, deterministic slug generation, the
shared centralized error-handling pattern, and the `CategoryService` business logic —
everything below the HTTP layer.

## Context

Part of Milestone 3A (Category Domain and API), depending on Task 008's schema. This
is also where the project's first `com.hfxconnect.common.error` package was built —
every later domain reuses it rather than inventing its own exception handling.

## Scope

- `Category` entity (no setters — see `docs/architecture/backend-architecture.md`).
- `CategoryRepository`.
- `CategorySlugGenerator` (package-private, pure function).
- `CategoryCreateRequest`, `CategoryResponse`, `CategoryPageResponse`.
- `CategoryNotFoundException`, `CategoryConflictException`.
- `com.hfxconnect.common.error`: `ApiException`, `NotFoundException`,
  `ConflictException`, `BadRequestException`, `InvalidPaginationException`,
  `ValidationException`, `ApiError`, `GlobalExceptionHandler`.
- `CategoryService`: normalization, slug generation, application-level duplicate
  pre-checks, database-constraint-violation translation, transactions, pagination
  validation, entity-to-DTO mapping.
- `spring-boot-starter-data-jpa` and `spring-boot-starter-validation` added to
  `pom.xml`; `spring.jpa.hibernate.ddl-auto=validate` and
  `spring.jpa.open-in-view=false` configured.

## Out of Scope

The REST controller and OpenAPI documentation (Task 010).

## Acceptance Criteria

- Entity has no public setters; built via constructor, updated only through
  `@PrePersist`/`@PreUpdate` timestamp management.
- Repository has exactly the methods the service uses — no speculative additions.
- Service normalizes whitespace, generates a deterministic slug, and rejects names
  that normalize to an empty slug with a clear `VALIDATION_ERROR`.
- Duplicate name/slug is rejected with `CategoryConflictException` at the application
  level (fast path) *and* the service correctly translates a genuine
  `DataIntegrityViolationException` (the database-level, race-condition path) into
  the same exception type.
- Pagination parameters are validated (negative page, size outside 1-100) before any
  query runs.
- `GlobalExceptionHandler` produces the one documented `ApiError` shape for every
  exception type it's registered for, with `fieldErrors` present only for
  validation-style failures.

## Technical Approach

Discovered and fixed a genuine JPA/Flyway startup-ordering bug while integrating this
layer: Spring Boot 4.1 has no built-in Flyway auto-configuration (Milestone 2B,
ADR-004), and without it, nothing forces the `flyway` bean to run before JPA's
`entityManagerFactory` bean — so Hibernate's schema validation could see a
not-yet-migrated database and fail startup with a misleading "missing table" error.
Fixed with `EntityManagerFactoryDependsOnPostProcessor` (relocated in Spring Boot 4.1
to `org.springframework.boot.jpa.autoconfigure`, found by inspecting the actual jar
contents rather than assumed), registered in `FlywayMigrationConfig` alongside the
`flyway` bean it now guarantees runs first.

The race-condition-handling branch of duplicate detection (a genuine
`DataIntegrityViolationException` reaching the service, as opposed to the
application-level pre-check catching it first) is deliberately tested with a mocked
repository rather than attempting real concurrency in an integration test — real
concurrent-request races are slow and flaky to engineer reliably in a test, whereas
the translation *logic* itself (catch `DataIntegrityViolationException`, inspect the
root cause, return the right conflict type) is exactly what unit testing with a mock
is for.

## Testing Requirements

`CategorySlugGeneratorTest` (9 tests, pure function, no Spring context) and
`CategoryServiceTest` (14 tests, Mockito-mocked repository) — both run in
milliseconds with no database dependency. Real database behavior for the entity and
repository is covered separately by Task 010's `CategoryRepositoryIntegrationTest`.

## Result

Completed. 23 fast unit tests pass with no database dependency; the
startup-ordering bug was caught and fixed before it could reach the API layer.

## Related Commit

`feat: add category persistence and service layer`
