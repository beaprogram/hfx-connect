# Wireframe — Loading, Empty, and Error States

Shared patterns used across the homepage, `/resources`, and `/resources/[slug]`.

## Loading

```
┌──────────────────────────────┐
│ ░░░░░░░░░░░░░  (skeleton title)  │
│ ░░░░░░░░  ░░░░░░░  ░░░░░░       │  <- card skeletons, same
│ ░░░░░░░░  ░░░░░░░  ░░░░░░       │     grid shape as real cards
└──────────────────────────────┘
```

- Route-level `loading.tsx` for `/resources` and `/resources/[slug]` renders static,
  non-animated skeleton shapes matching the real layout (no spinner-only screens, no
  blank page).
- No pulsing/shimmer animation — a static skeleton avoids the "distracting
  animation" the milestone brief warns against, and avoids motion for users who
  haven't opted into it.
- Skeletons reserve the same vertical space as the real content so nothing jumps
  once data arrives (no layout shift).

## Empty

```
┌──────────────────────────────┐
│  No resources match this filter yet │
│  Try a different category, or        │
│  [ Reset filters ]  [ Browse all ]   │
└──────────────────────────────┘
```

Three concrete copies (never the generic "No data"):

| Situation | Copy | Actions offered |
|---|---|---|
| Zero resources exist at all | "No active resources are published yet." | Link to `/` |
| A category filter matches zero resources | "No active resources in **{category}** yet." | "Reset filters" |
| Zero categories exist (homepage) | "Categories aren't available yet." | none needed — page remains usable |

## Error (Backend Failure, Not a 404)

```
┌──────────────────────────────┐
│  Something went wrong loading this page │
│  The backend could not be reached.       │
│  [ Try again ]     <- calls unstable_retry()
└──────────────────────────────┘
```

- Route-level `error.tsx` boundaries (Client Components, per Next.js's requirement)
  catch data-fetch failures and render this pattern; the `unstable_retry()` callback
  Next.js passes in re-fetches and re-renders the segment without a full page reload
  (see `docs/architecture/frontend-architecture.md` for why `unstable_retry` is used
  over the older `reset` prop on this Next.js version).
- No stack trace, backend error code, or internal message is ever rendered — the
  copy is fixed and generic, matching the backend's own rule of never leaking
  internal detail (`docs/api/README.md`).
- Distinguished explicitly from **not-found**: a 404 from the API (unknown/
  deactivated resource, in practice) is treated as "not found" and uses Next.js
  `notFound()` + `not-found.tsx`, never this generic error UI. A malformed/
  unexpected response shape (fails Zod validation) is treated as this generic error,
  not silently converted into an empty result.

## Not Found

```
┌──────────────────────────────┐
│  Resource not found                   │
│  This resource doesn't exist, or is no  │
│  longer active.                          │
│  [ Browse all resources ]                │
└──────────────────────────────┘
```

Used only by `/resources/[slug]/not-found.tsx` when the slug doesn't resolve to an
active resource.

## Invalid URL Parameters (`/resources`)

Not a distinct visual state — invalid `page`/`categoryId`/`sort` values are corrected
to safe defaults before any request is made (see `resource-list.md`), so the page
renders its normal loading/success/empty state against the corrected values rather
than showing an error for a malformed URL.
