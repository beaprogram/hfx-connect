# Milestone 10A: Organization Profiles, Verification, and Resource Ownership

## Objective

Let an `ORGANIZATION`-role account create and maintain one organization
profile (starting `PENDING_VERIFICATION`, server-controlled); let an
`ADMIN` review, verify, reject, or suspend it; let a verified
organization request ownership of an existing public resource it did
not create; let an `ADMIN` approve or reject that ownership claim; let
an approved claim establish a real, database-authoritative
organization → resource ownership relationship; let a verified
organization view the resources it owns through a protected dashboard;
let ordinary visitors see safe organization attribution on an owned
public resource — all while `ORGANIZATION` role and `VERIFIED`
organization status stay two independently-enforced facts, self-review
is blocked with no `ADMIN` exemption, and two concurrent decisions can
never establish dual ownership of the same resource.

## Product Value

The first milestone where a real-world organization — not just an
individual contributor — can hold authority over data on HFX Connect.
A newcomer-services non-profit can now claim its own existing listing
once verified, rather than every listing staying either
HFX-Connect-managed or anonymously contributed. This is deliberately
scoped to *claiming existing* resources, not creating new
organization-owned listings from scratch — the smallest version of
"organizations are real accounts with real authority" that is honestly
useful on its own.

## Technical Scope

**Backend** — one new package, plus a narrow, read-only extension to
the resource package:

- `com.hfxconnect.organization` — `Organization`/`ResourceOwnershipClaim`/
  `OrganizationAuditEvent` entities (+ repositories),
  `OrganizationVerificationStatus`/`ResourceOwnershipClaimStatus`/
  `OrganizationAuditEventType` enums, `OrganizationValidation`,
  `OrganizationSnapshots`, `OrganizationAuditRecorder`,
  `OrganizationAuditQueryService`, `OrganizationService`,
  `AdminOrganizationService`, `ResourceOwnershipClaimService`,
  `AdminResourceOwnershipClaimService`, `OrganizationResourceService`,
  `OrganizationController`, `AdminOrganizationController`,
  `ResourceOwnershipClaimController`,
  `AdminResourceOwnershipClaimController`,
  `OrganizationResourceController`, request/response DTOs, and five
  new exception types (`OrganizationNotFoundException`,
  `OrganizationConflictException`, `OrganizationNotVerifiedException`,
  `ResourceAlreadyOwnedException`,
  `ResourceOwnershipClaimConflictException`,
  `ResourceOwnershipClaimNotFoundException`, `InactiveResourceException`).
- `V11__create_organizations_and_resource_claims.sql` — `organizations`,
  `resources.organization_id`, `resource_ownership_claims`,
  `organization_audit_events`.
- `com.hfxconnect.common.error` — `SelfReviewNotAllowedException`/
  `ContributionAlreadyReviewedException` promoted from `moderation`
  (identical name/code/message, only the package changed).
- `com.hfxconnect.moderation.ModerationValidation` — made `public`;
  `validateReason` reused by the organization domain (a second
  cross-package promotion, after `ResourceValidation`, on its second
  real use).
- `com.hfxconnect.resource` — `CommunityResource.organizationId`
  (plain column, not a relationship — see ADR-017) +
  `assignOrganization`, `ResourceOrganizationSummaryResponse`,
  organization-summary batch-loading in `ResourceService` (search,
  nearby, and single-resource detail paths), `ResourceRepository`
  additions (`findByOrganizationIdWithCategory`,
  `findByIdInWithCategory`, `organization_id` in the native nearby
  query).
- `SecurityConfig` — `/api/v1/organizations/me` + `/**` restricted to
  `ORGANIZATION`; `GET /api/v1/organizations/{slug}` public;
  `/api/v1/admin/organizations/**` and
  `/api/v1/admin/resource-ownership-claims/**` restricted to `ADMIN`
  only (not `MODERATOR`).

**Frontend:**

