# Task 046: Organization Schema, Profile Management, and Admin Verification

## Objective

Establish the persistence foundation for Milestone 10A and the first
half of the workflow it enables: the `V11` migration (`organizations`,
`resources.organization_id`, `resource_ownership_claims`,
`organization_audit_events`), the organization-profile self-service API
(create/get/update, plus the public verified-only lookup), and the
`ADMIN`-only verification API (queue, detail, verify/reject/suspend).

## Context

First task of Milestone 10A. Verified V10 was the latest migration
before writing V11, and that ADR-017/task-046 were the next available
numbers. Promoted `SelfReviewNotAllowedException`/
`ContributionAlreadyReviewedException` from `com.hfxconnect.moderation`
to `com.hfxconnect.common.error` (identical name/code/message — only
the package changed) rather than writing new, differently-named
organization-domain exceptions with the same meaning, since the
organization-verification flow needs the identical rule Milestone 9A
already solved. Made `ModerationValidation`/`validateReason` `public`
for the same reason `ResourceValidation`'s field-level helpers were
made `public` in Milestone 8B — a second real cross-package use.

## Scope

- `V11__create_organizations_and_resource_claims.sql` — `organizations`
  (owner-unique, slug-unique, verification-metadata `CHECK`-constrained),
  `resources.organization_id` (`SET NULL`), `resource_ownership_claims`
  (`RESTRICT`, partial-unique pending-per-pair index), the append-only
  `organization_audit_events` table.
- `com.hfxconnect.organization` package scaffolding:
  `OrganizationVerificationStatus`/`ResourceOwnershipClaimStatus`/
  `OrganizationAuditEventType` enums, `Organization`/
  `ResourceOwnershipClaim`/`OrganizationAuditEvent` entities (+
  repositories, `OrganizationAuditEvent` using
  `@JdbcTypeCode(SqlTypes.JSON)` for its snapshot columns, the same
  pattern `ModerationAuditEvent` established), `OrganizationValidation`,
  `OrganizationSnapshots`, `OrganizationAuditRecorder`,
  `OrganizationAuditQueryService`.
- `OrganizationService` (create/get/update/getPublicBySlug, including
  the verification-reset-on-identity-change policy) and
  `OrganizationController` (`POST`/`GET`/`PATCH /api/v1/organizations/me`,
  public `GET /api/v1/organizations/{slug}`).
- `AdminOrganizationService` (queue/detail/verify/reject/suspend, row-
  locked, self-review-checked) and `AdminOrganizationController`.
- `OrganizationNotFoundException`, `OrganizationConflictException`,
  `AdminOrganizationQueueItemResponse`/`PageResponse`,
  `AdminOrganizationDetailResponse`, `OrganizationDecisionRequest`,
  `OrganizationAuditEventResponse`/`PageResponse`.
- `SecurityConfig` — `/api/v1/organizations/me`(`/**`) restricted to
  `ORGANIZATION`, declared before the public single-segment slug
  matcher for correct first-match-wins precedence;
  `/api/v1/admin/organizations/**` restricted to `ADMIN`.

## Out of Scope

Resource-ownership claims, the owned-resource list, and public resource
attribution — see Task 047. Frontend — see Task 048.

## Design Decisions

See [ADR-017](../decisions/ADR-017-organization-identity-and-ownership.md)
for the full rationale: role-vs-verification separation, the
verification-reset policy, why verification/suspension is `ADMIN`-only
(not `MODERATOR`), self-review promotion and reuse, and the deletion
policy for every new foreign key.

## Acceptance Criteria

- [x] `V11` applies cleanly against a fresh database; `V1`-`V10`
      unchanged.
- [x] One profile per account (unique index) and unique slug both
      enforced at the database level, not just in the service layer.
- [x] A profile always starts `PENDING_VERIFICATION`; no code path can
      start it `VERIFIED`.
- [x] Identity-significant edits reset an already-`VERIFIED` profile;
      contact-only edits never do — both behaviors covered by tests.
- [x] Verification/rejection/suspension are `ADMIN`-only, self-review
      blocked with no exemption, concurrency-safe under a real
      two-thread test.
- [x] Public profile lookup returns only a currently-`VERIFIED`
      organization; anything else is `404`.

## Evidence

Commits on branch `milestone/10a-organization-management`; see
`docs/database/README.md`'s `organizations`/`organization_audit_events`
sections and `docs/milestones/milestone-10a-organization-management.md`.
