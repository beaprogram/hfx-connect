# Task 014: Wireframes, Backend CORS, and Frontend Foundation

## Objective

Plan the public frontend's content hierarchy and states before writing any
implementation code, and put in place the two things everything else in
Milestone 4 depends on: a way for the browser to reach the backend (CORS),
and a typed, validated way to call it (the API client foundation).

## Context

Part of Milestone 4 (Public Frontend) — see
`docs/milestones/milestone-04-public-frontend.md`.

## Scope

- `docs/wireframes/`: homepage, resource-list, resource-detail, mobile
  navigation, and shared loading/empty/error/not-found states — low-fidelity,
  text-based, written before implementation per
  `docs/development-workflow.md`'s process.
- `com.hfxconnect.common.config.WebCorsConfig`: a plain `WebMvcConfigurer`
  CORS mapping for `/api/v1/**`, allowed origins from
  `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`) — see
  [ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md) for why
  this was chosen over a Next.js proxy layer.
- `lib/api/` (`client.ts`, `errors.ts`, `categories.ts`, `resources.ts`),
  `lib/validation/schemas.ts` (Zod, checked against the live `/v3/api-docs`
  before writing), `lib/formatting/`, `lib/constants/resources.ts`,
  `lib/query/` (query client, provider, keys, URL-param parsing).
- `frontend/.env.example` (`NEXT_PUBLIC_API_BASE_URL`); `backend/.env.example`
  gained `CORS_ALLOWED_ORIGINS`.

## Out of Scope

Any actual page/route implementation — this task is the plumbing everything
else builds on, not user-visible UI.

## Acceptance Criteria

- CORS allows the configured frontend origin and rejects an arbitrary one —
  verified by a real preflight request, not just reading the config.
- `getJson` centralizes query encoding, error mapping, and Zod validation;
  no other file makes a raw `fetch` call against the backend.
- Zod schemas contain no field the backend doesn't actually return (checked
  against the live OpenAPI document, not assumed).

## Technical Approach

Read the running backend's `/v3/api-docs` directly (via `curl` and `python3
-m json.tool`) before writing a single Zod schema, rather than trusting the
milestone brief's description of the API shape — this is what caught that
the brief's requested "accessibility information" field doesn't exist on
`ResourceResponse` at all (see the milestone doc's "A Note on Scope").

CORS: no Spring Security dependency exists in this project yet (Milestone 5),
so `WebMvcConfigurer#addCorsMappings` does the job without adding one.

## Testing Requirements

`CorsConfigurationIntegrationTest` (2 tests: allowed origin succeeds,
unconfigured origin is rejected) as part of the full backend suite
(`./mvnw clean verify`).

## Result

Completed. CORS verified both by automated test and a real preflight/`GET`
request with an `Origin` header against the running backend. The API client
and validation layer were exercised end-to-end (server-side prefetch
rendering real data into SSR HTML) before any later task's UI code was
written.

## Related Commits

`docs: add public frontend wireframes and ADR for backend connectivity`
`feat: add backend CORS support for the frontend origin`
`feat: add typed API client, Zod validation, and TanStack Query foundation`