- `lib/api/organization.ts`, `lib/query/use-organization.ts`,
  `lib/query/keys.ts`'s `organizationKeys`/`publicOrganizationKeys`/
  `adminOrganizationKeys`/`adminOwnershipClaimKeys`.
- `lib/api/client.ts` — new `patchJson` (the organization profile
  update contract; the first `PATCH` this codebase has used).
- `components/organization/` — `organization-route.tsx` (ORGANIZATION
  role guard), `admin-route.tsx` (ADMIN-only guard, the first `/admin`
  frontend section), `organization-dashboard.tsx`,
  `organization-profile-form.tsx`, `organization-profile-page.tsx`,
  `organization-resources-list.tsx`, `organization-claims-list.tsx`,
  `organization-nav.tsx`, `organization-status-badge.tsx`,
  `organization-audit-history.tsx`, `claim-resource-control.tsx`,
  `admin-organization-queue.tsx`, `admin-organization-detail.tsx`,
  `admin-ownership-claim-queue.tsx`, `admin-ownership-claim-detail.tsx`.
- Routes: `/organization`, `/organization/profile`,
  `/organization/resources`, `/organization/claims`,
  `/admin/organizations`, `/admin/organizations/[id]`,
  `/admin/resource-claims`, `/admin/resource-claims/[id]`.
- `AuthNav`/`MobileNav` — an "Organization" link for `ORGANIZATION`
  accounts, an "Administration" link for `ADMIN` accounts.
- `AuthProvider`'s private-cache clearing extended to `"organization"`,
  `"admin-organizations"`, and `"admin-ownership-claims"` key prefixes
  — `"public-organization"` deliberately excluded (public data).
- `components/resources/resource-detail.tsx` — safe organization
  attribution when a resource is owned by a verified organization, and
  the `ClaimResourceControl` when it is unowned.
