# ADR-015: Community Contribution Workflows — Data Model, Ownership, and Duplicate-Pending Design

## Status

Accepted — 2026-08-05

## Context

Milestone 8B adds this project's first two-sided community-contribution
workflows: proposing a brand-new resource, and reporting an issue with
an existing one. Both create pending review items only — Milestone 9
owns approving, rejecting, and actually applying either kind of
contribution. Several decisions need making before writing code:

- Whether a submission and a correction report should share one generic
  "contribution"/"report" table, or stay as two focused, differently-
  shaped tables.
- What primary-key strategy fits — the `BIGINT`-identity reasoning
  `saved_resources` and `resource_operating_hours` already established
  (never independently addressable by their own id), or the `UUID`
  reasoning `resources`/`users` established (numerous, created
  continuously, referenced by their own id in a URL).
- What happens to a submission or report if the account that created it
  is later deleted, and what happens to a correction report if its
  target resource is later deleted — two independent deletion-policy
  questions with no obviously-reusable precedent, since
  `saved_resources` (Milestone 8A) deliberately cascades both of its own
  foreign keys away, for reasons that don't automatically transfer here.
- How to stop an accidental double-submit (a duplicate browser tab, a
  slow network retry, an impatient double-click) from creating two
  simultaneous pending items, without blocking a user from genuinely
  submitting the same resource name again later, or reporting a second,
  different issue on the same resource.
- Whether withdrawal (a user cancelling their own still-pending item)
  belongs in this milestone at all, given the blueprint marks it
  optional.

## Decision

### Two Focused Tables, Not One Generic Contribution Table

`resource_submissions` and `correction_reports` remain two separate
tables with two separate entities
(`com.hfxconnect.resourcesubmission.ResourceSubmission`,
`com.hfxconnect.correctionreport.CorrectionReport`), not a single
polymorphic "contribution" table with a discriminator column. Their
shapes are genuinely different: a submission proposes an entire new
resource record (name, address, cost, ...) with no target resource to
reference; a report references an existing resource and carries a
smaller set of optional *proposed* fields plus a required issue type
and explanation. Forcing both through one table would mean every row
carries a large number of columns meaningless for the other kind of
contribution, and every query would need a discriminator check. This
is the same "focused entity over generic framework" reasoning
`SavedResource` already established (ADR-014) applied to a case with
two real shapes instead of one.

### UUID Primary Keys — Because Both Are Addressable by Their Own Id

Both tables use a `UUID` primary key, generated the same way
`resources.id`/`users.id` are (Hibernate-assigned, not `IDENTITY`).
This is a deliberate departure from `saved_resources`/
`resource_operating_hours`'s `BIGINT identity` choice: those rows are
*never* independently addressable by their own id in any URL — they're
always looked up via a natural key (`userId`+`resourceId`,
`resourceId`+`dayOfWeek`). A submission or report has no such natural
key alternative and genuinely is addressable by its own id
(`GET .../resource-submissions/{submissionId}`,
`/dashboard/submissions/{id}`) — the same reasoning that made
`resources.id`/`users.id` `UUID` in the first place (ADR-005/ADR-007),
now correctly extended to a third case with the identical shape rather
than defaulting to whichever precedent was set most recently.

### Deletion Policy: `RESTRICT` on the Owning Account, `SET NULL` on the Target Resource

Both `submitted_by_user_id` and `reported_by_user_id` use
`ON DELETE RESTRICT`, not `CASCADE`. This is the opposite choice from
`saved_resources.user_id`'s `CASCADE` (ADR-014), and deliberately so: a
saved-resource relation has no meaning independent of the account that
saved it, but a submission or report is community-contribution
*history* with standalone value even if the submitting account is
later deleted — deleting the account should not silently destroy the
record that a resource was once proposed, or an issue was once
reported. No user-deletion endpoint exists yet in either direction, so
this is a schema decision made in anticipation of one, the same way
`saved_resources.user_id`'s `CASCADE` was.

`correction_reports.resource_id` uses `ON DELETE SET NULL`, not
`CASCADE` or `RESTRICT` — a third, distinct policy from either
precedent. An unresolved report has standalone review value even after
its target resource is deleted (e.g., "this was reported as a
duplicate, and the duplicate was later removed" is exactly the kind of
history worth keeping). Because the resource reference can become
null, `resource_name_snapshot`/`resource_slug_snapshot` are captured
once at creation time and read directly for display — a report's
detail page never assumes `resource` is present, and never re-derives
the resource's name from a possibly-absent association.

