# ADR-017: Organization Identity, Verification, and Resource Ownership Design

## Status

Accepted — 2026-08-07

## Context

Milestone 10A introduces a new kind of account authority that nothing
before it needed: an `ORGANIZATION` account manages a profile that
represents a real-world entity, that entity can be checked by an
`ADMIN` before anyone trusts it, and once trusted it can request — and
be granted — authority over an *existing* public resource it did not
create. Several decisions carry real correctness or security
consequences if gotten wrong:

- Whether having the `ORGANIZATION` role is the same thing as being a
  trusted organization, and where that line is enforced.
- What actually grants ownership of a resource, versus what is merely
  a request for it.
- Whether editing an already-verified profile should be able to
  silently keep its verified status.
- Who may verify an organization or approve an ownership claim, and
  whether the highest-privileged role gets a self-review exemption.
- How two admins verifying the same organization, or two different
  organizations' claims racing for the same resource, stay
  correctness-safe under real concurrent load.
- Whether to reuse Milestone 9A's moderation/audit architecture or
  build a second one.
- What a resource "losing" its owning organization (via suspension or
  future removal) should look like from the public API.

## Decision

### Role vs. Verification: `ORGANIZATION` Is Not `VERIFIED`

Holding the `ORGANIZATION` role only ever grants access to the
profile-management and claim-*submission* endpoints
(`/api/v1/organizations/me/**`) — it never by itself grants the
authority to claim a resource. `ResourceOwnershipClaimService.create`
independently checks the current account's own `Organization.verificationStatus
== VERIFIED` before allowing a claim, and
`AdminResourceOwnershipClaimService.approve` re-checks it again inside
the approval transaction (see "Concurrency Strategy" below) — an
organization that was verified when it submitted a claim but has since
been suspended can never have that claim approved. The two concepts
(role, verification state) are deliberately never conflated anywhere
in this codebase.

### Ownership Source of Truth: `resources.organization_id`, Never a Claim's Status

`resources.organization_id` is the *only* place current ownership is
read from — every public/owner-facing response that shows attribution
reads this column (via `ResourceService`'s batch-loaded organization
summaries), never a `resource_ownership_claims` row's `status`. A
`ResourceOwnershipClaim` is workflow history: it records that an
organization asked, and how an admin decided, but an `APPROVED` claim
is not itself the ownership grant — `AdminResourceOwnershipClaimService.approve`
explicitly calls `CommunityResource.assignOrganization` in the same
transaction that marks the claim `APPROVED`, and nothing else in this
codebase ever sets `organization_id` outside that one call path. This
matters concretely for the suspension case: suspending an organization
never touches `resources.organization_id` at all (see "Organization
Suspension" below) — ownership and verification are independent
facts, and the schema keeps them in genuinely separate columns/tables
rather than deriving one from the other.

### One Organization Profile Per Account (MVP Simplification)

`organizations_owner_user_id_key` (a real unique index, not just an
application check) enforces exactly one profile per owning account —
matching the identical "database constraint is the authority, the
service-layer pre-check only narrows the race window" pattern already
established for resource/category slugs. There is no team/staff/
member-invitation model: `owner_user_id` is a plain `UUID` column, the
same "no back-reference needed" reasoning every other contribution-
domain owner column in this codebase already uses. This is a
deliberate, documented MVP limitation (see the milestone doc's "Known
Limitations") — the schema does not preclude a future multi-member
model, but nothing in this milestone builds toward one speculatively.

### Verification Reset Policy: Identity-Significant Fields Reset to Pending

`OrganizationService.update` compares the submitted `name`
(normalized) and `websiteUrl` against the organization's current
values; if either changed *and* the organization is currently
`VERIFIED`, it calls `Organization.resetVerificationToPending()`,
which clears `verifiedByUserId`/`verifiedAt`/`verificationReason`
entirely (not just the status) — `organizations_verification_metadata_check`
requires all three present or all three absent together, and the
previous decision no longer honestly describes an organization whose
identity has just changed. Contact-detail-only changes (description,
email, phone, address) never reset verification — an admin verified
the organization's *identity*, and a phone number update doesn't
change who the organization is. This is the conservative choice the
blueprint asked for: silently keeping `VERIFIED` after a name/website
change was rejected as a way an already-verified account could rename
into a different identity while keeping its trusted badge.

### Admin-Only Verification and Claim Review — Not Extended to `MODERATOR`

Both `/api/v1/admin/organizations/**` and
`/api/v1/admin/resource-ownership-claims/**` are `ADMIN`-only in
`SecurityConfig`, unlike `/api/v1/moderation/**` (`ADMIN` or
`MODERATOR`). Verifying an organization's real-world identity and
granting it ownership over existing public data is a materially
higher-trust decision than reviewing a single proposed resource
submission — Milestone 9A's `MODERATOR`/`ADMIN` pairing was not
extended here without a specific reason to, and none exists yet.

### Self-Review Prevention — Promoted, Reused, No `ADMIN` Bypass

`SelfReviewNotAllowedException` and `ContributionAlreadyReviewedException`
were promoted from `com.hfxconnect.moderation` to `com.hfxconnect.common.error`
in this milestone specifically because the organization domain needs
the identical rule a second time — this codebase's own "shared pure
utilities extracted on second use, not preemptively" pattern applied
to exceptions rather than static helper methods for the first time.
Class name, HTTP status, error code, and message text are preserved
exactly for `ContributionAlreadyReviewedException` (its name is a
slight stretch for an organization/claim context, but changing the
wire-visible `code` would be a breaking change to Milestone 9A's
already-shipped, already-documented API contract). `AdminOrganizationService`
and `AdminResourceOwnershipClaimService` both check
`admin.userId().equals(<target's owner/organization owner>)`
immediately after acquiring the relevant row lock, before any other
business check — the same "conflict-of-interest rule, not a
permission gate" reasoning ADR-016 already established, with the same
"no `ADMIN` exemption" answer.

### Concurrency Strategy: Reusing Milestone 9A's Row-Lock Pattern — Plus a Second Lock for Claim Approval

Organization verification (`AdminOrganizationService.verify/reject/suspend`)
reuses the exact `PESSIMISTIC_WRITE` row-lock pattern ADR-016
established: `OrganizationRepository.findByIdForReview` locks the
organization row for the whole decision transaction, so two admins
racing to verify the same organization serialize behind that lock and
the second one fails with `409 CONTRIBUTION_ALREADY_REVIEWED`.

Claim approval (`AdminResourceOwnershipClaimService.approve`) needed a
second lock the moderation workflow didn't: two *different*
organizations' claims are two different `resource_ownership_claims`
rows, so locking only the claim row would never serialize two admins
approving different claims against the *same* resource — nothing
would block the second approval from also reading `organization_id
IS NULL` as true. `approve` additionally locks the target
`CommunityResource` row via `ResourceRepository.findByIdForUpdate` —
the exact same lock ADR-016's correction-application flow already
uses for an identical "two different contribution rows, one shared
resource" shape — and re-verifies `organization.verificationStatus ==
VERIFIED`, `resource.active`, and `resource.organizationId == null`
all under that lock before assigning ownership. A claim that loses
this race fails with `409 RESOURCE_ALREADY_OWNED`, and the losing
claim's own row is left untouched (still `PENDING_REVIEW`) — an admin
must still explicitly decide it, rather than the system silently
auto-rejecting it, so the audit trail never invents a decision no
human actually made.

### Organization Suspension: Ownership and Verification Are Independent

`Organization.suspend` is only legal from `VERIFIED`. Suspending an
organization deliberately never touches any resource it owns —
`resources.organization_id` is untouched, resources it owns remain
active and public. The only observable effect elsewhere is that
`OrganizationRepository.findByIdInAndVerificationStatus(ids, VERIFIED)`
(the batch loader every public resource-response path uses) simply
stops returning a suspended organization's row, which is what makes
public attribution disappear from an otherwise-unaffected resource —
and the same query also correctly and automatically handles
`PENDING_VERIFICATION`/`REJECTED` with the one rule, not three
separate checks. The organization's own public profile
(`GET /api/v1/organizations/{slug}`) also becomes unreachable (404)
the moment `verificationStatus` leaves `VERIFIED`, for the identical
reason. Revoking an already-granted ownership is out of scope for this
milestone (see Known Limitations).

### A New, Separate Audit Table — Not a Repurposed `moderation_audit_events`

`organization_audit_events` is a new, separate table from
`moderation_audit_events`, not a reuse of it, even though both are
append-only, JSONB-snapshot, actor-email-snapshot designs. Reusing the
existing table was considered and rejected: `moderation_audit_events`'s
`contribution_type`/`action` `CHECK` constraints are specific to the
Milestone 8B/9A contribution-review domain
(`RESOURCE_SUBMISSION`/`CORRECTION_REPORT`,
`REVIEW_DECISION`/`RESOURCE_CREATED`/etc.), and organization/claim
events (`ORGANIZATION_VERIFIED`, `OWNERSHIP_CLAIM_APPROVED`, ...) don't
fit that vocabulary without corrupting its meaning. `OrganizationAuditEvent`
carries `organizationId` (always), plus optional `resourceId`/`claimId`
depending on which kind of event it is — the smallest coherent shape
that covers both organization-lifecycle and claim-lifecycle events
without inventing two tables for what is operationally one admin-only
audit surface.

### Deletion Policy

- `resources.organization_id` → `organizations(id)` `ON DELETE SET
  NULL`: removing an organization must never destroy the public
  resource it owned — the resource simply becomes unowned again, the
  same state a never-claimed resource is already in.
- `resource_ownership_claims.organization_id`/`resource_id` → `ON
  DELETE RESTRICT`: a claim is workflow/audit history with standalone
  review value, matching this codebase's established "decision history
  must not silently lose its actor/subject" reasoning.
- `organizations.owner_user_id`/`verified_by_user_id` → `ON DELETE
  RESTRICT`: identity/decision history must not silently lose its
  actor, the same reasoning every other actor-id column in this
  codebase already follows.

### Public vs. Private Fields

Public (`PublicOrganizationResponse`, and the `organization` summary
embedded on a resource response): name, slug, description, website,
public contact fields, public address fields, `verificationStatus`,
`verifiedAt`. Private (never returned by any public or owner-facing
route): `ownerUserId`, `verifiedByUserId`, `verificationReason` beyond
the owner's own `/me` view, the full audit trail, and any
pending/rejected/suspended organization's profile at all (404, not a
redacted 200). `AdminOrganizationDetailResponse`/
`AdminOwnershipClaimDetailResponse` are the only shapes that include
raw account ids (`ownerUserId`, `verifiedByUserId`,
`reviewedByUserId`) — never resolved to an email inline, matching
ADR-016's "an admin wanting human identity uses the audit-events
endpoint's point-in-time `actorEmail` snapshot" reasoning exactly.

### Dependency for a Future Event Feature

Nothing in this milestone builds event CRUD, event search, or
organization-event management — see "Out of Scope." The schema (an
`organizations` table with a real primary key, and the established
"an organization owns things via a plain foreign-key column"
pattern `resources.organization_id` demonstrates) is what a future
Milestone 10B event feature would extend, but no event-shaped table,
column, or endpoint exists yet.

## Consequences

- Two independently-locked rows (the decision row itself, plus — for
  claim approval only — the target resource row) is now established
  in a second domain after Milestone 9A's correction-report flow; a
  future workflow with the same "two different decision rows, one
  shared resource" shape should reuse `ResourceRepository.findByIdForUpdate`
  rather than inventing a third locking mechanism.
- `organization_audit_events` is a third table using JSONB columns via
  Hibernate's `@JdbcTypeCode(SqlTypes.JSON)`, after
  `resource_submissions`/`correction_reports` (V9) and
  `moderation_audit_events` (V10).
- `SelfReviewNotAllowedException`/`ContributionAlreadyReviewedException`
  now live in `com.hfxconnect.common.error` — any future domain with
  an identical "row-locked pending item, one decision, no self-review"
  shape should reuse them rather than writing new exception classes
  with the same meaning.
- `ModerationValidation.validateReason` is now `public` and reused
  by the organization domain — a second precedent (after
  `ResourceValidation`, Milestone 8B) for promoting a validation
  helper to cross-package `public` on its second real use rather than
  duplicating it.
- Public resource responses now carry an optional `organization`
  summary, loaded via the same "one batch query per page, never one
  per card" posture `ResourceService`'s hours loader already
  established (Milestone 6B) — a third batch-loading call site
  following that exact shape.
- No multi-member/staff organization model, ownership-transfer
  workflow, ownership-revocation UI, uploaded verification documents,
  automated business-registry lookup, org-managed resource editing, or
  event feature exists yet — see the milestone doc's Known
  Limitations for the complete list.
