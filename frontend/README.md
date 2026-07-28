# HFX Connect — Frontend

The Next.js application for HFX Connect. See the [repository root README](../README.md)
for the product overview, and [docs/](../docs/) for the full product and architecture
documentation.

**Status:** public browsing experience (Milestone 4) — a homepage, a filterable/sortable/
searchable paginated resource list (keyword search added in Milestone 6A), and a resource
detail page, all backed by the real Category and Resource APIs (Milestones 3A/3C/6A) —
plus real authentication (Milestone 5C): `/login`, `/register`, and a protected
`/dashboard`, with an in-memory access token and refresh-cookie-based session
restoration. See
[docs/architecture/frontend-architecture.md](../docs/architecture/frontend-architecture.md#authentication-architecture)
for the authentication design and its
["URL State" section](../docs/architecture/frontend-architecture.md#url-state-resources)
for the search design. Saved resources, submissions, maps, and role-specific
dashboards are not implemented yet.

## Stack

Next.js (App Router) with TypeScript in strict mode, Tailwind CSS, TanStack Query, Zod,
ESLint, and Jest + React Testing Library.

## Local Development

Requires Node.js 20+, npm, and the backend running (see
[backend/README.md](../backend/README.md)) — this app has no meaningful data of its own
without it.

```bash
cp .env.example .env.local   # only needed if the backend isn't on the default port
npm install
npm run dev
```

The app runs at [http://localhost:3000](http://localhost:3000) and expects the backend at
`http://localhost:8080` by default.

## Environment Configuration

| Variable | Purpose |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Base URL of the backend API. Defaults to `http://localhost:8080` if unset. |

`NEXT_PUBLIC_` variables are inlined into the JavaScript bundle sent to the browser at
build time — anyone can read them from the network tab, so **no secret may ever be
stored in one**. `NEXT_PUBLIC_API_BASE_URL` is safe to expose this way because it isn't
sensitive: it's the same URL a browser's own network requests already reveal. Real
`.env.local` files are gitignored; only `.env.example` (a template with no real values)
is committed. Production deployment expects this variable to point at the real deployed
backend origin (see [docs/architecture/system-overview.md](../docs/architecture/system-overview.md)'s
Deployment Path — Vercel frontend + Render backend, two separate origins).

### Backend Connectivity (CORS)

The browser calls the backend directly (no proxy layer) — see
[ADR-006](../docs/decisions/ADR-006-frontend-backend-connectivity.md) for why. This
requires the backend to allow this frontend's origin via CORS
(`com.hfxconnect.common.config.WebCorsConfig`, configured through
`CORS_ALLOWED_ORIGINS` — see [backend/.env.example](../backend/.env.example)), which
already defaults to `http://localhost:3000` for local development. No configuration is
needed for standard local development; production environments must set
`CORS_ALLOWED_ORIGINS` on the backend to the real deployed frontend origin.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Start the development server |
| `npm run build` | Production build |
| `npm start` | Serve the production build |
| `npm run lint` | ESLint |
| `npm run typecheck` | TypeScript compiler check (`tsc --noEmit`), no output emitted |
| `npm test` | Run the Jest test suite once |
| `npm run test:watch` | Run tests in watch mode |

## Project Structure

```
frontend/
  src/
    app/                       Route segments (App Router)
      page.tsx                     Homepage
      resources/
        page.tsx                     Resource list (filter/sort/pagination)
        loading.tsx, error.tsx
        [slug]/
          page.tsx                     Resource detail
          loading.tsx, not-found.tsx, error.tsx
      login/page.tsx           Login (Milestone 5C)
      register/page.tsx       Registration — does not log the caller in (Milestone 5C)
      dashboard/page.tsx     Protected: the current authenticated account (Milestone 5C)
    components/
      categories/               Category card/grid
      resources/                Resource card/grid, filter form (incl. keyword search,
                                            Milestone 6A), pagination, detail
      navigation/                Mobile disclosure nav (auth-aware as of Milestone 5C)
      auth/                          Login/register forms, dashboard content, the
                                            protected-route guard, and the auth-aware
                                            nav link (Milestone 5C)
      feedback/                    Shared badge/empty-state/error components
      site-header.tsx, site-footer.tsx
    lib/
      api/                          Typed API client (client.ts) + one module per
                                            resource (categories.ts, resources.ts, auth.ts)
      auth/                         AuthProvider/useAuth — the in-memory session
                                            (Milestone 5C)
      validation/                Zod schemas mirroring the real backend contract
      query/                       TanStack Query client/provider/keys, URL-param parsing
      formatting/               Label/date/address formatting helpers
      constants/                 Allowlisted sort values, page sizes
  jest.config.mjs        Jest configuration (see the file's own comment for why
                                    this is .mjs rather than .ts)
  jest.setup.ts             Testing Library / jest-dom setup
```

## API Integration

All backend calls go through `lib/api/client.ts` — `getJson` (centralized
query-string encoding, optional `Authorization: Bearer` header), plus
`postJson`/`postNoContent` (added in Milestone 5C for
register/login/refresh/logout, which need a request body and/or
`credentials: "include"` for the refresh cookie). Every one of them maps
non-2xx responses to `ApiRequestError` and validates response shape with Zod
(`ApiResponseShapeError` on a mismatch, so a backend contract drift fails
loudly and safely instead of rendering broken data). `lib/api/categories.ts`,
`lib/api/resources.ts`, and `lib/api/auth.ts` expose the specific operations
the frontend actually needs — nothing speculative. `lib/api/resources.ts`'s
`getResources` accepts an optional `q` (keyword search, Milestone 6A),
omitted from the request entirely when blank. See
`docs/architecture/frontend-architecture.md` for the full data-fetching
strategy, its "Authentication Architecture" section for how the access
token/session are handled, and its "URL State (`/resources`)" section for
the search design.

## Notes

- TypeScript strict mode is enabled, plus `noUncheckedIndexedAccess` for additional
  safety on array/object indexing.
- This project pins a patched `postcss` version via an `overrides` entry in
  `package.json` to resolve a moderate-severity advisory in a dependency bundled
  inside Next.js itself (GHSA-qx2v-qp2m-jg93); this does not affect Next.js's own
  version.
- `AGENTS.md` / `CLAUDE.md` are generated by `create-next-app` and contain a note for
  AI coding agents that this Next.js version may include breaking changes relative to
  older training data — confirmed true during Milestone 4 (see
  `docs/architecture/frontend-architecture.md`'s notes on `params`/`searchParams` as
  Promises and `error.tsx`'s `unstable_retry` prop).
