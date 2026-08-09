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
  `RESOURCE_SUBMISSION_NOT_FOUND`, `RESOURCE_SUBMISSION_CONFLICT`,
  `CORRECTION_REPORT_NOT_FOUND`, `CORRECTION_REPORT_CONFLICT`,
  `INVALID_CONTRIBUTION_STATUS` (withdrawing an already-resolved
  submission or report — Milestone 8B), `INTERNAL_ERROR`.
  `VALIDATION_ERROR` also covers an invalid saved-resources batch status
  request (a null entry or more than 100 distinct ids — Milestone 8A),
  same as any other field-level validation failure.

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
| `PUT`/`DELETE /api/v1/users/me/saved-resources/{resourceId}`, `GET /api/v1/users/me/saved-resources`, `POST /api/v1/users/me/saved-resources/status` | Any authenticated, `ACTIVE` account (`USER`/`ORGANIZATION`/`MODERATOR`/`ADMIN` — no role restriction beyond "authenticated") |
| `POST`/`GET /api/v1/users/me/resource-submissions`, `GET`/`POST .../{id}/withdraw`, `POST /api/v1/resources/{resourceId}/correction-reports`, `GET /api/v1/users/me/correction-reports`, `GET`/`POST .../{id}/withdraw` | Any authenticated, `ACTIVE` account — no role restriction (Milestone 8B) |
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
| `GET` | `/api/v1/resources/{id}` | Get an active resource by UUID. `404` for a deactivated resource, same as an unknown ID. Includes the resource's weekly schedule and current open status (Milestone 6B — see below). |
| `GET` | `/api/v1/resources/slug/{slug}` | Get an active resource by slug. Same `404` behavior. |
| `GET` | `/api/v1/resources` | Paginated list of **active resources only** — there is no way to include inactive resources publicly yet. Optional `categoryId` filter. Optional `q` keyword search (Milestone 6A — see below). Optional `costType`, `verificationStatus`, `openNow` filters (Milestone 6B — see below). Optional `sort`: `name` (default, ascending) or `createdAt` (newest first); anything else returns `400 INVALID_SORT`. |
| `PUT` | `/api/v1/resources/{id}/operating-hours` | Fully replace a resource's weekly schedule (Milestone 6B — see below). `ADMIN` or `MODERATOR` only. |
| `PUT` | `/api/v1/resources/{id}/location` | Replace a resource's geographic coordinate (Milestone 7A — see below). `ADMIN` or `MODERATOR` only. |
| `GET` | `/api/v1/resources/nearby` | Find active resources near a coordinate, ordered by distance (Milestone 7A — see below). Public. |

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

### Operating Hours and Filtering (Milestone 6B)

Full design: [ADR-011](../decisions/ADR-011-operating-hours-and-open-now.md).

**Weekly schedule.** Every resource read (`GET /api/v1/resources/{id}`,
`/slug/{slug}`) includes an `hours` object: `timezone` (always
`"America/Halifax"`), `weeklyHours` (an array of `{dayOfWeek, closed,
opensAt, closesAt, overnight}` entries — `MONDAY`-`SUNDAY`, at most one
entry per day, missing days have no schedule), `hoursStatus`
(`OPEN`/`CLOSED`/`UNKNOWN`), and `openNow` (`true`/`false`/`null`).
`opensAt`/`closesAt` are ISO local-time strings with seconds (e.g.
`"09:00:00"`), never a UTC instant — they're plain wall-clock values
already resolved to Halifax local time. An interval where `opensAt` is
later than `closesAt` (`overnight: true`) crosses midnight, continuing
into the next calendar day. List results (`ResourceSummaryResponse`)
include the compact `hoursStatus`/`openNow` pair only, not the full
schedule.

`hoursStatus` is `UNKNOWN` (with `openNow: null`) only when a resource has
**no schedule at all**. A resource with any schedule data always resolves
to `OPEN` or `CLOSED`, even for a day with no entry — `openNow=true`
therefore has one unambiguous meaning ("currently calculated as OPEN") and
never returns a resource whose hours are unknown.

**Filters**, all independently optional and combinable with `q`/`categoryId`/
`sort`/pagination:

