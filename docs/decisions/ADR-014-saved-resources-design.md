# ADR-014: Saved Resources — Data Model, API Contract, and Cache Isolation Design

## Status

Accepted — 2026-08-03

## Context

Milestone 8A adds the first genuinely private, per-account data this
project has ever stored beyond authentication itself: a user's saved
resources. Several decisions need making before writing code:

- Whether this needs a generic "bookmark"/"favorite" framework capable of
  saving arbitrary entity types, or a focused relation between users and
  resources specifically.
- How the entity references its two sides — bidirectional JPA
  collections on `User`/`CommunityResource`, or plain unidirectional
  references.
- What happens when a user saves a resource that later becomes inactive,
  and what happens if the resource never existed or is already inactive
  at save time.
- How a concurrent double-save (the same user clicking Save twice in
  quick succession, or two tabs racing) is resolved without a 500 or a
  visible error.
- How the frontend shows saved/not-saved status on many visible resource
  cards at once without one HTTP request per card, while keeping this
  private data correctly isolated per account in a client-side cache that
  persists across a login/logout/account-switch within the same browser
  session.
- How a signed-out visitor is invited to save a resource without an
  open-redirect vulnerability in the "come back here after signing in"
  flow.

## Decision

### A Focused `SavedResource` Entity, Not a Generic Bookmark Framework

`SavedResource` has exactly the fields this feature needs: a
surrogate `id`, a plain `userId` (`UUID`, no `@ManyToOne` to `User` —
nothing here ever needs to read a field off the associated user), a
lazy unidirectional `@ManyToOne CommunityResource resource` (this
association's fields — name, slug, category, cost, verification,
hours — genuinely are read on every list/status response, unlike
`userId`), and `createdAt`. No generic `Bookmarkable`/`SavedItem` base
entity, no polymorphic target-type column — this project has exactly
one thing users save today, and a future different kind of saved item
(if one is ever added) can get its own equally-focused table rather
than forcing both through one premature abstraction.

### Unidirectional Only — No Collection on `User` or `CommunityResource`

Neither `User` nor `CommunityResource` gained a `@OneToMany
List<SavedResource>` field. A saved-resources collection on `User`
would force Hibernate to consider loading it whenever a `User` entity
loads (or require explicit `FetchType.LAZY` discipline every caller
must remember), for a relationship that is, in every real use case,
queried from the `SavedResource` side (`findByUserId`, not
`user.getSavedResources()`). This matches the same reasoning already
applied to `ResourceOperatingHours.resourceId` (V6) and
`RefreshSession.userId` (V5) — neither of those gained a matching
collection on their owning entity either.

### Database Uniqueness Is the Authority, Not the Application-Level Check Alone

