# HFX Connect — Frontend

The Next.js application for HFX Connect. See the [repository root README](../README.md)
for the product overview, and [docs/](../docs/) for the full product and architecture
documentation.

**Status:** public browsing experience (Milestone 4) — a homepage, a filterable/sortable/
searchable paginated resource list (keyword search added in Milestone 6A;
cost/verification/open-now filters and weekly-schedule display added in
Milestone 6B), a resource detail page, and an interactive **Map** view
(Milestone 7B — Leaflet/OpenStreetMap, browser geolocation, marker
clustering, list/map synchronization, consuming Milestone 7A's PostGIS
nearby-search API), all backed by the real Category and Resource APIs
(Milestones 3A/3C/6A/6B/7A) —
plus real authentication (Milestone 5C): `/login`, `/register`, and a protected
`/dashboard`, with an in-memory access token and refresh-cookie-based session
restoration, a private **Saved Resources** feature (Milestone 8A):
save/remove a resource from its card or detail page, and browse a paginated
Saved Resources section on the dashboard, and, as of Milestone 8B, two
community-contribution workflows: propose a new resource
(`/submit-resource`) and report an issue on an existing one
(`/resources/[slug]/report`), each tracked in its own paginated
dashboard section with a withdraw action on still-pending items. See
[docs/architecture/frontend-architecture.md](../docs/architecture/frontend-architecture.md#authentication-architecture)
for the authentication design, its
["URL State" section](../docs/architecture/frontend-architecture.md#url-state-resources)
for the search design, its
["Interactive Map Architecture" section](../docs/architecture/frontend-architecture.md#interactive-map-architecture)
for the map, and its
["Saved Resources and Private-Data Cache Isolation" section](../docs/architecture/frontend-architecture.md#saved-resources-and-private-data-cache-isolation)
for the private-data cache pattern shared by saved resources,
submissions, and correction reports. Role-specific dashboards are not
implemented yet.

## Stack

Next.js (App Router) with TypeScript in strict mode, Tailwind CSS, TanStack Query, Zod,
Leaflet/React Leaflet (Milestone 7B — see
[ADR-013](../docs/decisions/ADR-013-interactive-map-and-geolocation-design.md)),
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
        layout.tsx                    Mounts MapSearchProvider (Milestone 7B)
        page.tsx                     Resource list/map (filter/sort/pagination,
                                            or the Map view, see ResourceExplorer)
        loading.tsx, error.tsx
        [slug]/
          page.tsx                     Resource detail
          loading.tsx, not-found.tsx, error.tsx
          report/page.tsx           Correction-report form (protected, Milestone 8B)
      login/page.tsx           Login (Milestone 5C)
      register/page.tsx       Registration — does not log the caller in (Milestone 5C)
      submit-resource/page.tsx  Resource-submission form (protected, Milestone 8B)
      dashboard/
        page.tsx                     Protected: the current authenticated account (Milestone 5C)
        submissions/[id]/page.tsx        Owned submission detail (Milestone 8B)
        correction-reports/[id]/page.tsx  Owned report detail (Milestone 8B)
    components/
      categories/               Category card/grid
      resources/                Resource card/grid, filter form (incl. keyword search
                                            (6A) and cost/verification/open-now filters
                                            (6B)), pagination, detail (incl. weekly
                                            schedule, Milestone 6B, and a "Report
                                            incorrect information" link, Milestone 8B),
                                            status-badges, ResourceExplorer/
                                            MapExplorerView/NearbyMapView/ViewToggle
                                            (Milestone 7B), SaveResourceButton/
                                            ResourceDetailSaveControl (Milestone 8A)
      map/                          The Leaflet map, marker clustering, "Use my
                                            location", radius selector, "Search this
                                            area", nearby pagination (Milestone 7B —
                                            client-only, loaded via next/dynamic)
      contributions/             SubmitResourceForm, CorrectionReportForm,
                                            ResourceSubmissionsSection/
                                            CorrectionReportsSection (dashboard),
                                            ResourceSubmissionDetail/
                                            CorrectionReportDetail, ContributionStatusBadge
                                            (Milestone 8B)
      navigation/                Mobile disclosure nav (auth-aware as of Milestone 5C)
      auth/                          Login/register forms, dashboard content, the
                                            protected-route guard (returnTo-aware,
                                            Milestone 8B), the auth-aware nav link
                                            (Milestone 5C), and SavedResourcesSection
                                            (Milestone 8A)
      feedback/                    Shared badge/empty-state/error components
      site-header.tsx, site-footer.tsx
    lib/
      api/                          Typed API client (client.ts) + one module per
                                            resource (categories.ts, resources.ts, auth.ts,
                                            saved-resources.ts — Milestone 8A;
                                            resource-submissions.ts,
                                            correction-reports.ts — Milestone 8B)
      auth/                         AuthProvider/useAuth — the in-memory session
                                            (Milestone 5C; clears every private-data
                                            cache prefix on logout/account switch,
                                            Milestone 8A/8B) — and return-to.ts's
                                            open-redirect-safe returnTo validation, now
                                            also used by ProtectedRoute (Milestone 8B)
      map/                          MapSearchProvider/useMapSearch (session-scoped
                                            centre/radius/geolocation/selection state,
                                            mounted at the /resources layout) and
                                            useGeolocation (Milestone 7B)
      validation/                Zod schemas mirroring the real backend contract
      query/                       TanStack Query client/provider/keys, URL-param parsing
      formatting/               Label/date/address/distance formatting helpers
      constants/                 Allowlisted sort values, page sizes, map defaults
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
`getResources` accepts an optional `q` (keyword search, Milestone 6A) and
optional `costType`/`verificationStatus`/`openNow` (Milestone 6B), each
omitted from the request entirely when unset. The same file's
`getNearbyResources` (Milestone 7B) calls Milestone 7A's
`GET /resources/nearby`, validating latitude/longitude/radius synchronously
before any request is sent. `lib/api/saved-resources.ts` (Milestone 8A) adds
`saveResource`/`removeSavedResource` (via `client.ts`'s new
`putNoContent`/`deleteNoContent`), `getSavedResources`, and
`getSavedResourceStatus` — every call requires an access token, since saved
resources are always per-account. `lib/api/resource-submissions.ts`/
`lib/api/correction-reports.ts` (Milestone 8B) add the equivalent
`create`/`get`/`list`/`withdraw` operations for both new contribution
domains, reusing `client.ts`'s existing `postJson` for both creation
(`201`) and withdrawal (`200`, since both return the updated item, not
an empty body). See
`docs/architecture/frontend-architecture.md` for the full data-fetching
strategy, its "Authentication Architecture" section for how the access
token/session are handled, its "URL State (`/resources`)" section for
the search design, and its "Saved Resources and Private-Data Cache
Isolation" section for the private-data cache pattern shared by all
three account-linked domains.

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
  Promises and `error.tsx`'s `unstable_retry` prop) and again during Milestone 7B (the
  `ssr: false` `next/dynamic` restriction was confirmed against this version's own
  bundled documentation, not assumed).
- `npm audit` reports four pre-existing high-severity advisories in `next`/`postcss`/
  `sharp`/`brace-expansion` — none introduced by Milestone 7B's new dependencies
  (`leaflet`, `react-leaflet`, `react-leaflet-cluster`, `leaflet.markercluster`, all
  clean); fixing them requires downgrading Next.js itself, out of scope here.
- Milestone 8A introduced no new dependencies — the saved-resources feature
  reuses the existing API client, TanStack Query, and auth infrastructure
  end to end; `npm audit` is unchanged from Milestone 7B.
- Milestone 8B introduced no new dependencies either — both contribution
  forms and dashboard sections reuse the same API client, TanStack Query,
  and form/validation conventions `register-form.tsx`/
  `resource-filter-form.tsx` already established; `npm audit` is
  unchanged from Milestone 8A.
