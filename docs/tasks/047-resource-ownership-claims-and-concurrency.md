# Task 047: Resource Ownership Claims, Approval Concurrency, and Owned-Resource Attribution

## Objective

Let a verified organization request ownership of an existing, active,
unowned public resource; let an `ADMIN` approve or reject that request
with an atomically-safe, concurrency-proof transaction; let a verified
organization see the resources it now owns; and let the public
resource API expose safe organization attribution wherever it already
shows resource detail.

## Context

Second task of Milestone 10A, built directly on Task 046's schema and
organization-profile foundation. The concurrency requirement here is
strictly harder than Milestone 9A's: two different organizations
submitting two different claims for the same resource are two
different `resource_ownership_claims` rows, so locking only the claim
row (Task 046's `findByIdForReview` pattern) would never serialize two
admins approving different claims against the same resource. Reused
`ResourceRepository.findByIdForUpdate` — already established by the
Milestone 9A correction-application flow for the identical "two
different decision rows, one shared resource" shape — rather than
inventing a new locking mechanism.

## Scope

- `ResourceOwnershipClaimService` (organization-side create/list/
  withdraw: verified-org check, active+unowned resource check,
  duplicate-pending check backed by the migration's partial unique
  index) and `ResourceOwnershipClaimController`.
- `AdminResourceOwnershipClaimService` (admin-side queue/detail/
  approve/reject — `approve` is the milestone's most concurrency-
  sensitive operation, locking both the claim row and the target
  resource row) and `AdminResourceOwnershipClaimController`.
- `OrganizationResourceService`/`OrganizationResourceController`
  (`GET /api/v1/organizations/me/resources`), using
  `ResourceRepository.findByOrganizationIdWithCategory`.
- New exceptions: `OrganizationNotVerifiedException`,
  `ResourceAlreadyOwnedException`,
  `ResourceOwnershipClaimConflictException`,
  `ResourceOwnershipClaimNotFoundException`, `InactiveResourceException`.
- `CommunityResource.organizationId` (a plain `UUID` column, not a
  `@ManyToOne` — see ADR-017 for the circular-package-dependency
  reasoning) + `assignOrganization`; `ResourceOrganizationSummaryResponse`;
  batch organization-summary loading in `ResourceService` for search,
  nearby search, and single-resource detail, mirroring the exact
  "one query per page, never one per card" posture the Milestone 6B
  hours loader established; `ResourceRepository.findByIdInWithCategory`
  for claim-list resource summaries.
- `OrganizationConcurrencyIntegrationTest` — two real, separate-
  transaction concurrency tests: two admins verifying the same
  organization, and two different organizations' claims racing to
  become the owner of the same resource.
- `SecurityConfig` — `/api/v1/admin/resource-ownership-claims/**`
  restricted to `ADMIN`.

## Out of Scope

Frontend — see Task 048.

## Design Decisions

See [ADR-017](../decisions/ADR-017-organization-identity-and-ownership.md)'s
"Concurrency Strategy" and "Ownership Source of Truth" sections for the
full rationale, including why a losing concurrent claim is left
`PENDING_REVIEW` (for an admin to explicitly decide) rather than
silently auto-rejected, and why organization suspension never touches
`resources.organization_id`.

## Acceptance Criteria

- [x] Only a `VERIFIED` organization may create a claim; re-verified
      fresh (not from a stale earlier check) inside the approval
      transaction.
- [x] A resource must be active and currently unowned to be claimed;
      an already-owned resource returns `409 RESOURCE_ALREADY_OWNED`
      at both claim-creation and claim-approval time.
- [x] A duplicate pending claim for the same organization/resource
      pair is rejected, backed by a real database constraint, not just
      an application check.
- [x] Approval assigns `resources.organization_id` atomically in the
      same transaction as marking the claim `APPROVED` and recording
      the audit event.
- [x] Two claims racing for the same resource: proven, with a real
      two-thread test, that at most one can ever be approved.
- [x] A claim's own organization may withdraw only its own,
      still-`PENDING_REVIEW` claim; another organization's claim id
      returns `404`, never leaking its existence.
- [x] Public resource responses (detail, nearby) show organization
      attribution only for a currently-`VERIFIED` owner; the compact
      list-card response never includes it.

## Evidence

Commits on branch `milestone/10a-organization-management`; see
`docs/database/README.md`'s `resource_ownership_claims` section and
`docs/milestones/milestone-10a-organization-management.md`.
