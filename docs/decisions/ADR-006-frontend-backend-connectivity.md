# ADR-006: Frontend-to-Backend Connectivity (Direct Browser CORS, Not a Proxy)

## Status

Accepted — 2026-07-24

## Context

Milestone 4 gives the Next.js frontend a real reason to call the backend: an initial
server-rendered fetch (safe — server-to-server HTTP calls aren't subject to CORS at
all) and, per the milestone's TanStack Query requirement, client-side interactivity
that re-fetches from the browser as filters, sort, and pagination change. The browser
runs on `http://localhost:3000`, the backend on `http://localhost:8080` — different
origins, so the browser-side calls need either the backend to grant CORS permission,
or a same-origin proxy in front of it. Both are legitimate; the milestone brief left
the choice open but required picking one coherent approach and avoiding a wildcard
(`*`) policy.

## Decision

**Minimal backend CORS configuration**, via a plain `WebMvcConfigurer` bean
(`com.hfxconnect.common.config.WebCorsConfig`) — not Spring Security (the project has
no Spring Security dependency yet; that's Milestone 5). It maps only `/api/v1/**`,
allows only `GET`/`POST` (the only methods any endpoint currently supports), and reads
its allowed origins from `app.cors.allowed-origins` (backed by the
`CORS_ALLOWED_ORIGINS` environment variable, default `http://localhost:3000` — the
frontend's own local dev default). `allowCredentials` is `false`: no request in this
milestone carries a cookie or session, and there is no reason to widen the policy
beyond what's actually used.

## Alternatives Considered

- **A Next.js proxy layer** (rewrites, or route handlers under `app/api/`, forwarding
  browser requests to the real backend so the browser only ever sees same-origin
  requests). Rejected for this milestone: it would duplicate the same base-URL/error
  handling the typed API client already centralizes, add a second hop with its own
  timeout/error-mapping behavior to get right and test, and doesn't match the
  project's already-documented MVP deployment target (Vercel frontend + Render
  backend as two genuinely separate origins — see `docs/architecture/system-overview.md`'s
  Deployment Path) where a proxy would need to exist in production too, not just
  local dev. A small, explicit CORS allowlist is less code, and is the same shape the
  app needs in production regardless.
- **`allowedOrigins("*")`.** Rejected outright per the milestone brief and this
  project's own security posture — a wildcard would let any website's browser-side
  JavaScript call the (currently unauthenticated) `POST` endpoints too, which is a
  materially worse version of the already-documented "POST is temporarily
  unsecured" limitation, not a neutral convenience.
- **Introducing Spring Security now just to get its CORS integration.** Rejected:
  Spring MVC's own `WebMvcConfigurer#addCorsMappings` does the same job with zero new
  dependencies, and adding a security filter chain before Milestone 5 actually needs
  one would be scope creep this milestone doesn't need.

## Consequences

- Every future environment (a real production frontend origin, a staging origin) is a
  one-line `CORS_ALLOWED_ORIGINS` change, not a code change — see `backend/.env.example`.
- If Milestone 5 introduces cookie/session-based authentication, `allowCredentials`
  and the allowed-headers list will need revisiting at that point; this ADR's decision
  is scoped to the current, unauthenticated `GET`/`POST` surface only.
- Verified with `CorsConfigurationIntegrationTest`: a real preflight request from the
  configured origin succeeds, and one from an arbitrary unconfigured origin receives
  no `Access-Control-Allow-Origin` header — proving this is a genuine allowlist, not
  effectively open.