- `costType` — exact match against `CostType` (`FREE`, `LOW_COST`, `PAID`,
  `UNKNOWN`), case-insensitive. Invalid value returns `400
  INVALID_COST_TYPE`.
- `verificationStatus` — exact match against `VerificationStatus`
  (`UNVERIFIED`, `VERIFIED`), case-insensitive. Invalid value returns `400
  INVALID_VERIFICATION_STATUS`.
- `openNow` — `true` narrows to currently-open resources (evaluated in
  America/Halifax); missing, blank, or `false` apply no filter. Any other
  value returns `400 INVALID_OPEN_NOW_FILTER`. Resources with `UNKNOWN`
  hours are never returned when `openNow=true`.

```bash
curl "http://localhost:8080/api/v1/resources?costType=FREE&verificationStatus=VERIFIED&openNow=true"
```

**`PUT /api/v1/resources/{id}/operating-hours`** fully replaces a
resource's weekly schedule (delete-then-insert, one transaction — no
partial update). Body: `{"hours": [{"dayOfWeek": "MONDAY", "closed": false,
"opensAt": "09:00", "closesAt": "17:00"}, ...]}`, at most one entry per
day, at most 7 entries, a closed day must omit `opensAt`/`closesAt`, an
open day must supply both and they must differ. Requires a Bearer access
token for an `ADMIN` or `MODERATOR` account (the same pairing as
`POST /api/v1/resources`). Returns the updated schedule shape (`200`).
Invalid schedules return `400 VALIDATION_ERROR` with field-level detail.
There is no public endpoint for editing hours.

```bash
curl -X PUT "http://localhost:8080/api/v1/resources/$ID/operating-hours" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"hours":[{"dayOfWeek":"MONDAY","closed":false,"opensAt":"09:00","closesAt":"17:00"}]}'
```

**`POST /api/v1/resources` requires a Bearer access token for an `ADMIN` or
`MODERATOR` account** (Milestone 5C). `ORGANIZATION` accounts cannot create
resources yet — see ADR-009.

**No update or delete endpoint yet for the resource itself.**
`ResourceService.update`/`deactivate` exist and are fully tested
(Milestone 3B), but are not exposed over HTTP in this milestone — see
`docs/milestones/milestone-03c-public-resource-api.md`. The one write
endpoint this domain does expose over HTTP is the operating-hours
replacement above.

### Resource Locations and Nearby Search (Milestone 7A)

Full design: [ADR-012](../decisions/ADR-012-postgis-nearby-search-design.md).

**`PUT /api/v1/resources/{id}/location`** replaces a resource's geographic
coordinate transactionally. Body: `{"latitude": 44.6488, "longitude":
-63.5752}` — both required; `latitude` between `-90` and `90`, `longitude`
between `-180` and `180`. Requires a Bearer access token for an `ADMIN` or
`MODERATOR` account (the same pairing as `POST /api/v1/resources` and the
operating-hours endpoint). Returns the saved coordinate (`200`). Invalid
coordinates return `400 VALIDATION_ERROR` with field-level detail. There
is no public endpoint for editing a resource's location.

```bash
curl -X PUT "http://localhost:8080/api/v1/resources/$ID/location" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"latitude":44.6488,"longitude":-63.5752}'
```

**`GET /api/v1/resources/nearby`** finds active resources within a radius
of a coordinate, ordered nearest first. `latitude`/`longitude` are
required; both missing or out-of-range values return `400
INVALID_LATITUDE`/`INVALID_LONGITUDE`. `radiusKm` is optional (default
`5`, maximum `50`; anything outside `(0, 50]` returns `400
INVALID_RADIUS`). Combines freely with `q`/`categoryId`/`costType`/
`verificationStatus`/`openNow`/`page`/`size` — identical semantics to
`GET /api/v1/resources`. Resources with no saved coordinate are always
excluded. Each result includes `latitude`, `longitude`, and
`distanceMeters` (straight-line geographic distance from the search
origin, in metres — **never** route distance, walking time, or driving
time). No matches returns `200` with empty content, never `404`.

```bash
curl "http://localhost:8080/api/v1/resources/nearby?latitude=44.6488&longitude=-63.5752&radiusKm=10&costType=FREE"
```

