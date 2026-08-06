# ADR-016: Moderation Workflow — Concurrency, Publication, Correction Application, and Audit Design

## Status

Accepted — 2026-08-06

## Context

Milestone 9A gives a MODERATOR/ADMIN the authority Milestone 8B
deliberately withheld: reviewing a pending resource submission or
correction report and turning that decision into a real effect on the
public dataset (publishing a resource, applying a correction,
deactivating a resource). Several decisions need making before writing
code, each with a real correctness or security consequence if gotten
wrong:

- How to guarantee that two moderators racing to decide the same
  pending item can never both "win" — never two public resources from
  one submission, never a correction applied twice, never a
  torn/partial resource update.
- How to stop a moderator/admin from reviewing their own contribution,
  including specifically preventing ADMIN from bypassing the rule.
- How review decisions read: as one record, or as several append-only
  audit rows, and what a "snapshot" in that trail is allowed to
  contain.
- What "verified" means once a moderator has acted, and whether the
  resource schema needs a new column to express it.
- Which of a correction report's proposed fields this workflow is
  honestly capable of applying automatically, and what happens for the
  two issue types (`OPERATING_HOURS`, `DUPLICATE_RESOURCE`) it is not.
- What the contribution's owner is allowed to see about a decision
  versus what only a moderator may see.

## Decision

### Concurrency Control: Row-Level `PESSIMISTIC_WRITE` Locking, Not `@Version`

Both `ResourceSubmissionRepository.findByIdForReview` and
`CorrectionReportRepository.findByIdForReview` acquire a
`PESSIMISTIC_WRITE` lock (`SELECT ... FOR UPDATE`) on the contribution
row for the entire review transaction, rather than using JPA
optimistic locking (`@Version`) with a retry/exception-translation
path. A second moderator's concurrent review call on the same id
blocks at that `SELECT ... FOR UPDATE` until the first transaction
commits or rolls back, then re-reads the now-current (no longer
`PENDING_REVIEW`) status and fails fast with `409
CONTRIBUTION_ALREADY_REVIEWED`. This was chosen over `@Version`
because it makes "the database transaction is authoritative" maximally
literal — the second moderator's entire review transaction (including
its own field revalidation and any resource mutation it would have
performed) is serialized behind the first one, not just the final
`UPDATE` statement, and there is no separate retry-and-detect-conflict
code path to get subtly wrong.

The same problem exists one level down for correction reports
specifically: two *different* pending reports can target the *same*
resource, and applying both concurrently without locking the resource
row itself would be a classic lost-update race (one moderator's
committed field change silently overwritten by the other's stale
in-memory copy). `ResourceRepository.findByIdForUpdate` locks the
target resource row for the duration of the apply step, layered on top
of — not instead of — the report's own row lock.

### Self-Review Prevention Applies Equally to ADMIN

