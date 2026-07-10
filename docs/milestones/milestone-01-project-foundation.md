# Milestone 1: Project Foundation

## Objective

Establish a clear, specific, and honest foundation for HFX Connect before any
application code is written: the problem it solves, who it serves, what the MVP
actually includes, the intended architecture, and the repository/documentation
structure that every later milestone will build on.

## Product Value

A well-defined foundation prevents scope drift across a 12-week build and gives every
later milestone (and every future reader — recruiter, interviewer, or collaborator) a
single, coherent source of truth for why the product exists and what "done" means for
the MVP.

## Technical Scope

- Git repository initialized, with a `main` branch and a public GitHub remote
  (`beaprogram/hfx-connect`).
- Documentation directory structure (`docs/product`, `docs/architecture`,
  `docs/decisions`, `docs/milestones`, `docs/tasks`, `docs/development-log`,
  `docs/career`, `docs/api`, `docs/database`).
- Product definition documents: problem statement, vision, measurable success
  criteria, non-goals, personas, MVP scope and feature priorities, user journeys, user
  stories, and acceptance criteria for the highest-priority stories.
- Initial architecture overview describing the intended system shape, stack, backend
  module layout, and deployment path.
- Architecture decision records for the three foundational technical choices already
  implied by the stack: monorepo structure, PostgreSQL + PostGIS, and REST over JSON.
- Development workflow documentation: branching model, commit conventions,
  milestone process, documentation expectations, and quality gate.
- Root `README.md` describing the product, stack, current status, and repository
  layout.
- Foundational (structure-only) career-evidence and interview-notes documents, to be
  populated once real features exist.
- `.gitignore` covering the planned Next.js and Spring Boot toolchains.

## Out of Scope

- Any frontend or backend application code (`frontend/`, `backend/` directories are
  intentionally not created this milestone).
- Database schema or Flyway migrations.
- Docker Compose configuration.
- CI/CD pipeline.
- Authentication, search, moderation, or any other feature implementation.
- Wireframes/visual design artifacts (noted as a recommended next step, not delivered
  here).

## Acceptance Criteria

- [x] Problem statement, product vision, and non-goals are documented and specific to
      HFX Connect (not generic boilerplate).
- [x] At least five measurable MVP success criteria are defined with a stated
      measurement method.
- [x] Target users and at least four personas are documented, including the primary
      persona used to anchor later acceptance criteria.
- [x] MVP scope is prioritized (must/should/could/later) and mapped to the milestone
      roadmap.
- [x] At least two full user journeys and eight representative user stories are
      documented.
- [x] Acceptance criteria are written for the three highest-priority stories (nearby
      search, correction-report moderation, unauthorized-access rejection).
- [x] The documentation directory structure exists and matches the structure described
      in the development workflow document.
- [x] A root README accurately describes only what currently exists — no unimplemented
      feature is described as complete.
- [x] An initial architecture overview and three ADRs exist for the foundational
      technical decisions already implied by the chosen stack.
- [x] The Git workflow (branching, commit conventions, milestone process) is documented.
- [x] The repository has a public GitHub remote and the `main` branch is pushed.
- [x] All content is internally consistent (cross-references between documents resolve
      to real files or clearly state what milestone will add them).

## Planned Tasks

1. Product definition — problem statement, vision, success criteria, personas, MVP
   scope, non-goals ([docs/tasks/001-product-definition.md](../tasks/001-product-definition.md)).
2. User journeys, stories, and acceptance criteria
   ([docs/tasks/002-user-journeys-and-stories.md](../tasks/002-user-journeys-and-stories.md)).
3. Repository foundation, architecture overview, ADRs, workflow docs, and README
   ([docs/tasks/003-repository-foundation-and-architecture-docs.md](../tasks/003-repository-foundation-and-architecture-docs.md)).

## Testing Requirements

Not applicable — this milestone produces no executable code. The verification method
for this milestone is a documentation review against the acceptance criteria above,
performed before pushing.

## Documentation Requirements

This milestone's output is entirely documentation; see Technical Scope above. All
files listed there constitute the documentation requirement for Milestone 1.

## Security Considerations

No secrets are introduced in this milestone. The `.gitignore` proactively excludes
`.env` files and common secret file patterns before any environment configuration is
added in Milestone 2, so a later mistake is less likely.

## Accessibility Considerations

Not directly applicable to a documentation-only milestone. The accessibility
requirements that will govern later implementation work are captured in the product
and architecture documents (for example, the requirement that map content always has a
list alternative) so they are decided before UI work begins, not retrofitted.

## Risks

| Risk | Mitigation |
|---|---|
| Scope creep across a 12-week solo build | Explicit non-goals and a "must/should/could/later" priority table, revisited every milestone |
| Documentation drifting from implementation | Every later milestone updates the relevant docs as part of its own definition of done, not as a separate cleanup pass |
| Over-designing the architecture before real constraints are known | Architecture overview explicitly states what it does not yet cover, and ADRs are limited to decisions already forced by the chosen stack |

## Completion Summary

All planned Milestone 1 deliverables were completed: product definition, user
journeys/stories with acceptance criteria, architecture overview, three ADRs,
development workflow documentation, documentation directory structure, root README,
and career-evidence/interview-notes scaffolding. The repository was initialized, a
public GitHub remote (`beaprogram/hfx-connect`) was created, and `main` was pushed.
No application code, database schema, or CI/CD was introduced, consistent with the
milestone's explicit scope. The recruiter-readiness review for this milestone is
recorded in the completion report delivered alongside this milestone.
