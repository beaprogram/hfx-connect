# Task 005: Frontend Initialization

## Objective

Create a runnable Next.js application shell with strict TypeScript, Tailwind CSS,
linting, type checking, testing, and a production build all working, plus a minimal
accessible and responsive HFX Connect homepage.

## Context

Part of Milestone 2A (Application Initialization). The frontend has no dependency on
the backend yet (no API calls exist), so it was initialized independently and
verified on its own.

## Scope

- Generate the project via `create-next-app`: TypeScript, Tailwind CSS, ESLint, App
  Router, `src/` directory, `@/*` import alias, Turbopack.
- Enable `noUncheckedIndexedAccess` in `tsconfig.json` in addition to the
  already-enabled `strict` mode.
- Install and configure Jest + React Testing Library per the Next.js-recommended
  `next/jest` setup; add `test`, `test:watch`, and `typecheck` npm scripts (only
  `dev`/`build`/`start`/`lint` exist by default).
- Replace the generated boilerplate homepage with an accessible, responsive HFX
  Connect shell: skip-to-content link, `SiteHeader`/`SiteFooter` components, semantic
  landmarks, a single `<h1>`, and honest copy that does not imply unbuilt features
  (search, listings, map) are live.
- Remove unused default template assets (`public/*.svg`) and unused CSS (Geist font
  variables were loaded but never actually applied to the page — fixed by adding
  `font-sans` to the body and trimming the now-redundant background/foreground CSS
  variables and dark-mode media query from `globals.css`).
- Resolve a moderate-severity `npm audit` finding (`postcss` XSS advisory, bundled
  transitively inside `next`'s own dependency tree) via a `postcss` version
  `overrides` entry, since `npm audit fix --force`'s suggested fix would have
  downgraded Next.js from 16.2.10 to 9.3.3 — rejected as unsafe.
- Approve install scripts (`npm approve-scripts`) for `sharp`, `fsevents`, and
  `unrs-resolver` — standard transitive dependencies of Next.js/ESLint, gated by a
  newer npm supply-chain security feature.
- Write `frontend/README.md` with real, verified setup instructions.

## Out of Scope

Any API client, data fetching, authentication, routing beyond the single homepage, or
Docker configuration.

## Acceptance Criteria

- `npm run lint` passes with zero warnings or errors.
- `npm run typecheck` passes.
- `npm test` passes (3 suites, 4 tests, covering the homepage heading/link and both
  shared layout components).
- `npm run build` completes a production build successfully.
- `npm run dev` serves the shell on port 3000 with the expected heading and content
  (verified live via `curl`, not just build-checked).
- `npm audit` reports zero vulnerabilities.
- The shell is keyboard-navigable: the skip link is the first focusable element, and
  all interactive elements have a visible focus state.

## Technical Approach

Scaffolded via `npx create-next-app@latest`. Because this Next.js version
(16.2.10) is materially newer than commonly available reference material, the
project's own bundled guidance (`frontend/AGENTS.md`, which points to
`node_modules/next/dist/docs/`) was consulted before writing App Router code — the
Jest setup and `layout.tsx`/metadata conventions used were cross-checked against the
bundled docs rather than assumed from prior knowledge.

## Testing Requirements

`npm run lint`, `npm run typecheck`, `npm test`, `npm run build` — all run and
passed. The dev server was also started and checked live with `curl` for the expected
`<h1>` content and a `200` response, then stopped.

## Result

Completed. The frontend lints, type-checks, tests, and builds cleanly; the shell is
accessible and responsive; `npm audit` is clean.

## Related Commit

`chore: initialize Next.js frontend with strict TypeScript and Tailwind`