`SelfReviewNotAllowedException` (`403 SELF_REVIEW_NOT_ALLOWED`) is
checked against the current database identity (`principal.userId()`
compared to the contribution's own owning-account column) immediately
after acquiring the row lock, before any other business check. ADMIN
gets no special exemption — the check is a conflict-of-interest rule,
not a permission gate, and Milestone 9A's scope explicitly does not
introduce a bypass for the highest-privileged role.

### Audit Model: One Row Per Concrete Effect, Not One Row Per Decision

`moderation_audit_events` is append-only — no application code path
ever updates or deletes a row. The rule this workflow follows
consistently: `ModerationAction.REVIEW_DECISION` is used only when a
decision produces no concrete resource-table effect (every rejection,
and an approval where the moderator applied no change and requested no
deactivation); `RESOURCE_CREATED`, `RESOURCE_UPDATED`, and
`RESOURCE_DEACTIVATED` are used exactly when there is one, and that
row itself carries the decision/reason/actor directly rather than
needing a separate companion `REVIEW_DECISION` row for the same
decision. A single correction approval can therefore produce two rows
(applying changes *and* deactivating), but a rejection or a no-op
approval always produces exactly one. This was chosen over "always one
row per API call" specifically so the audit trail answers "what
actually happened to the public data" directly from the `action`
column, without a reader needing to cross-reference a separate
decision row.

`actorEmail` is captured as a point-in-time snapshot on the audit row
itself (the same reasoning `correction_reports.resource_name_snapshot`
already established in V9) rather than requiring a join back to
`users` on every audit read — a moderator's audit entries stay
independently readable even if that account's email later changes,
and `actor_user_id`'s `ON DELETE RESTRICT` means the join would always
be safe anyway, but the snapshot avoids it being necessary at all.

### Snapshot Policy: Small, Explicit Field Maps — Never a Serialized Entity

`beforeSnapshot`/`afterSnapshot` are `Map<String, Object>` values built
field-by-field in `ModerationSnapshots` (submission/resource creation)
or inline in `CorrectionReportReviewService` (only the fields a report
actually proposed changing, before and after). No snapshot is ever
produced by reflecting over or serializing a JPA entity. This is a
hard rule, not a style preference: an entity-wide serialization would
be one missed `@JsonIgnore` away from leaking a password hash, a
refresh-session token, or an unrelated internal field into a table
with no access-control layer of its own beyond "is this account a
moderator." Explicit allowlists make that class of leak structurally
impossible rather than dependent on remembering to exclude fields.

### Verification Policy: Approval Means `VERIFIED`, with a Real Timestamp

An approved resource submission is published `VERIFIED` immediately,
and an approved correction report marks its target resource `VERIFIED`
once its changes are applied — both stamped with a real
`lastVerifiedAt` (a new `resources.last_verified_at` column, added by
V10; the field did not exist before this milestone since nothing
before Milestone 9A could ever move a resource off `UNVERIFIED`). This
is deliberately narrower than "moderator activity implies broad trust
in a resource": a direct `POST /api/v1/resources` (the existing
ADMIN/MODERATOR resource-creation path) is unaffected and still starts
every resource `UNVERIFIED` — only a *contribution reviewed through
this workflow* earns `VERIFIED`, because that is the one path where a
human has actually read and checked the complete record. `
ResourceService.createVerified` and `CommunityResource.markVerified`
are the only two ways `VERIFIED`/`lastVerifiedAt` can ever be set;
there is deliberately no mutator that moves a resource back to
`UNVERIFIED` — nothing in this milestone's scope needs that direction.

A real defect surfaced during this milestone's own manual end-to-end
verification pass: `lastVerifiedAt` was added to `CommunityResource`
and threaded through the *moderation-facing* audit snapshots
correctly, but the *public* `GET /api/v1/resources/{id}` and
`GET /api/v1/resources/slug/{slug}` response DTO
(`ResourceResponse`, via the shared `ResourceDetails` business-layer
record) was never updated to expose it — an entity/service-layer
change that compiled cleanly and passed every unit/integration test
written against the entity and the moderation-facing responses, but
silently never reached the actual public API contract. Caught only by
live-curling a real approved resource and reading the response JSON,
not by any automated test, because no automated test asserted the
*public* response DTO's field set end-to-end. Fixed by adding
`lastVerifiedAt` to both `ResourceDetails` and `ResourceResponse`, plus
a permanent regression test
(`ModerationResourceSubmissionApiIntegrationTest
.publishedResourceExposesVerificationStatusAndLastVerifiedAtPublicly`)
asserting the field actually appears in the live HTTP response body,
not just at the entity/service layer.

### Correction Application Policy: Only What V9's Actual Schema Supports

Applying a correction report's changes merges whichever `proposed*`
fields the report actually has non-null (name, description, address
lines, city, province, postal code, phone, email, website, cost type,
cost details, eligibility) onto the target resource's *current*
values, revalidates the merged result with `ResourceValidation`'s same
field-level rules resource creation/update already use, then applies
it via `CommunityResource.updateDetails`. Category is never touched —
`CorrectionReport` has no `proposedCategory` field (V9's actual
schema, not an idealized one), so there is nothing to apply.

