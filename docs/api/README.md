# API Documentation

The authoritative, always-current API contract is the generated OpenAPI document,
available whenever the backend is running:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Interactive Swagger UI: `http://localhost:8080/swagger-ui.html`

This file summarizes what exists; the running OpenAPI document is the source of
truth if the two ever disagree.

Browser-based clients (the Milestone 4 frontend) call this API cross-origin;
see `backend/README.md#cors` and
[ADR-006](../decisions/ADR-006-frontend-backend-connectivity.md) for the CORS
allowlist that makes that possible.

## Conventions

- All endpoints are versioned under `/api/v1/`.
- List endpoints are paginated (see below) rather than returning unbounded results.
- Errors use one consistent shape across every endpoint:

  ```json
  {
    "timestamp": "2026-07-15T12:00:00Z",
    "status": 400,
    "code": "VALIDATION_ERROR",
    "message": "The submitted request contains invalid information.",
    "fieldErrors": { "name": "Category name is required." }
  }
  ```

  `fieldErrors` is present only for validation-style failures; other errors omit it.
  Responses never include stack traces, SQL, table names, or internal class names —
  see `com.hfxconnect.common.error.GlobalExceptionHandler`.

- Stable error codes in use so far: `VALIDATION_ERROR`, `MALFORMED_REQUEST`,
  `NOT_FOUND` (a genuinely unmapped route — e.g. no path exists at all, as opposed to
  a specific record not being found), `CATEGORY_NOT_FOUND`, `CATEGORY_CONFLICT`,
  `RESOURCE_NOT_FOUND`, `RESOURCE_CONFLICT`, `INACTIVE_CATEGORY` (a resource
  referenced a real category that exists but is inactive — distinct from
  `CATEGORY_NOT_FOUND`, where the referenced category doesn't exist at all),
  `USER_CONFLICT` (duplicate email at registration), `AUTHENTICATION_FAILED`
  (login), `AUTHENTICATION_REQUIRED` (missing/invalid Bearer token, a
  non-`ACTIVE` account on an authenticated request, or a refresh with no
  cookie presented), `ACCESS_DENIED` (authenticated, but the account's role
  doesn't permit the action — Milestone 5C), `INVALID_REFRESH_TOKEN`,
  `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED`, `ACCOUNT_UNAVAILABLE`
  (valid credentials/refresh token, non-`ACTIVE` account), `INVALID_PAGINATION`,
  `INVALID_SORT`, `INVALID_SEARCH_QUERY` (an over-length `q` — Milestone 6A),
  `INTERNAL_ERROR`.

## Authentication and Authorization (Milestone 5C)

Most of the API is public. Protected routes require a Bearer access token
(from `POST /api/v1/auth/login` or `/refresh`): `Authorization: Bearer
<accessToken>`. Full design:
[ADR-009](../decisions/ADR-009-request-authentication-and-role-authorization.md)
and [docs/architecture/security-architecture.md](../architecture/security-architecture.md).

| Route | Requirement |
|---|---|
| `POST /api/v1/auth/{register,login,refresh,logout}` | Public |
| `GET /api/v1/categories`, `/api/v1/categories/**` | Public |
| `GET /api/v1/resources`, `/api/v1/resources/**` | Public |
| `GET /actuator/health` | Public |
| `GET /api/v1/users/me` | Any authenticated, `ACTIVE` account |
| `POST /api/v1/categories` | `ADMIN` only |
| `POST /api/v1/resources` | `ADMIN` or `MODERATOR` (not `ORGANIZATION` yet — see ADR-009) |
| Everything else | Authenticated (fail closed) |

Missing/invalid tokens and non-`ACTIVE` accounts return `401
AUTHENTICATION_REQUIRED`. An authenticated caller whose role doesn't permit
the action returns `403 ACCESS_DENIED` with a generic message — never the
specific role or expression required.

## Pagination

List endpoints accept `page` (0-based, default `0`) and `size` (default `20`, maximum
`100`) query parameters and return an explicit page shape rather than Spring's raw
`Page` serialization:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 6,
  "totalPages": 1
}
```

Out-of-range values (negative page, size outside `1`-`100`) return `400
INVALID_PAGINATION` rather than being silently clamped. Sorting is fixed for
categories, and allowlisted (not arbitrary) for resources — see each endpoint's own
conventions below. Arbitrary caller-specified sort fields (any entity column) are not
supported anywhere in the API; an unrecognized `sort` value returns `400
INVALID_SORT`.

## Categories — `/api/v1/categories`

Full detail: `docs/milestones/milestone-03a-category-domain.md`. Summary:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/categories` | Create a category. Slug is always derived from the name (see ADR-005) — it cannot be supplied. |
| `GET` | `/api/v1/categories/{id}` | Get a category by numeric ID. |
| `GET` | `/api/v1/categories/slug/{slug}` | Get a category by slug. |
| `GET` | `/api/v1/categories` | Paginated list, sorted by name ascending. Optional `active` (`true`/`false`) query filter. |

**`POST /api/v1/categories` requires a Bearer access token for an `ADMIN`
account** (Milestone 5C — see "Authentication and Authorization" above).

### Status codes

| Status | Meaning |
|---|---|
| `201` | Category created; `Location` header points to `GET /api/v1/categories/{id}` |
| `200` | Successful retrieval or listing |
| `400` | Validation failure, malformed JSON, or invalid pagination parameters |
| `401` | `POST` only — missing or invalid access token |
| `403` | `POST` only — authenticated, but not an `ADMIN` account |
| `404` | No category exists with the given ID or slug |
| `409` | A category with that name or slug already exists |
| `500` | Unexpected server error (no internal detail is exposed) |

## Resources — `/api/v1/resources`

Full detail: `docs/milestones/milestone-03c-public-resource-api.md`. Summary:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/resources` | Create a resource under an existing, active category. Slug is derived from the name — it cannot be supplied. New resources always start `UNVERIFIED`. |
| `GET` | `/api/v1/resources/{id}` | Get an active resource by UUID. `404` for a deactivated resource, same as an unknown ID. |
| `GET` | `/api/v1/resources/slug/{slug}` | Get an active resource by slug. Same `404` behavior. |
| `GET` | `/api/v1/resources` | Paginated list of **active resources only** — there is no way to include inactive resources publicly yet. Optional `categoryId` filter. Optional `q` keyword search (Milestone 6A — see below). Optional `sort`: `name` (default, ascending) or `createdAt` (newest first); anything else returns `400 INVALID_SORT`. |

Responses embed a small `CategorySummaryResponse` (`id`, `name`, `slug`) rather than
the full category representation. List results use a leaner
`ResourceSummaryResponse` (omits full description, contact details, and eligibility —
see `GET /api/v1/resources/{id}` for those).

### Keyword Search (`q`)

Full design: [ADR-010](../decisions/ADR-010-keyword-search-design.md).

`GET /api/v1/resources?q=library` performs a case-insensitive substring
match across `name`, `description`, `addressLine1`, and `city` — a resource
matches if **any** of those fields contains the (normalized) query text.
Combines freely with `categoryId`, `sort`, and pagination.

- **Normalization:** leading/trailing whitespace trimmed, repeated internal
  whitespace collapsed to a single space, non-whitespace control characters
  stripped. A blank or whitespace-only `q` is treated identically to `q`
  being absent — no keyword filter, not an error.
- **Maximum length:** 100 characters after normalization. Longer returns
  `400 INVALID_SEARCH_QUERY`.
- **Wildcards:** PostgreSQL's `LIKE` metacharacters (`%`, `_`) are escaped
  and matched **literally** — searching `50%` or `user_name` matches those
  exact characters, not "any characters."
- **No relevance ranking.** Results are returned in the same `sort` order
  requested (`name` ascending by default, or `createdAt` descending) — a
  keyword match does not reorder results, and there is no relevance score.
- **Not searched:** province, postal code, category name, phone, email,
  website URL — see ADR-010 for why each is excluded.

```bash
curl "http://localhost:8080/api/v1/resources?q=library&categoryId=1&sort=name"
```

**`POST /api/v1/resources` requires a Bearer access token for an `ADMIN` or
`MODERATOR` account** (Milestone 5C). `ORGANIZATION` accounts cannot create
resources yet — see ADR-009.

**No update or delete endpoint yet.** `ResourceService.update`/`deactivate` exist and
are fully tested (Milestone 3B), but are not exposed over HTTP in this milestone —
see `docs/milestones/milestone-03c-public-resource-api.md`.

### Status codes

| Status | Meaning |
|---|---|
| `201` | Resource created; `Location` header points to `GET /api/v1/resources/{id}` |
| `200` | Successful retrieval or listing |
| `400` | Validation failure, malformed JSON, invalid pagination/sort, an over-length `q` (`INVALID_SEARCH_QUERY`), or an inactive category (`INACTIVE_CATEGORY`) |
| `401` | `POST` only — missing or invalid access token |
| `403` | `POST` only — authenticated, but not an `ADMIN` or `MODERATOR` account |
| `404` | No active resource exists with the given ID/slug, or the referenced category doesn't exist (`CATEGORY_NOT_FOUND`) |
| `409` | A resource with that (derived) slug already exists |
| `500` | Unexpected server error (no internal detail is exposed) |

## Auth — `/api/v1/auth`

Full detail: `docs/milestones/milestone-05a-user-registration.md` and
`docs/milestones/milestone-05b-authentication-sessions.md`. Summary:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register an account. Always creates a `USER`-role, `ACTIVE`, unverified account — any `role` or other privilege field submitted in the request body is silently ignored, never honored. Does not log the caller in. |
| `POST` | `/api/v1/auth/login` | Verify email/password, return an access token, and set a rotating refresh token as an `HttpOnly` cookie. |
| `POST` | `/api/v1/auth/refresh` | Rotate the refresh session (read from the cookie only) and return a new access token and cookie. |
| `POST` | `/api/v1/auth/logout` | Revoke the session matching the presented cookie, if any, and clear it. Always safe and idempotent. |

Registration email is normalized (trimmed, lowercased) before the uniqueness check,
so `User@Example.org` and `user@example.org` cannot both register. Passwords are
hashed with BCrypt (strength 12) before storage — the response never includes a
password or its hash. Password policy: at least 8 characters, and **at most 72
bytes when encoded as UTF-8** (not 72 characters — BCrypt's limit is a byte limit,
and a multibyte-Unicode password can exceed it well under 72 characters; see
[ADR-007](../decisions/ADR-007-user-identity-and-password-hashing.md)'s 2026-07-24
correction). A small set of the most common leaked passwords is also rejected.

**Login/refresh never return a refresh token in JSON.** It is only ever set as an
`HttpOnly`, path-scoped (`/api/v1/auth`) `hfx_refresh_token` cookie — see
[ADR-008](../decisions/ADR-008-authentication-session-architecture.md). The
response body is:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "...", "email": "...", "role": "USER", "status": "ACTIVE", "emailVerified": false }
}
```

Unknown email and wrong password are always the exact same `401
AUTHENTICATION_FAILED` response — the API never reveals whether an email is
registered. A correct-credentials account that is not `ACTIVE` returns `403
ACCOUNT_UNAVAILABLE` instead (a real state, distinct from "wrong password"), without
saying why. Refresh tokens rotate on every successful refresh; presenting an
already-used token revokes every session descended from the same login, not just
that one.

**None of the four endpoints above require an access token** — they are how a
caller obtains one in the first place. **No rate limiting exists** — login
accepts unlimited attempts; see ADR-008's honest limitations section.

### Status codes

| Status | Meaning |
|---|---|
| `201` | Account created (`register`); response body is the safe account representation (no password/hash) |
| `200` | Login or refresh succeeded |
| `204` | Logout — always, regardless of whether a valid session was presented |
| `400` | Validation failure (missing/malformed email, missing/weak/common password) or malformed JSON |
| `401` | `register`: n/a. `login`: invalid credentials (`AUTHENTICATION_FAILED`). `refresh`: missing (`AUTHENTICATION_REQUIRED`), invalid (`INVALID_REFRESH_TOKEN`), expired (`REFRESH_TOKEN_EXPIRED`), or reused (`REFRESH_TOKEN_REUSED`) refresh token |
| `403` | Credentials or refresh token were valid, but the account is not `ACTIVE` (`ACCOUNT_UNAVAILABLE`) |
| `409` | An account with that (case-insensitively normalized) email already exists (`register`) |
| `500` | Unexpected server error (no internal detail is exposed) |

## Users — `/api/v1/users`

Full detail: `docs/milestones/milestone-05c-role-authorization.md`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/users/me` | The current authenticated account. Requires a Bearer access token. |

Returns the same safe shape as registration's response — `{id, email, role,
status, emailVerified, createdAt}` — for the authenticated caller only. There
is no user-ID parameter and no way to look up a different account; that would
be a role-management/admin-lookup capability this project doesn't have.

### Status codes

| Status | Meaning |
|---|---|
| `200` | Current account returned |
| `401` | Missing or invalid access token, or the account is no longer `ACTIVE` (`AUTHENTICATION_REQUIRED`) |
