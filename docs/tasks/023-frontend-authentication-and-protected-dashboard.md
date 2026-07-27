# Task 023: Frontend Authentication and Protected Dashboard

## Objective

Give the frontend real authentication: login and registration pages, an
in-memory session with refresh-cookie-based restoration, and a protected
`/dashboard` route — while documenting explicitly that the route guard is a
UX convenience, not the security boundary the backend already is.

## Context

The third of three tasks completing Milestone 5C. Depends on Tasks 021/022's
backend endpoints (`/api/v1/auth/*`, `/api/v1/users/me`). Read the relevant
guides under `frontend/node_modules/next/dist/docs/01-app/02-guides/`
(`authentication.md`, `single-page-applications.md`) before writing any
code, per `frontend/AGENTS.md`'s explicit instruction that this Next.js
version may differ from training data.

## Scope

- `lib/api/client.ts` — `postJson`/`postNoContent` alongside the existing
  `getJson`; an optional `accessToken` parameter on all three for a Bearer
  header.
- `lib/api/auth.ts` — `register`/`login`/`refreshSession`/`logout`/
  `getCurrentUser`.
- `lib/validation/schemas.ts` — `userResponseSchema`/`loginResponseSchema`/
  `roleSchema`/`accountStatusSchema`.
- `lib/auth/auth-provider.tsx` — `AuthProvider`/`useAuth`: in-memory access
  token only (never `localStorage`/`sessionStorage`), mount-time session
  restoration via the refresh cookie, single-flight refresh coalescing,
  proactive expiry tracking with a clock-skew buffer.
- `components/auth/`: `login-form.tsx`, `register-form.tsx`,
  `dashboard-content.tsx`, `protected-route.tsx`, `auth-nav.tsx`.
- `app/login/page.tsx`, `app/register/page.tsx`, `app/dashboard/page.tsx`.
- `components/site-header.tsx`/`components/navigation/mobile-nav.tsx`
  updated to show Log in / Dashboard+Log out based on session state.
- `app/layout.tsx` wraps the tree in `AuthProvider`.

## Out of Scope

Role-specific dashboards, category/resource creation UI (none exists yet to
hide per-role), saved resources, submissions, moderation, organization
tooling, password reset, email verification, MFA, OAuth.

## Acceptance Criteria

- [x] `/login`: email/password fields, correct `autocomplete` (`email`/
      `current-password`), one generic failure message regardless of the
      underlying error, redirect to `/dashboard` on success, link to
      `/register`.
- [x] `/register`: email/password fields (`autocomplete="new-password"`,
      an `aria-describedby` password hint), server field-error display,
      duplicate-account handling (`409` → generic message), success →
      message + link to `/login` — no fabricated auto-login (the backend
      endpoint doesn't return a session).
- [x] `/dashboard`: requires an authenticated session, calls
      `GET /api/v1/users/me`, shows only safe fields (email/role/status)
      and a logout control — no fabricated features.
- [x] The access token is never written to `localStorage`/`sessionStorage`
      (`git grep` across `frontend/src` is clean; a dedicated test spies on
      `Storage.prototype.setItem`).
- [x] A page reload restores the session via the refresh cookie, or lands
      cleanly on `"unauthenticated"` — never an error state.
- [x] Concurrent calls to `getValidAccessToken()` while a refresh is
      in-flight share one request (single-flight), verified by a dedicated
      test.
- [x] The protected-route guard never renders dashboard content before
      authentication is confirmed, and documents that it is UX-only.
- [x] `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` all
      pass; `npm audit` reviewed (pre-existing, unrelated transitive
      vulnerabilities only).

## Technical Approach

`AuthProvider` follows the plain client-side Context provider pattern
`node_modules/next/dist/docs/01-app/02-guides/single-page-applications.md`
documents as fully supported alongside TanStack Query for this Next.js
version — nothing here needs Server Component access to session data, since
every backend call is client-side to a separate origin (ADR-006), not a
Next.js server reading its own cookies. The protected-route guard is a
client component, not Next.js middleware/Proxy, because
`.../02-guides/authentication.md`'s own "Optimistic checks with Proxy"
section describes reading a session from `cookies()` — which only works for
a cookie set on the Next.js app's own domain; this project's refresh cookie
belongs to the backend's separate origin, so no Next.js-server-side check
could ever validate it (see ADR-009 for the full reasoning).

## Testing Requirements

`npm test`. 38 new tests: API client (`postJson`/`postNoContent`/Bearer
header), `lib/api/auth.ts` operations, `AuthProvider` (loading/authenticated/
unauthenticated transitions, no-storage-write, login/logout, single-flight,
no-infinite-retry), `LoginForm`, `RegisterForm`, `DashboardContent`,
`ProtectedRoute`, plus updated `SiteHeader`/`MobileNav` tests wrapped in
`AuthProvider`. Manual: full session lifecycle (register → login → view
dashboard → reload → logout) exercised against the real running backend via
`curl` with a cookie jar, and the frontend pages' rendered HTML confirmed via
`curl` (no browser-automation tool was available in this environment).

## Result

Completed. 38 new tests pass alongside the 82 inherited from Milestone 4
(120 total, authoritative per `npm test`). `npm run build` succeeds,
producing `/login`, `/register`, `/dashboard` as static routes.

## Related Commits

`feat: add frontend authentication and protected dashboard`,
`test: add authentication and authorization coverage`.
