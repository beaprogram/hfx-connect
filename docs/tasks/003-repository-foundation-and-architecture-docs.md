# Task 003: Repository Foundation and Architecture Documentation

## Objective

Stand up the repository itself (git, GitHub remote, directory structure), document the
intended system architecture and the three technical decisions already implied by the
chosen stack, document the development workflow, and write a root README that
accurately reflects Milestone 1's actual scope.

## Context

Part of Milestone 1 (Project Foundation), and the task that turns the product
definition (Tasks 001-002) into a repository other people (recruiters, interviewers,
future collaborators, or the developer's own future self) can pick up and understand.

## Scope

- Git repository initialized (`main` branch), public GitHub remote created
  (`beaprogram/hfx-connect`), `.gitignore` added.
- `docs/architecture/system-overview.md`.
- `docs/decisions/ADR-001-monorepo-structure.md`,
  `docs/decisions/ADR-002-postgresql-and-postgis.md`,
  `docs/decisions/ADR-003-rest-api.md`.
- `docs/development-workflow.md`.
- `docs/career/resume-evidence.md`, `docs/career/interview-notes.md` (foundation only).
- `docs/api/README.md`, `docs/database/README.md` (placeholders explaining what will
  populate these directories and when).
- `docs/development-log/2026-07-10.md`.
- Root `README.md`.

## Out of Scope

Any `frontend/` or `backend/` scaffolding, Docker Compose, Flyway migrations, or CI
configuration — all explicitly deferred to Milestone 2.

## Acceptance Criteria

- The repository has a real, pushed `main` branch on a public GitHub remote.
- The documentation directory structure matches what `docs/development-workflow.md`
  describes.
- Each ADR follows the Status/Context/Decision/Alternatives Considered/Consequences
  structure and documents a decision genuinely already implied by the chosen stack
  (not a placeholder decision).
- The root README describes only what exists as of Milestone 1 — it does not claim a
  live URL, screenshots, or implemented features that do not exist yet.
- Every cross-reference between documents resolves to a real file, or explicitly states
  which future milestone will add it.

## Technical Approach

Repository initialized locally with `git init -b main`, GitHub repository created via
`gh repo create` (public, per explicit confirmation), and `main` pushed before
starting milestone work on a dedicated `milestone/01-project-foundation` branch.
Documentation content was written directly against the product definition from Tasks
001-002 and the technology direction already given for this project, translated into
ADR form only where a decision was genuinely already made (monorepo, PostgreSQL +
PostGIS, REST) rather than inventing decisions that belong to later milestones (token
design, CI pipeline shape).

## Testing Requirements

Not applicable (documentation and repository-configuration task). Verified by: `git
log`, `git status`, and a manual read-through of every new document against the
acceptance criteria above and against Section 26 (Recruiter-Readiness Review) of the
project's own working agreement.

## Result

Completed. The repository exists locally and on GitHub
(`https://github.com/beaprogram/hfx-connect`), `main` is pushed, and all listed
documents were created. No application code, schema, or CI was introduced, matching
scope.

## Related Commit

`docs: add architecture overview, ADRs, workflow docs, and README`
