# Task 035: Saved Resources Service and API

## Objective

Implement the full saved-resources backend surface on top of Task
034's schema and entity: repository queries, business rules, and the
four HTTP endpoints, fully tested.

## Context

The second task of Milestone 8A. Depends on Task 034's `SavedResource`
entity and migration being in place.

## Scope

- `SavedResourceRepository` — `existsByUserIdAndResource_Id`,
  `deleteByUserIdAndResource_Id`, `findSavedResourceIds` (batch
  status), and two `JOIN FETCH` paginated queries (`savedAt`
  descending, `name` ascending) each with an explicit `countQuery`.
- `SavedResourceValidation` — page/size/sort validation, batch-status
  request validation (max 100 ids, null-entry rejection via
  `anyMatch(id -> id == null)`, not `.contains(null)`, which throws on
  an immutable list).
- `SavedResourceService` — `save` (active-resource check, idempotent,
  catches a concurrent-insert race's `DataIntegrityViolationException`
  and treats it as success), `remove` (unconditional on resource
  state), `list` (active resources only, paginated, sorted), `status`
  (batch lookup).
- `SavedResourceController` — `PUT`/`DELETE
  /api/v1/users/me/saved-resources/{resourceId}`, `GET
  /api/v1/users/me/saved-resources`, `POST .../status`. Identity always
  from `@AuthenticationPrincipal CurrentUserPrincipal`.
- DTOs: `SavedResourceSummaryResponse`, `SavedResourcePageResponse`,
  `SavedResourceStatusRequest`, `SavedResourceStatusResponse`.
- Full test suite: `SavedResourceRepositoryIntegrationTest` (17),
  `SavedResourceServiceIntegrationTest` (25),
  `SavedResourceApiIntegrationTest` (29) — including a real concurrent-
  save-request race test and the full `USER`/`ORGANIZATION`/
  `MODERATOR`/`ADMIN` role matrix.

## Out of Scope

Frontend integration — see Tasks 036-037.

## API Contract

See `docs/api/README.md`'s "Saved Resources" section and
`docs/milestones/milestone-08a-saved-resources.md`'s "Saved-Resource
API Contract" for the full request/response shapes and status codes.

## Acceptance Criteria

- [x] All four endpoints implemented exactly as specified; no endpoint
      accepts a client-supplied user id.
- [x] Idempotent `PUT`/`DELETE` confirmed by tests calling each twice.
- [x] A genuine concurrent-request race resolves to exactly one row,
      not a `500`/`409` (dedicated test).
- [x] Active/inactive/missing-resource behavior matches the design:
      save requires active, remove is unconditional, list excludes
      inactive but preserves the relation.
- [x] 71 new backend tests pass; 482 inherited tests remain unaffected
      (553 total at this task's completion, before Task 037's CORS fix
      added one more).

## Evidence

Commits on branch `milestone/08a-saved-resources`; see
`docs/milestones/milestone-08a-saved-resources.md` for full test
results.