`IssueType.OPERATING_HOURS` and `IssueType.DUPLICATE_RESOURCE` have no
automated field mapping and `applyProposedChanges=true` is rejected
with `400 UNSUPPORTED_CORRECTION_APPLICATION` for either: V9 never
captured a structured proposed schedule (only free-text `explanation`),
so there is no schedule to apply without inventing one, and this
workflow does not implement automated duplicate-listing merging or
deletion. A moderator handles either case by rejecting with an
explanatory reason, or by using the existing, separate
operating-hours-update endpoint manually. This is an honest limitation,
not a deferred feature disguised as done.

`IssueType.RESOURCE_CLOSED` is the only issue type
`deactivateResource=true` is legal for — `400
INVALID_DEACTIVATION_REQUEST` otherwise. `applyProposedChanges` and
`deactivateResource` are independent flags; a moderator may approve
with neither set, which is a legitimate "reviewed, no automatic
public-data change" outcome (recorded as a bare `REVIEW_DECISION` audit
row per the audit model above).

### Owner-Visible Reason vs. Moderator-Only Audit Data

`ResourceSubmissionResponse`/`CorrectionReportResponse` (the
owner-facing DTOs from Milestone 8B) gain `reviewedAt`, `reviewReason`,
and (for submissions) `resultingResource` — but never the reviewing
moderator's identity. `reviewedByUserId` exists only on the
*moderation-facing* detail responses
(`ResourceSubmissionModerationDetailResponse`/
`CorrectionReportModerationDetailResponse`), and even there it is a
raw account id, not resolved to a display name — a moderator wanting a
colleague's human-readable identity uses the linked audit-events
endpoint, whose entries already carry a point-in-time `actorEmail`
snapshot. The full moderator-only audit trail
(`moderation_audit_events`, and every `/audit-events` route) is never
reachable from any owner-facing or public route. `review_reason` is
required for every decision and is always the same text the owner
sees — this workflow has no separate "private moderator note" field,
deliberately: introducing one would create a place operational
commentary about a specific person could accumulate with no owner
visibility or audit accountability.

### Audit API Scope: `contributionType`/`decision` Filters Only

The global audit list (`GET /api/v1/moderation/audit-events`) supports
independently optional `contributionType` and `decision` filters, using
the same `(:param IS NULL OR ...)` pattern `ResourceRepository.search`
already established. `actorId` and date-range filtering are
deliberately not implemented — nothing in this milestone's
requirements justifies the added query/index complexity yet, and
either can be added later without a breaking change to the route.

## Consequences

- Two independent row-level locks (contribution row, and — for
  corrections — the target resource row) are now a real pattern in
  this codebase for the first time; a future workflow touching more
  than one row atomically should look at
  `CorrectionReportReviewService.approve` before inventing a different
  mechanism.
- `moderation_audit_events` is a second place (after
  `resource_submissions`/`correction_reports`) using JSONB columns —
  `Map<String, Object>` fields via Hibernate's `@JdbcTypeCode(SqlTypes.JSON)`,
  a pattern this codebase had not used before this milestone.
- A resource created via the public `POST /api/v1/resources` endpoint
  and a resource published via an approved submission are now
  observably different at creation time (`UNVERIFIED` vs. `VERIFIED`)
  even though both produce an otherwise-identical `CommunityResource`
  row — a deliberate, documented asymmetry, not an inconsistency.
- The `lastVerifiedAt` exposure gap (see above) is a standing reminder,
  now written down rather than just fixed: a schema/entity change that
  compiles and passes entity-layer tests can still silently fail to
  reach a *different* response DTO that happens to share the same
  underlying data. Future fields added to `CommunityResource` should be
  checked against every response DTO that claims to expose "full
  resource detail," not just the one the change was made for.
- No reviewer assignment, private moderator notes, bulk review, appeal
  workflow, email notification, automated spam scoring, automated
  duplicate merge, decision reversal, or audit export exists yet — see
  the milestone doc's Known Limitations for the complete list.
