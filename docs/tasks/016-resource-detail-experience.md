# Task 016: Resource Detail Experience

## Objective

Build `/resources/[slug]`: full public detail for one active resource, using
the existing slug endpoint, with honest handling of missing optional fields
and a proper not-found experience.

## Context

Part of Milestone 4 (Public Frontend) — see
`docs/milestones/milestone-04-public-frontend.md`.

## Scope

- `app/resources/[slug]/page.tsx` (+ `loading.tsx`, `not-found.tsx`,
  `error.tsx`), `generateMetadata` based on the real resource content,
  deduplicated against the page body's own fetch via React's `cache()`.
- `components/resources/resource-detail.tsx`: formats cost/verification
  enums as readable labels, formats the address as separate lines, links
  phone (`tel:`)/email (`mailto:`)/website (`target="_blank" rel="noopener
  noreferrer"`) safely, and omits Contact/Location/Eligibility sections
  entirely (not as empty headings) when the backend field is absent.
- `components/resources/external-website-link.tsx`: shared safe external
  link, with an accessible "(opens in a new tab)" note.
- `lib/formatting/address.ts`: address-line composition.

## Out of Scope

An "accessibility information" section — the backend has no such field; see
the milestone doc's "A Note on Scope."

## Acceptance Criteria

- A deactivated or nonexistent slug calls `notFound()` and renders the
  route's `not-found.tsx` — verified both return the same UI, since the
  public API already treats them identically.
- No empty section, raw enum value, or `null` is ever rendered.
- Contact links use the correct protocol/security attributes.
- Page `<title>`/description reflect the real resource, not a generic
  placeholder.

## Technical Approach

`generateMetadata` and the page body both need the same resource; wrapping
`getResourceBySlug` in React's `cache()` deduplicates that into a single
network call per page view instead of two.

## Testing Requirements

`resource-detail.test.tsx`: full rendering, missing-optional-field omission
(each section individually), safe contact links, and an explicit assertion
that no "Accessibility" text ever renders.

## Result

Completed. Manually verified against the real running backend that a
genuinely unknown slug renders the not-found content correctly (`curl`
against the dev server — see the milestone doc's Manual Verification
section). A deactivated resource was not separately re-verified in this
session: the frontend's `loadResourceOrNotFound` treats every `404` from
`getResourceBySlug` identically regardless of cause, and Milestone 3C's own
backend tests already establish that a deactivated resource returns `404`
identically to an unknown one — so the unknown-slug check exercises the
same frontend code path a deactivated resource would.

## Related Commits

`feat: build public homepage and resource browsing experience`