- `lib/validation/schemas.ts` — organization/claim schemas, plus an
  optional `organization` summary threaded onto `resourceResponseSchema`
  and `nearbyResourceSummaryResponseSchema` (never onto the compact
  list-card schema — matching the backend's own response shapes).

## Out of Scope

Events, event CRUD, event search, organization analytics, multi-member/
staff organization accounts, invitations, multiple owners per
organization, resource-ownership transfer, ownership revocation UI,
automated business-registry verification, uploaded verification
documents, direct org-managed resource editing, email notifications,
role-management UI, Milestone 10B, Milestone 11.

## Organization Principles

- Holding the `ORGANIZATION` role and having a `VERIFIED` organization
  profile are two independently-checked facts — never conflated
  anywhere in this codebase (see ADR-017).
- A new profile always starts `PENDING_VERIFICATION`; only an `ADMIN`
  decision can move it to `VERIFIED`/`REJECTED`, and only from
  `VERIFIED` to `SUSPENDED`.
- Editing an already-`VERIFIED` profile's name or website resets it to
  `PENDING_VERIFICATION`; contact-detail-only edits never do.
- `resources.organization_id` is the sole ownership authority — a
  claim is workflow history, never itself the grant.
- Only a `VERIFIED` organization may submit a claim; only an `ADMIN`
  may approve or reject one; approval re-verifies eligibility fresh,
  under lock, at decision time — not from a stale earlier check.
- An `ADMIN` can never verify, reject, suspend, or decide a claim for
  their own organization — no exception for the highest-privileged
  role.
- Two admins deciding the same organization, or two organizations'
  claims racing for the same resource, can never both "win."
- Suspending an organization never touches a resource it already owns
  — the resource stays active and public; only public attribution and
  the organization's own public profile disappear.

Full identity/ownership/concurrency/audit design:
[ADR-017](../decisions/ADR-017-organization-identity-and-ownership.md).

## Database Migration

`V11__create_organizations_and_resource_claims.sql` — `V1`-`V10`
unchanged:

- `organizations`: `owner_user_id` (`RESTRICT`, unique — one profile
  per account), `name`/`normalized_name`/`slug` (unique), public
  contact/address fields (all nullable), `verification_status`
  (`CHECK`-constrained), `verified_by_user_id` (`RESTRICT`,
  nullable)/`verified_at`/`verification_reason` — a `CHECK` constraint
  requires all three present together on `VERIFIED`/`REJECTED`/
  `SUSPENDED` and absent together on `PENDING_VERIFICATION`.
- `resources.organization_id` — nullable `UUID` FK →
  `organizations(id)` `ON DELETE SET NULL` (removing an organization
  must never destroy the resource it owned).
- `resource_ownership_claims`: `organization_id`/`resource_id`
  (`RESTRICT` — claim history has standalone review value), `status`
  (`CHECK`-constrained), a partial unique index enforcing at most one
  `PENDING_REVIEW` claim per organization/resource pair, review
  metadata under the same present/absent-together `CHECK` shape.
- `organization_audit_events`: append-only, `event_type`
  `CHECK`-constrained (8 values), `organization_id` (required) +
  optional `resource_id`/`claim_id`, `actor_user_id` (`RESTRICT`) + a
  snapshot `actor_email`, nullable JSONB `before_snapshot`/
  `after_snapshot`, indexed on `organization_id`.

## Concurrency Control

Organization verification reuses Milestone 9A's row-level
`PESSIMISTIC_WRITE` pattern exactly (`OrganizationRepository.findByIdForReview`).
Claim approval additionally locks the *target resource* row
(`ResourceRepository.findByIdForUpdate`, the same lock the Milestone 9A
correction-application flow already established) — necessary because
two different organizations' claims are two different claim rows, so
locking only the claim would never serialize two admins approving
different claims against the same resource. See ADR-017's "Concurrency
Strategy" for the full account, and its "Consequences" for why this
now generalizes as this codebase's pattern for "two decision rows, one
shared resource."

## Organization Profile API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/organizations/me` | `ORGANIZATION` only. Creates the current account's profile, always `PENDING_VERIFICATION`. |
| `GET` | `/api/v1/organizations/me` | `ORGANIZATION` only. The current account's own profile. |
| `PATCH` | `/api/v1/organizations/me` | `ORGANIZATION` only. Updates owner-editable fields; resets verification on an identity-significant change. |
| `GET` | `/api/v1/organizations/{slug}` | Public. Only a `VERIFIED` organization; `404` otherwise. |

## Admin Organization Verification API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/organizations` | `ADMIN` only. Queue, defaults to `PENDING_VERIFICATION` oldest-first. `verificationStatus`, `sort=createdAt\|name`. |
| `GET` | `/api/v1/admin/organizations/{id}` | `ADMIN` only. Full detail, not owner-scoped. |
| `POST` | `/api/v1/admin/organizations/{id}/verify` | `{reason}` — only legal from `PENDING_VERIFICATION`. |
| `POST` | `/api/v1/admin/organizations/{id}/reject` | `{reason}` — profile kept, reason visible to owner. |
| `POST` | `/api/v1/admin/organizations/{id}/suspend` | `{reason}` — only legal from `VERIFIED`. |
| `GET` | `/api/v1/admin/organizations/{id}/audit-events` | This organization's audit history. |

## Resource Ownership Claim API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/organizations/me/resource-claims/{resourceId}` | `ORGANIZATION` only, `VERIFIED` org, active+unowned resource. |
| `GET` | `/api/v1/organizations/me/resource-claims` | The current organization's own claims. `status` filter. |
| `POST` | `/api/v1/organizations/me/resource-claims/{claimId}/withdraw` | Only the owning organization's own, `PENDING_REVIEW` claim. |
| `GET` | `/api/v1/organizations/me/resources` | `ORGANIZATION` only. Resources currently owned by the current account's organization. |
| `GET` | `/api/v1/admin/resource-ownership-claims` | `ADMIN` only. Queue, defaults to `PENDING_REVIEW` oldest-first. `status`, `organizationId`. |
| `GET` | `/api/v1/admin/resource-ownership-claims/{id}` | `ADMIN` only. Full detail, including current live ownership. |
| `POST` | `/api/v1/admin/resource-ownership-claims/{id}/approve` | `{reason}` — assigns `resources.organization_id` atomically. |
| `POST` | `/api/v1/admin/resource-ownership-claims/{id}/reject` | `{reason}` — resource never modified. |

All admin routes: `ADMIN` only, never `MODERATOR` (see ADR-017's
"Admin-Only Verification" section).

## Claim Approval Contract

Locks the claim row → self-review check → confirms `PENDING_REVIEW` →
re-verifies the organization is still `VERIFIED` → locks the target
resource row → re-verifies it is still active and unowned → assigns
`resources.organization_id` → marks the claim `APPROVED` → records an
`OWNERSHIP_CLAIM_APPROVED` audit event → commits atomically. A
resource that became owned in the meantime returns `409
RESOURCE_ALREADY_OWNED`, and the losing claim is left `PENDING_REVIEW`
for an admin to explicitly decide — never silently auto-rejected.

## Audit Schema

`organization_audit_events` — a new, separate table from
`moderation_audit_events` (see ADR-017's "A New, Separate Audit Table"
for why reusing Milestone 9A's table was rejected). Eight event types
covering both the organization lifecycle
(`ORGANIZATION_SUBMITTED`/`_VERIFIED`/`_REJECTED`/`_SUSPENDED`) and the
claim lifecycle (`OWNERSHIP_CLAIM_SUBMITTED`/`_APPROVED`/`_REJECTED`/
`_WITHDRAWN`). Append-only; `ADMIN`-only, never reachable from an
owner-facing or public route.

## Public Resource Organization Attribution

`ResourceResponse` and `NearbyResourceSummaryResponse` (never the
compact list-card `ResourceSummaryResponse`) gain an optional
`organization: {id, name, slug, verified: true}` — present only when
the resource is owned *and* the owning organization is currently
`VERIFIED`. A suspended (or rejected/pending, though those states
cannot co-exist with ownership under normal operation) organization's
resource stays public and owned; attribution is simply omitted, via
one query-level filter (`OrganizationRepository.findByIdInAndVerificationStatus`)
rather than three separate status checks.

## Cache Invalidation

Verification/suspension decisions invalidate: `["admin-organizations"]`
(queue/detail/audit), `["organization"]` (the owner's own profile
query), the public organization-profile query for that slug, and
`["resources"]` (since suspension changes public attribution). Claim
approval/rejection invalidates: `["admin-ownership-claims"]`
(queue/detail), `["organization"]` (the claiming organization's own
claims and resources), and `["resources"]` (public detail/list/
nearby, since approval changes attribution there). Logout/account
switch clears `"organization"`, `"admin-organizations"`, and
`"admin-ownership-claims"` — never `"public-organization"`, which is
public data, the same posture the public `"resources"` cache already
has. Access tokens never appear in any query key.

## Accessibility and Responsive Behavior

Labelled profile-form fields with inline field-level errors and an
error summary; decision forms reuse `ModerationDecisionForm`'s
labelled-reason/double-submit-prevention pattern unchanged; status
always paired with text via `Badge`, never colour alone; loading/
empty/error-with-retry states on every list/queue; no content flash on
either role guard (`OrganizationRoute`/`AdminRoute` render a checking-
session state until the account's role is known). Verified responsive
at the same breakpoints Milestone 9A established.

## Tests Executed

`./mvnw test` (backend, against a real Testcontainers PostgreSQL/
PostGIS instance), `npm test` / `npm run lint` / `npx tsc --noEmit` /
`npm run build` (frontend).

## Authoritative Backend Test Results

**738/738 tests pass** (684 inherited unchanged + 54 new: 12
`OrganizationServiceIntegrationTest`, 10
`AdminOrganizationServiceIntegrationTest`, 10
`ResourceOwnershipClaimServiceIntegrationTest`, 8
`AdminResourceOwnershipClaimServiceIntegrationTest`, 2
`OrganizationConcurrencyIntegrationTest` (real two-thread races — one
for organization verification, one for two claims racing to own the
same resource), 12 `OrganizationApiIntegrationTest` (full HTTP-layer
role matrix and end-to-end flow), plus `FlywayMigrationIntegrationTest`
and `ResourceRepositoryIntegrationTest`'s column-list assertion updated
for `V11`/`organization_id`), 0 failures, 0 errors, 0 skipped.

## Authoritative Frontend Test Results

**539/539 tests pass** (478 inherited unchanged + 61 new) across 69
suites, 0 failures, per `npm test`.

## Build, CI, and Quality Results

`npm run lint`, `npx tsc --noEmit`, and `npm run build` (production) —
all clean. All twelve new `/api/v1/organizations/**` and
`/api/v1/admin/**` operations confirmed present, with correct HTTP
verbs, in `GET /v3/api-docs` against the live running application.

## Manual Verification

A live, scripted pass against the real backend (real PostgreSQL, real
JWT issuance, `docker exec psql` used only to promote three fresh
accounts to `ORGANIZATION`/`ORGANIZATION`/`ADMIN` and one to
`MODERATOR`, since no role-assignment endpoint exists) plus the real
frontend dev server, covering:

- `ADMIN` cannot create an organization profile (`403`); `ORGANIZATION`
  can, and it starts `PENDING_VERIFICATION`.
- The organization's public profile is `404` while pending.
- `ADMIN` verifies it; the public profile then returns `200` with only
  safe fields (no `ownerUserId`/`verifiedByUserId` in the public body).
- A second, different `ORGANIZATION` account is also verified.
- Both organizations submit a claim for the same still-unowned
  resource — both succeed (a claim is not itself ownership).
- `ADMIN` approves the first claim — `resources.organization_id` is
  assigned, the public resource detail now shows organization
  attribution, and the organization's own `/resources` list shows it.
- Approving the second (now-stale) claim for the same resource returns
  `409 RESOURCE_ALREADY_OWNED`, and the claim is left `PENDING_REVIEW`
  — confirmed it was not silently mutated by the failed attempt.
- `ADMIN` explicitly rejects the stale claim.
- Suspending the first organization: its public profile becomes `404`
  again, but the resource it owns stays `active: true` and public,
  with `organization: null` on its public detail response — ownership
  itself untouched.
- `MODERATOR` receives `403` on both `/api/v1/admin/organizations` and
  `/api/v1/admin/resource-ownership-claims` — confirming these are
  `ADMIN`-only, unlike `/api/v1/moderation/**`.
- A fresh claim on a second resource: submitting a second, duplicate
  pending claim for the same organization/resource pair returns `409
  RESOURCE_OWNERSHIP_CLAIM_CONFLICT`; withdrawing it succeeds; a
  different organization attempting to withdraw it returns `404` (no
  ownership leak).
- In the browser: `/organization` and `/admin/organizations` both
  render (`200`) through the real Next.js dev server; the resource
  detail page for the (by then suspended-owner) resource correctly
  omits the "Owned by" attribution line via a real server-rendered
  fetch, not just the raw API response.
- The two automated concurrency tests (`OrganizationConcurrencyIntegrationTest`)
  independently prove, with real concurrent HTTP-equivalent service
  calls on separate threads/transactions, that exactly one of two
  simultaneous organization verifications succeeds, and exactly one of
  two simultaneous claim approvals for the same resource succeeds.

Both manual servers (backend on `:8080`, frontend on `:3000`) were
started against the real dev database (docker-compose Postgres/PostGIS
on port `55432`) and stopped cleanly after verification.

## OpenAPI Verification

`GET /v3/api-docs` against the live running application confirms all
twelve new operations across `/api/v1/organizations/**` and
`/api/v1/admin/**`, each with the correct HTTP verb.

## Security Review

- `ORGANIZATION` role and `VERIFIED` organization status are checked
  independently at every relevant call site — never inferred from one
  another; confirmed live (a verified-but-wrong-role and a
  right-role-but-unverified account both correctly denied a claim).
- `resources.organization_id` is the only ownership authority read
  anywhere in a response — no code path derives current ownership from
  a claim's `status` alone (see ADR-017).
- Self-review is enforced against the current database owner column
  for both organization decisions and claim decisions, for every role
  including `ADMIN` — confirmed live and by dedicated automated tests.
- No endpoint accepts a client-supplied `ownerUserId`, `organizationId`
  (on a claim it submits for itself), verification status, or reviewer
  identity — every one of those is server-assigned.
- Claim-approval concurrency is enforced by real database row locks
  (claim row + resource row), not an application-level pre-check
  alone — proven by an actual two-thread concurrent test.
- `git grep` across the new backend/frontend files for
  `organizationId`, `ownerUser`, `verificationStatus`, `organization_id`,
  `localStorage`, `sessionStorage`, `accessToken`, `refreshToken`,
  `console.log`, `dangerouslySetInnerHTML` shows no client-side trust
  logic and no logging/storage of session material.

## Privacy Review

- `organization_audit_events` and every `/audit-events` route are
  `ADMIN`-only — never reachable from a public or owner-facing route.
- The public organization profile and the public resource `organization`
  summary never include `ownerUserId`, `verifiedByUserId`, or
  `verificationReason` — confirmed by direct inspection of the live
  response body.
- A pending/rejected/suspended organization's profile is `404`
  publicly, not a redacted `200` — indistinguishable from a slug that
  never existed.
- The frontend's private-cache clearing (logout/account-switch) now
  also covers `"organization"`, `"admin-organizations"`, and
  `"admin-ownership-claims"` — confirmed by an automated test asserting
  a public organization-profile cache entry deliberately survives the
  same clear.

## Performance Review

- Organization/claim queue rows are compact DTOs; queue queries are
  simple, filtered, paginated lookups.
- Public organization attribution is batch-loaded per page of results
  (search, nearby, and the owned-resource list all use one query for
  the whole page), the same "never one request per card" posture
  Milestone 6B's hours loader established — confirmed by reading the
  implementation, not by a load benchmark.
- The `organization_audit_events(organization_id)` index supports the
  per-organization audit-history query.
- No production-scale concurrency/load benchmark was run — stated
  honestly, matching Milestone 9A's own precedent; correctness under
  concurrency is proven by real two-thread tests, not a load test.

## Acceptance Criteria

**Database**

- [x] `V11` adds `organizations`, `resources.organization_id`,
      `resource_ownership_claims`, `organization_audit_events`; `V1`-`V10`
      unchanged.
- [x] One profile per owner (real unique index); unique slug.
- [x] Verification/review metadata `CHECK`-constrained present-together/
      absent-together.
- [x] At most one `PENDING_REVIEW` claim per organization/resource pair.
- [x] `resources.organization_id` → `SET NULL`; claim/owner FKs →
      `RESTRICT`.

**Organization identity and verification**

- [x] `ORGANIZATION` role ≠ `VERIFIED` status, enforced independently.
- [x] Profile always starts `PENDING_VERIFICATION`; client cannot set
      verification status.
- [x] Identity-significant edits reset verification; contact-only
      edits do not.
- [x] Only `ADMIN` verifies/rejects/suspends; self-review blocked with
      no `ADMIN` exemption.
- [x] Concurrent verification: exactly one admin wins.

**Resource ownership**

- [x] Only a `VERIFIED` organization may claim; re-verified fresh at
      approval time.
- [x] `resources.organization_id` is the sole ownership authority.
- [x] Approval assigns ownership atomically; a losing concurrent claim
      fails safely without corrupting state.
- [x] Suspension never removes existing ownership or deactivates the
      resource.
- [x] Public attribution appears only for a currently-`VERIFIED` owner.

**Frontend**

- [x] Role guards: `OrganizationRoute` (`ORGANIZATION`-only),
      `AdminRoute` (`ADMIN`-only) — loading/access-denied/redirect
      states correct, no content flash.
- [x] Profile create/edit, owned-resources list, claims list (with
      withdraw), admin organization/claim queues and detail pages, all
      complete, accessible, responsive.
- [x] Claim control on the public resource detail page, gated on real
      fetched verification state, never assumed.
- [x] Cache invalidation confirmed to cover admin/org/public-resource
      queries as applicable, and to spare public organization data on
      logout.

**Testing** — 738/738 backend, 539/539 frontend, all quality gates
clean (see above).

**Manual verification** — full live scripted pass against the real
running stack, including two real concurrency races and the complete
create → verify → claim → approve → suspend lifecycle (see above).

**Documentation** — this document, ADR-017, tasks 046-048, and every
file listed under "Documentation Updated" in the completion report.

## Known Limitations (as of Milestone 10A)

- One owner account per organization — no staff/membership model, no
  invitations, no multiple owners. The schema (`owner_user_id` as a
  plain column) does not preclude a future multi-member model, but
  nothing here builds toward one speculatively.
- No organization analytics, no automated business-registry
  verification, no uploaded verification documents.
- No ownership-transfer workflow and no ownership-revocation UI —
  suspending an organization intentionally leaves existing ownership
  in place (see ADR-017's "Organization Suspension").
- No organization-managed resource-editing UI — an owned resource's
  own fields are still only ever edited through the existing
  `ADMIN`/`MODERATOR` resource-update path or the moderation workflow.
- No event CRUD, event search, or expiry handling — deferred to
  Milestone 10B.
- No email notifications for verification/claim decisions.
- `ORGANIZATION` role assignment itself stays outside this workflow
  (an existing account is promoted only via direct database access in
  this dev environment, matching every other role-assignment
  limitation already documented since Milestone 5C).
- No organization self-service profile deletion.
- No production-scale concurrency/load benchmark — concurrency
  correctness is proven by real two-thread tests, not load-tested at
  scale, matching every prior milestone's own precedent.

## Risks

| Risk | Mitigation |
|---|---|
| Two admins verifying the same organization concurrently could both succeed | A real `PESSIMISTIC_WRITE` row lock serializes the whole decision transaction; proven with an actual two-thread concurrent test |
| Two different organizations' claims could both be approved for the same resource, corrupting ownership | Claim approval additionally locks the *target resource* row (not just the claim row) and re-verifies `organization_id IS NULL` under that lock; proven with an actual two-thread concurrent test racing two different claim ids against the same resource |
| An organization could silently regain a "verified" badge after changing its identity | Identity-significant field changes (name, website) always reset verification to pending — no code path allows keeping `VERIFIED` through such an edit |
| A client could attempt to assign ownership directly, or claim on another organization's behalf | Ownership is always derived server-side from the authenticated principal → that account's own organization profile → `resources.organization_id`; no endpoint accepts a caller-supplied organization id, owner id, or resource-ownership value |
| Suspending an organization could accidentally cascade into deactivating or hiding resources it owns | `suspend` only ever changes the organization's own row; resource attribution is derived by a query-level `VERIFIED`-only filter, never by mutating the resource itself |

## Completion Summary

All planned Milestone 10A deliverables were completed and verified
three ways: 738 automated backend tests (684 inherited unchanged, 54
new, including two real two-thread concurrency races) plus 539
automated frontend tests (478 inherited unchanged, 61 new), a live
scripted manual verification pass against the real running stack
covering the complete organization lifecycle (profile creation through
verification, ownership claim submission through approval, and
suspension), and a full regression pass confirming Milestones 5-9A
remain unaffected. No defects were found during manual verification
that automated tests had missed.