`SavedResourceService.save` checks `existsByUserIdAndResource_Id`
first (an ordinary, correct guard for the overwhelmingly common
single-request case), but the database's `UNIQUE (user_id,
resource_id)` constraint (`V8`) is what actually prevents a duplicate
row under a genuine concurrent-request race — two near-simultaneous
save requests can both pass the existence check before either commits.
The service catches the resulting
`DataIntegrityViolationException` from the losing insert and treats it
as success (the resource ends up saved either way, which is exactly
what idempotent `PUT` semantics promise), rather than surfacing a `500`
or `409` for what is, from the caller's perspective, not an error at
all.

### Deactivation vs. Deletion — the Relation Outlives a Resource's Active Flag, Not Its Row

Saving a resource requires it to currently be active (`404`-equivalent
otherwise — the same "don't reveal detail about something that isn't
really there" posture used elsewhere in this API for inactive/missing
resources). Once saved, the relation is **not** removed if the resource
is later deactivated — `resources.active` toggling to `false` is a
soft, reversible state, and a user's saved list should reappear if a
resource is reactivated rather than silently and permanently losing the
save. The saved-resources **list** endpoint therefore filters to active
resources only (an inactive save is invisible, not deleted), while
**removal** works unconditionally regardless of the resource's current
active state — a user must always be able to un-save something,
including something that has since gone inactive. Only an actual
resource-row deletion (which no endpoint currently performs) cascades
the relation away, via `resource_id ... ON DELETE CASCADE`.

### Batch Status Endpoint — One Request Per Listing, Never Per Card

`POST /api/v1/users/me/saved-resources/status` accepts up to 100
resource ids and returns which of them are saved, so a page of resource
cards (list, nearby/map, or a single detail page) issues exactly one
status request regardless of how many cards it renders. The frontend's
`useSavedResourceStatusMap(resourceIds)` hook is called once per
listing container — never inside an individual card component — and its
TanStack Query key sorts the id list first, so the same set of ids in a
different render order still hits one shared cache entry rather than
creating a new one on every render.

### Query Keys Rooted in `userId`, Not the Access Token

Every saved-resource query key (`savedResourceKeys.all/list/status`)
starts with the authenticated user's stable `id`, never the access
token string — tokens rotate on refresh (a live, valid session would
otherwise fragment its own cache across token rotations), while
`userId` is stable for the life of the session and unambiguous across
accounts.

### Explicit Cache Clearing on Logout and Account Switch

Saved resources are private account data; leaving them in a shared
`QueryClient` cache across a logout or a switch to a different account
in the same browser tab would let a second user see the first user's
saved state, even briefly, before their own status request resolves.
`AuthProvider` (which already owns the authentication lifecycle) now
also holds a `useQueryClient()` reference and removes every
`saved-resources`-rooted query, unconditionally, from both `logout()`
and the moment `applySession()` detects the newly-authenticated user's
id differs from the previously-authenticated one. This was chosen over
scoping every query to `userId` alone (which prevents cross-account
data leakage but would still leave a stale, orphaned cache entry
growing unboundedly across many account switches in one session) and
over a full `queryClient.clear()` (which would also discard unrelated,
harmless public caches like the category list, for no benefit).

### Save/Remove Controls Reuse Existing Auth, API-Client, and Query Patterns

No second authentication architecture, no bespoke fetch wrapper. The
save/remove mutations use the same `getValidAccessToken()` from
`useAuth()` and the same `ApiRequestError`-throwing `client.ts` used by
every other authenticated write in this codebase (the operating-hours
and location `PUT` endpoints). Idempotent `PUT`/`DELETE` semantics on
the wire are mirrored one-to-one in `TanStack Query`'s `useMutation`:
clicking Save (or Remove) while a mutation is already in flight is
prevented at the UI level by disabling the button, not re-implemented
as request de-duplication logic.

### Signed-Out "Sign In to Save" Link with a Validated, Same-Origin-Only `returnTo`

A signed-out visitor sees a "Sign in to save {resource name}" link
instead of a disabled or hidden control — inviting the exact action
that requires authentication, not just blocking it silently. The link's
`returnTo` query parameter is validated by `isSafeReturnPath` before
ever being used to redirect: it must start with `/`, must not start
with `//` or `/\` (protocol-relative-URL smuggling), and must parse
(via `new URL(path, "https://return-to.invalid")`) to the exact same
placeholder origin it was resolved against — collectively rejecting
every absolute URL, protocol-relative URL, and cross-origin string
before it can ever reach a `router.push`/`window.location` assignment.
An invalid or missing `returnTo` falls back to `/dashboard`, never to
an unvalidated caller-supplied destination.

## Consequences

- Saving a resource is exactly one indexed lookup (`existsBy...`) plus,
  in the common case, one insert; a race is resolved by one caught
  exception on the rare losing side — no additional locking, retry loop,
  or distributed coordination was needed for a single-Postgres-instance
  deployment.
- The saved list excluding inactive resources, while preserving the
  underlying row, means a user who saved a resource that later goes
  inactive sees it silently disappear from their list rather than an
  error or a broken card — and sees it reappear automatically if the
  resource is reactivated, with no re-saving required. This is a
  deliberate product behavior, not a bug, and is called out explicitly
  in the milestone document and API docs so it is never mistaken for
  one.
- A future second kind of "saved item" (if the product ever needs one)
  requires its own focused entity/table/repository, following this same
  pattern, rather than extending `SavedResource` with a polymorphic
  target type — judged the right tradeoff today given exactly one saved
  item type exists, at the cost of some duplication if a second type is
  ever actually added.
- Every authenticated component that now needs saved-status data
  (`ResourceCard`, `NearbyResourceCard`, the detail page) also needs a
  `QueryClientProvider` ancestor and, in tests, a mocked `useAuth()` —
  this cascaded into updating several pre-existing test files that had
  previously rendered these components in isolation, now documented as
  the established convention for any future component in this tree that
  needs authenticated data.
- `AuthProvider` taking on a `useQueryClient()` dependency ties two
  previously-separate pieces of frontend infrastructure (authentication
  state and TanStack Query) more closely together than before; this was
  judged acceptable because the alternative — every consumer of saved
  data remembering to invalidate on logout individually — is exactly
  the kind of easy-to-forget, security-relevant responsibility that
  belongs centralized in the one place the logout/switch event actually
  happens.