**Privacy note.** `latitude`/`longitude` on `GET /nearby` are used only
for that one request and never persisted, but — being `GET` query
parameters, deliberately so the endpoint stays shareable and works
without JavaScript — they can appear in browser history and server access
logs, same as any other URL query parameter.

### Status codes

| Status | Meaning |
|---|---|
| `201` | Resource created; `Location` header points to `GET /api/v1/resources/{id}` |
| `200` | Successful retrieval, listing, or operating-hours/location replacement |
| `400` | Validation failure, malformed JSON, invalid pagination/sort, an over-length `q` (`INVALID_SEARCH_QUERY`), an invalid `costType`/`verificationStatus`/`openNow` filter, an invalid operating-hours schedule (`VALIDATION_ERROR`), an invalid `latitude`/`longitude`/`radiusKm` on `/nearby` (`INVALID_LATITUDE`/`INVALID_LONGITUDE`/`INVALID_RADIUS`), an invalid location body (`VALIDATION_ERROR`), or an inactive category (`INACTIVE_CATEGORY`) |
| `401` | `POST`/`PUT` only — missing or invalid access token |
| `403` | `POST`/`PUT` only — authenticated, but not an `ADMIN` or `MODERATOR` account |
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

## Saved Resources — `/api/v1/users/me/saved-resources` (Milestone 8A)

Full design: [ADR-014](../decisions/ADR-014-saved-resources-design.md).
The current authenticated account's own private, saved-resource list —
every endpoint requires a Bearer access token for any authenticated,
`ACTIVE`-status role and is scoped to the caller's own account only.
The user id is always taken from the authenticated principal, never
from a path/query/body parameter.

| Method | Path | Purpose |
|---|---|---|
| `PUT` | `/api/v1/users/me/saved-resources/{resourceId}` | Idempotently save an active resource for the current user. |
| `DELETE` | `/api/v1/users/me/saved-resources/{resourceId}` | Idempotently remove a saved resource for the current user. |
| `GET` | `/api/v1/users/me/saved-resources` | Paginated list of the current user's saved, currently-active resources. |
| `POST` | `/api/v1/users/me/saved-resources/status` | Batch lookup: given a list of resource ids, returns which are saved for the current user — intended for one request per page of resource cards, not one per card. |

**`PUT /{resourceId}`** — `204` whether this call created the saved
relation or it already existed (true idempotency, not "succeeds once,
then conflicts"). `404` if no **active** resource exists with the given
id — the same "missing and inactive look identical" posture used
elsewhere in this API. A genuine concurrent double-save (two
near-simultaneous requests for the same user/resource pair) is resolved
by the database's own uniqueness constraint to a single saved relation
and two `204` responses — never a `500` or `409`.

**`DELETE /{resourceId}`** — `204` whether this call removed a row or
none existed. Works regardless of the resource's current active state —
a resource that was saved and has since been deactivated can always
still be removed by its id.

**`GET`** — accepts `page` (0-based, default `0`), `size` (default `20`,
maximum `100`), and `sort` (`savedAt`, default, newest first; or `name`,
resource name ascending). Returns the standard page shape (see
"Pagination" above). **Excludes saved resources that have since gone
inactive** — the underlying relation is preserved (not deleted), and the
resource reappears in this list automatically if it becomes active
again; see ADR-014's "Deactivation vs. Deletion."

```json
{
  "content": [
    {
      "savedAt": "2026-08-01T00:00:00Z",
      "resource": {
        "id": "...", "name": "...", "slug": "...", "city": "...", "province": "NS",
        "costType": "FREE", "verificationStatus": "VERIFIED", "active": true,
        "category": { "id": 1, "name": "...", "slug": "..." },
        "createdAt": "...", "hoursStatus": "OPEN", "openNow": true
      }
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

**`POST /status`** — body `{"resourceIds": ["...", "..."]}`, at most 100
distinct ids (duplicates normalized, order-independent); a null entry
or more than 100 distinct ids returns `400 VALIDATION_ERROR`. Response:
`{"savedResourceIds": ["..."]}` — the subset of the requested ids the
current user has saved.

```bash
curl -X PUT "http://localhost:8080/api/v1/users/me/saved-resources/$RESOURCE_ID" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:8080/api/v1/users/me/saved-resources?sort=name" \
  -H "Authorization: Bearer $TOKEN"

curl -X POST "http://localhost:8080/api/v1/users/me/saved-resources/status" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"resourceIds":["'"$RESOURCE_ID"'"]}'
```

### Status codes

| Status | Meaning |
|---|---|
| `200` | `GET`/`POST status` succeeded |
| `204` | `PUT`/`DELETE` succeeded (always — idempotent) |
| `400` | Invalid page/size (`INVALID_PAGINATION`), invalid `sort` (`INVALID_SORT`), or an invalid batch status request (`VALIDATION_ERROR`) |
| `401` | Missing or invalid access token, or the account is no longer `ACTIVE` |
| `404` | `PUT` only — no active resource exists with the given id |

## Resource Submissions — `/api/v1/users/me/resource-submissions` (Milestone 8B)

Full design: [ADR-015](../decisions/ADR-015-community-contribution-workflows-design.md).
Proposes a new community resource that isn't listed yet. Every endpoint
requires a Bearer access token for any authenticated, `ACTIVE`-status
role and is scoped to the caller's own account only. **Creating a
submission never creates a public resource** — it always starts
`PENDING_REVIEW`, visible only to its owner, until Milestone 9's
moderation workflow reviews it.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/users/me/resource-submissions` | Create a `PENDING_REVIEW` submission. |
| `GET` | `/api/v1/users/me/resource-submissions` | Paginated list of the current user's own submissions. |
| `GET` | `/api/v1/users/me/resource-submissions/{submissionId}` | One owned submission. |
| `POST` | `/api/v1/users/me/resource-submissions/{submissionId}/withdraw` | Withdraws a still-`PENDING_REVIEW` submission. |