### Duplicate-Pending Guard: A Partial Unique Index, Scoped to `PENDING_REVIEW`

`resource_submissions_pending_duplicate_key` (`(submitted_by_user_id,
category_id, normalized_name)`) and
`correction_reports_pending_duplicate_key` (`(reported_by_user_id,
resource_id, issue_type)`) are both `CREATE UNIQUE INDEX ... WHERE
status = 'PENDING_REVIEW'` — partial indexes, not a plain table-wide
unique constraint. This precisely expresses "at most one *currently
pending* item of this shape," not "you may only ever submit this
resource once" or "you may only ever report one issue on this
resource." A withdrawn, approved, or rejected item never blocks a
later resubmission or re-report of the identical shape, and a user
reporting two genuinely different issues on the same resource (e.g.
`ADDRESS` and `COST`) is never blocked by either index. The database
constraint is the authority the same way `saved_resources`'s own
unique index is (ADR-014) — the service-layer check that runs first is
a normal-path convenience, not the actual guarantee.

### Withdrawal Is Implemented, Flushed Explicitly

Both `ResourceSubmission.withdraw()`/`CorrectionReport.withdraw()`
guard on `status == PENDING_REVIEW`, throwing
`InvalidContributionStatusException` otherwise (shared across both
packages in `com.hfxconnect.common.error`, since the rule and error
shape are byte-for-byte identical). The service layer calls
`saveAndFlush` immediately after mutating the entity, not leaving the
change to implicit auto-flush timing — discovered necessary during
this milestone's own service-layer testing: Hibernate's flush action
queue orders entity *insertions* before *updates* within a single
flush regardless of call order, so a withdrawal immediately followed
by a resubmission of the same shape (two logically separate requests,
but sharing one flush if ever chained without an intervening boundary)
could have its update physically applied to the database *after* the
new row's insert already ran, defeating the duplicate-pending index's
purpose for that one interleaving. Explicit `saveAndFlush` makes the
withdrawal durable before the method returns, independent of whatever
runs next.

### Reusing `ResourceValidation`'s Field-Level Helpers, Not Duplicating Them

`ResourceSubmissionValidation`/`CorrectionReportValidation` both call
directly into `com.hfxconnect.resource.ResourceValidation`'s
province/postal-code/phone/email/website-scheme helpers (now `public
static`, widened specifically for this reuse) rather than
reimplementing the same regex/normalization rules a third time. This
is the "shared pure utilities extracted on second use" pattern this
codebase already documents (`SlugGenerator`, `EmailNormalizer`) —
`ResourceValidation` itself stays in the `resource` package rather
than being promoted to `common`, since resource creation remains its
primary, most complete caller; only the individual field checks are
reused, not `validate()` itself, since submissions and reports each
have genuinely different field sets (a submission's `shortDescription`/
`fullDescription` split and `accessibilityInformation` field don't
exist on `CommunityResource` at all — deliberately not invented for
corrections, which only ever propose values for fields a resource
genuinely has today).

## Consequences

- Two tables and two full sets of repository/service/controller/DTO
  classes were written instead of one shared set — more files, but each
  one stays legible without a discriminator branch, and either domain
  can evolve its own field set independently without touching the
  other.
- A submission's or report's own id is now a meaningful, guessable-format
  identifier a user could in principle enumerate — this is not a
  security concern here specifically because every read path already
  returns an identical `404` for "doesn't exist" and "exists but isn't
  yours," so enumeration reveals nothing about which ids are real or
  who owns them.
- The `RESTRICT`/`SET NULL` deletion policies mean no endpoint anywhere
  can currently delete a user or a resource out from under an existing
  submission or report — correct today (no such delete endpoints
  exist), and the schema is already correct in anticipation of them
  being added later, the same forward-looking posture
  `saved_resources`' own FK policies already took.
- Explicit `saveAndFlush` on withdrawal is a small, deliberate deviation
  from `SavedResourceService`'s lighter-weight save/remove pattern —
  documented here so a future reader doesn't "simplify" it back to a
  bare mutation without understanding the flush-ordering bug it exists
  to prevent.
