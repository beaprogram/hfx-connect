# Task 017: Frontend Tests, Quality Gates, and Manual Verification

## Objective

Bring Milestone 4's implementation to a verified, complete state: automated
test coverage across the new API client/formatting/component/page layers,
a clean lint/typecheck/build, a backend regression check, and manual
verification of the running application.

## Context

Part of Milestone 4 (Public Frontend) — see
`docs/milestones/milestone-04-public-frontend.md`. This task also covers the
mid-milestone environment problem (disk space) and its resolution, since
that directly affected how verification had to be carried out — see the
milestone doc's "A Note on This Session's Environment."

## Scope

- 82 Jest/React Testing Library tests across 18 suites (see the milestone
  doc's Testing section for the full breakdown).
- `npm run lint`, `npm run typecheck`, `npm run build`, `npm audit` —
  executed from the SSD-relocated project copy once the original disk's
  I/O degradation made these hang indefinitely.
- `./mvnw clean verify` (backend) — confirms Milestone 4's changes (CORS
  config) introduced no regression in the existing 147 backend tests, plus
  2 new CORS tests (149 total).
- Manual `curl`-based verification of the running dev server against the
  running backend: homepage/list/detail rendering, category filtering,
  invalid-parameter handling, not-found behavior, CORS preflight/actual
  requests, and a real backend stop/restart cycle.

## Out of Scope

Playwright/end-to-end browser automation — no browser-automation tool was
available in this environment; full end-to-end coverage remains Milestone
11's responsibility per the milestone brief.

## Acceptance Criteria

- Jest, lint, typecheck, and build all pass cleanly from a fresh
  `npm install`.
- Backend suite remains 100% passing after the CORS change.
- The two real defects caught during test-writing (duplicate "Reset
  filters" link; an ambiguous test regex) are both fixed, not just noted.
- Manual verification results are recorded honestly, including what
  *couldn't* be verified without a graphical browser (see the milestone
  doc's Manual Verification section).

## Technical Approach

When `npm test`/`tsc --noEmit` began hanging indefinitely (traced to the
development machine's internal disk being within ~1.6GB of full, not to
this project's code — confirmed by a plain `cat` of a small tracked file
also hanging), the project was relocated to an external SSD with the
project owner's explicit direction: first a fast git-history-only copy,
then an incremental source-only `rsync` (excluding `node_modules`/`.next`/
`target`, which were instead freshly regenerated via `npm install`/
`./mvnw` on the SSD — far faster than copying tens of thousands of small
files at the original disk's degraded I/O rate), verified byte-for-byte
identical to the original via `diff -rq` before treating the SSD copy as
canonical for all further work.

## Testing Requirements

See Scope above — this task *is* the testing/verification pass for the
whole milestone.

## Result

Completed. 82/82 Jest tests, 149/149 backend tests, clean lint/typecheck/
build. One pre-existing, unrelated `sharp`/Next.js transitive dependency
vulnerability remains (fixable only by downgrading Next.js to a major
version behind the one this project deliberately uses) — accepted and
documented rather than silently ignored, since this milestone doesn't use
`next/image` at all, so its practical exposure is zero.

## Related Commits

`test: add frontend component, API client, and page-level test coverage`
`docs: document Milestone 4 public frontend`