**`POST`** body:

```json
{
  "categoryId": 1,
  "name": "Halifax Food Bank",
  "shortDescription": "Free groceries for anyone in need.",
  "fullDescription": null,
  "addressLine1": "123 Main St",
  "addressLine2": null,
  "city": "Halifax",
  "province": "NS",
  "postalCode": "B3H 4R2",
  "phone": null,
  "email": null,
  "websiteUrl": null,
  "costType": "FREE",
  "eligibilityInformation": null,
  "accessibilityInformation": null
}
```

There is no `submittedByUserId` or `status` field to supply — both are
server-assigned. `categoryId` must reference an existing, active
category (`404 CATEGORY_NOT_FOUND` / `400 INACTIVE_CATEGORY`
otherwise). Field validation reuses the same province/postal-code/
phone/email/website-scheme rules real resource creation uses
(`docs/architecture/backend-architecture.md`'s "Focused Entities Over
Generic Frameworks" section).

**Milestone 9A**: once a moderator has decided, the response also
includes `reviewedAt`, `reviewReason`, and — only for an `APPROVED`
submission — `resultingResource` (`{id, name, slug}`, linking to the
now-public resource). The reviewing moderator's identity is never
included in this response; see "Moderation" below.

**Duplicate-pending policy**: at most one `PENDING_REVIEW` submission
per (account, category, normalized name) at a time — a repeat while one
is already pending returns `409 RESOURCE_SUBMISSION_CONFLICT`. A
withdrawn, approved, or rejected submission never blocks a later
resubmission of the same name/category.

**`GET`** accepts `page`/`size`/`sort` (`submittedAt`, default, newest
first; `updatedAt`; or `status`), same pagination conventions as every
other list in this API. **`GET .../{submissionId}`** and **`POST
.../{submissionId}/withdraw`** both return `404
RESOURCE_SUBMISSION_NOT_FOUND` for an id that doesn't exist *or*
belongs to a different account — the two cases are indistinguishable to
the caller.

```bash
curl -X POST "http://localhost:8080/api/v1/users/me/resource-submissions" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"categoryId":1,"name":"Halifax Food Bank","shortDescription":"Free groceries.","addressLine1":"123 Main St","city":"Halifax","province":"NS","postalCode":"B3H 4R2","costType":"FREE"}'
```

### Status codes

| Status | Meaning |
|---|---|
| `200` | `GET`/`POST .../withdraw` succeeded |
| `201` | Submission created |
| `400` | Validation failure, an inactive category (`INACTIVE_CATEGORY`), invalid pagination/sort, or withdrawing a non-pending submission (`INVALID_CONTRIBUTION_STATUS`) |
| `401` | Missing or invalid access token |
| `404` | No category exists with the given id, or no submission with the given id is owned by the current user |
| `409` | The current user already has a pending submission for this category and name |

## Correction Reports — `/api/v1/resources/{resourceId}/correction-reports`, `/api/v1/users/me/correction-reports` (Milestone 8B)

Full design: [ADR-015](../decisions/ADR-015-community-contribution-workflows-design.md).
Reports an issue on an existing, active resource. Creation is nested
under the target resource's id (a path segment, not request-body data);
listing and detail are scoped to the caller's own account under
`/api/v1/users/me/correction-reports`. **Creating a report never
modifies the target resource** — it always starts `PENDING_REVIEW`,
visible only to its owner.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/resources/{resourceId}/correction-reports` | Create a `PENDING_REVIEW` report against an active resource. |
| `GET` | `/api/v1/users/me/correction-reports` | Paginated list of the current user's own reports. |
| `GET` | `/api/v1/users/me/correction-reports/{reportId}` | One owned report. |
| `POST` | `/api/v1/users/me/correction-reports/{reportId}/withdraw` | Withdraws a still-`PENDING_REVIEW` report. |

**`POST`** body — every `proposed*` field is optional; an
explanation-only report (e.g. `issueType: "RESOURCE_CLOSED"` or
`"OTHER"`) is fully valid:

```json
{
  "issueType": "ADDRESS",
  "explanation": "The address listed is out of date.",
  "proposedName": null,
  "proposedDescription": null,
  "proposedAddressLine1": "456 New St",
  "proposedAddressLine2": null,
  "proposedCity": null,
  "proposedProvince": null,
  "proposedPostalCode": null,
  "proposedPhone": null,
  "proposedEmail": null,
  "proposedWebsiteUrl": null,
  "proposedCostType": null,
  "proposedCostDetails": null,
  "proposedEligibility": null
}
```

`issueType` is one of `GENERAL_INFORMATION`, `ADDRESS`,
`CONTACT_INFORMATION`, `OPERATING_HOURS`, `ELIGIBILITY`,
`ACCESSIBILITY`, `COST`, `RESOURCE_CLOSED`, `DUPLICATE_RESOURCE`,
`OTHER`. The target resource must currently be active — a missing or
inactive resource both return the identical `404 RESOURCE_NOT_FOUND`,
the same visibility rule used throughout this API.

**Duplicate-pending policy**: at most one `PENDING_REVIEW` report per
(account, resource, issue type) at a time — `409
CORRECTION_REPORT_CONFLICT` on a repeat. A different issue type on the
same resource, or a re-report after the first is resolved, is never
blocked.

**Resource-deletion behavior**: if the target resource is later
deleted, an existing report is preserved (not cascaded away) —
`resource.resourceId` becomes `null` in the response, but
`resource.name`/`resource.slug` remain populated from a snapshot
captured when the report was created.

**Milestone 9A**: once a moderator has decided, the response also
includes `reviewedAt`, `reviewReason`, and — only once `status` is
`APPROVED` — `changesApplied` (`true`/`false`; a moderator may approve
without applying any automatic change). The reviewing moderator's
identity is never included in this response; see "Moderation" below.

```bash
curl -X POST "http://localhost:8080/api/v1/resources/$RESOURCE_ID/correction-reports" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"issueType":"ADDRESS","explanation":"The address listed is out of date."}'
```

### Status codes

| Status | Meaning |
|---|---|
| `200` | `GET`/`POST .../withdraw` succeeded |
| `201` | Report created |
| `400` | Validation failure, invalid pagination/sort, or withdrawing a non-pending report (`INVALID_CONTRIBUTION_STATUS`) |
| `401` | Missing or invalid access token |
| `404` | No active resource exists with the given id, or no report with the given id is owned by the current user |
| `409` | The current user already has a pending report for this resource and issue type |

## Moderation — `/api/v1/moderation/**` (Milestone 9A)

Full design: [ADR-016](../decisions/ADR-016-moderation-workflow-design.md).
Reviewing pending resource submissions and correction reports.
**Every route below requires a Bearer access token for a current
`ADMIN` or `MODERATOR` account** — `401` with no token, `403` for any
other role, enforced by `SecurityConfig`'s `/api/v1/moderation/**`
matcher and backed by the account's *current* database role (never a
JWT claim alone — see ADR-009). Submitter/reporter identity is never
exposed on any route in this section; a moderator/admin can never
review their own contribution (`403 SELF_REVIEW_NOT_ALLOWED`, no
exception for `ADMIN`).

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/moderation/resource-submissions` | Queue. `status` (default `PENDING_REVIEW`), `categoryId`, `page`, `size`, `sort` (`submittedAt`, default, oldest first; `updatedAt`, newest first). |
| `GET` | `/api/v1/moderation/resource-submissions/{submissionId}` | Full detail, not owner-scoped. |
| `POST` | `/api/v1/moderation/resource-submissions/{submissionId}/approve` | `{"reason": "..."}` — publishes a `VERIFIED` public resource in the same transaction. |
| `POST` | `/api/v1/moderation/resource-submissions/{submissionId}/reject` | `{"reason": "..."}` — never creates a resource. |
| `GET` | `/api/v1/moderation/resource-submissions/{submissionId}/audit-events` | This submission's audit history, oldest first. |
| `GET` | `/api/v1/moderation/correction-reports` | Queue. `status`, `issueType`, `page`, `size`, `sort`. |
| `GET` | `/api/v1/moderation/correction-reports/{reportId}` | Full detail, including the target resource's current live values. |
| `POST` | `/api/v1/moderation/correction-reports/{reportId}/approve` | `{"reason": "...", "applyProposedChanges": true, "deactivateResource": false}`. |
| `POST` | `/api/v1/moderation/correction-reports/{reportId}/reject` | `{"reason": "..."}` — never modifies the target resource. |
| `GET` | `/api/v1/moderation/correction-reports/{reportId}/audit-events` | This report's audit history, oldest first. |
| `GET` | `/api/v1/moderation/audit-events` | Global audit list. `contributionType`, `decision` filters, both optional. Newest first. |

A review `reason` is required for every decision (approve or reject,
either domain) — trimmed, 5-1000 characters — and is always the same
text later shown to the contribution's owner; there is no separate
private moderator-note field.

**Resource-submission approval**: revalidates every proposed field
with the same rules real resource creation uses, generates the slug
the same way, and publishes the resource `VERIFIED` with a real
`lastVerifiedAt`. A slug/name collision with an existing resource
returns `409 RESOURCE_PUBLICATION_CONFLICT` and leaves the submission
`PENDING_REVIEW` — it is never marked `APPROVED` when publication
fails.

**Correction-report approval** — `applyProposedChanges` and
`deactivateResource` are independent; both may be `false` (a
legitimate "reviewed, no automatic public-data change" outcome):

```bash
curl -X POST "http://localhost:8080/api/v1/moderation/correction-reports/$REPORT_ID/approve" \
  -H "Authorization: Bearer $MODERATOR_TOKEN" -H "Content-Type: application/json" \
  -d '{"reason":"Confirmed the new address against the source.","applyProposedChanges":true,"deactivateResource":false}'
```

- `applyProposedChanges=true` applies only whichever of the report's 13
  supported scalar fields (name, description, address, city, province,
  postal code, phone, email, website, cost type, cost details,
  eligibility) are present — category is never touched (the schema has
  no `proposedCategory` field). Returns `400
  UNSUPPORTED_CORRECTION_APPLICATION` for `issueType: "OPERATING_HOURS"`
  or `"DUPLICATE_RESOURCE"` — neither has an automated field mapping;
  reject with a reason, or handle the change manually through the
  existing operating-hours endpoint.
- `deactivateResource=true` is only legal for `issueType:
  "RESOURCE_CLOSED"` — `400 INVALID_DEACTIVATION_REQUEST` otherwise.
- A merged-field result that would be invalid, or that collides with
  existing data, returns `409 CORRECTION_APPLICATION_CONFLICT` and
  leaves the report `PENDING_REVIEW`.

**Concurrency**: a second moderator's decision on an already-decided (or
concurrently-being-decided) contribution returns `409
CONTRIBUTION_ALREADY_REVIEWED` — the database's own row lock is
authoritative, not a pre-check (see ADR-016's "Concurrency Control").

**Audit history** — `beforeSnapshot`/`afterSnapshot` are small,
explicitly-built field maps (never a full serialized entity), present
only on the row(s) recording a concrete effect
(`RESOURCE_CREATED`/`RESOURCE_UPDATED`/`RESOURCE_DEACTIVATED`); a bare
`REVIEW_DECISION` row (every rejection, and a no-op approval) has
neither. `actorEmail` is a point-in-time snapshot, not a live account
lookup.

### Status codes

| Status | Meaning |
|---|---|
| `200` | Queue/detail/approve/reject/audit-events succeeded |
| `400` | Missing/invalid reason, an invalid deactivation request, an unsupported correction application, invalid pagination/sort, or the resulting resource/correction would be invalid |
| `401` | Missing or invalid access token |
| `403` | Current account is not `ADMIN`/`MODERATOR`, or a self-review attempt (`SELF_REVIEW_NOT_ALLOWED`) |
| `404` | No submission/report exists with the given id |
| `409` | Already reviewed/withdrawn (`CONTRIBUTION_ALREADY_REVIEWED`), a publication conflict (`RESOURCE_PUBLICATION_CONFLICT`), or a correction-application conflict (`CORRECTION_APPLICATION_CONFLICT`) |

## Organizations and Resource Ownership — `/api/v1/organizations/**`, `/api/v1/admin/**` (Milestone 10A)

Full design: [ADR-017](../decisions/ADR-017-organization-identity-and-ownership.md).
An `ORGANIZATION` account's own profile and resource-ownership claims,
plus the `ADMIN`-only verification/claim-review surface, plus a
verified organization's public profile. Holding the `ORGANIZATION` role
is **not** the same thing as being a `VERIFIED` organization — every
route below states which of the two it actually requires.

### Organization profile

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/organizations/me` | `ORGANIZATION` role only. Creates the current account's profile, always `PENDING_VERIFICATION`. `{"name": "...", "description": "...", "websiteUrl": "...", "publicEmail": "...", "phone": "...", "addressLine1": "...", "city": "...", "province": "...", "postalCode": "..."}` — only `name` is required. |
| `GET` | `/api/v1/organizations/me` | `ORGANIZATION` role only. The current account's own profile. |
| `PATCH` | `/api/v1/organizations/me` | `ORGANIZATION` role only. Same body shape as create. Changing `name` or `websiteUrl` on an already-`VERIFIED` profile resets it to `PENDING_VERIFICATION`. |
| `GET` | `/api/v1/organizations/{slug}` | Public. Returns only a currently-`VERIFIED` organization's safe public fields; `404` for pending/rejected/suspended or a nonexistent slug — the two are indistinguishable. |

`ownerUserId`, `verifiedByUserId`, and the full audit trail never
appear on any response in this section except the `ADMIN`-only detail
routes below.

### Admin organization verification

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/organizations` | `ADMIN` only. Queue, defaults to `PENDING_VERIFICATION`, oldest submitted first. `verificationStatus`, `page`, `size`, `sort` (`createdAt`, default; `name`). |
| `GET` | `/api/v1/admin/organizations/{organizationId}` | `ADMIN` only. Full detail, including `ownerUserId` (raw id, never resolved to an email). |
| `POST` | `/api/v1/admin/organizations/{organizationId}/verify` | `{"reason": "..."}` — only legal from `PENDING_VERIFICATION`. |
| `POST` | `/api/v1/admin/organizations/{organizationId}/reject` | `{"reason": "..."}` — profile is kept, not deleted; reason visible to the owner. |
| `POST` | `/api/v1/admin/organizations/{organizationId}/suspend` | `{"reason": "..."}` — only legal from `VERIFIED`. Resources the organization already owns are untouched. |
| `GET` | `/api/v1/admin/organizations/{organizationId}/audit-events` | This organization's audit history, oldest first. |

Unlike `/api/v1/moderation/**`, these routes are **`ADMIN` only** —
`MODERATOR` receives `403`. A self-review attempt (an admin who owns
the target organization) returns `403 SELF_REVIEW_NOT_ALLOWED`, no
exception. A decision on an already-decided organization returns `409
CONTRIBUTION_ALREADY_REVIEWED` — the same shared exception Milestone
9A's moderation workflow uses, reused here rather than duplicated.

### Resource ownership claims

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/organizations/me/resource-claims/{resourceId}` | `ORGANIZATION` role, and the current organization must be `VERIFIED`. Target resource must exist, be active, and be currently unowned. No request body. |
| `GET` | `/api/v1/organizations/me/resource-claims` | The current organization's own claims only. `status`, `page`, `size`. |
| `POST` | `/api/v1/organizations/me/resource-claims/{claimId}/withdraw` | Only the current organization's own, still-`PENDING_REVIEW` claim. Another organization's claim id returns `404`, never leaking its existence. |
| `GET` | `/api/v1/organizations/me/resources` | `ORGANIZATION` role only. Resources currently owned (`resources.organization_id` matches), both active and inactive. |
| `GET` | `/api/v1/admin/resource-ownership-claims` | `ADMIN` only. Queue, defaults to `PENDING_REVIEW`, oldest requested first. `status`, `organizationId`, `page`, `size`. |
| `GET` | `/api/v1/admin/resource-ownership-claims/{claimId}` | `ADMIN` only. Full detail, including the resource's current live ownership. |
| `POST` | `/api/v1/admin/resource-ownership-claims/{claimId}/approve` | `{"reason": "..."}` — assigns `resources.organizationId` atomically in the same transaction. |
| `POST` | `/api/v1/admin/resource-ownership-claims/{claimId}/reject` | `{"reason": "..."}` — the resource is never modified. |

**`resources.organization_id` is the sole ownership authority** — a
claim's `status` is workflow history, never itself the grant (see
ADR-017's "Ownership Source of Truth"). Approval re-verifies, inside
the locked transaction, that the organization is still `VERIFIED` and
the resource is still active and unowned; if the resource became owned
in the meantime it returns `409 RESOURCE_ALREADY_OWNED` and leaves the
claim `PENDING_REVIEW` for an admin to explicitly decide. A duplicate
pending claim for the same organization/resource pair returns `409
RESOURCE_OWNERSHIP_CLAIM_CONFLICT`.

**Concurrency**: two different organizations' claims racing for the
same resource are safe — approval locks both the claim row and the
target resource row (reusing the exact lock Milestone 9A's correction-
application flow established), proven with a real two-thread test, not
reasoning alone.

### Public resource attribution

`GET /api/v1/resources/{id}`, `.../slug/{slug}`, and
`GET /api/v1/resources/nearby` all gain an optional `organization`
field — `{"id": "...", "name": "...", "slug": "...", "verified": true}`
— present only when the resource is owned by a currently-`VERIFIED`
organization. The compact list-card response from `GET
/api/v1/resources` never includes it. A suspended organization's
resource stays public and owned; only the attribution disappears.

### Status codes

| Status | Meaning |
|---|---|
| `200` | Profile get/update, queue/detail/decision/audit-events, claim list/withdraw, owned-resources succeeded |
| `201` | Profile or claim created |
| `400` | Validation failure, invalid pagination/sort, or claiming an inactive resource (`INACTIVE_RESOURCE`) |
| `401` | Missing or invalid access token |
| `403` | Current account lacks the required role, the organization is not `VERIFIED` (`ORGANIZATION_NOT_VERIFIED`), or a self-review attempt (`SELF_REVIEW_NOT_ALLOWED`) |
| `404` | No organization/claim exists with the given id, no resource exists with the given id, or the current account has no profile yet (`ORGANIZATION_NOT_FOUND`) |
| `409` | Duplicate profile/slug (`ORGANIZATION_CONFLICT`), already reviewed (`CONTRIBUTION_ALREADY_REVIEWED`), duplicate pending claim (`RESOURCE_OWNERSHIP_CLAIM_CONFLICT`), or the resource is already owned (`RESOURCE_ALREADY_OWNED`) |
