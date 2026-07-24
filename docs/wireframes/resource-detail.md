# Wireframe — Resource Detail (`/resources/[slug]`)

## Content Hierarchy

```
┌──────────────────────────────────────────────────────────┐
│ Header / skip link (site-wide)                               │
├──────────────────────────────────────────────────────────┤
│ <- Back to all resources   ·   Category name (-> filtered list)│
├──────────────────────────────────────────────────────────┤
│ H1  Resource name                                              │
│     verification-status badge · cost-type badge                │
├──────────────────────────────────────────────────────────┤
│ H2  About                                                      │
│     description paragraph                                      │
├──────────────────────────────────────────────────────────┤
│ H2  Location                                                   │
│     address lines, city, province, postal code                 │
├──────────────────────────────────────────────────────────┤
│ H2  Contact                              (omitted entirely if   │
│     phone (tel:) · email (mailto:) · website (safe external)    │  no contact fields
│                                                                  │  are present)
├──────────────────────────────────────────────────────────┤
│ H2  Cost                                                        │
│     cost-type label + cost details paragraph (if provided)      │
├──────────────────────────────────────────────────────────┤
│ H2  Eligibility                          (omitted entirely if   │
│     eligibility paragraph                                       │  not present)
├──────────────────────────────────────────────────────────┤
│ Small print: added <date>                                       │
├──────────────────────────────────────────────────────────┤
│ Footer (site-wide)                                               │
└──────────────────────────────────────────────────────────┘
```

## A Note on Scope: No Accessibility-Information Section

The task brief for this milestone asks for an "accessibility information" section on
the detail page. The actual `ResourceResponse` returned by the backend (verified
against the live `/v3/api-docs` document before writing any code) has no such field —
`docs/milestones/milestone-03c-public-resource-api.md` and `docs/database/README.md`
confirm the resource schema was deliberately built without one. Inventing a section
for data the API cannot supply would violate this milestone's own "no fabricated
values" rule, so it is omitted here; adding it is future schema/backend work, not a
frontend decision. See the milestone document's "A Note on Scope" section.

## Main Actions

1. "Back to all resources" → `/resources`.
2. Category name → `/resources?categoryId=<id>`.
3. Phone → `tel:`, email → `mailto:`, website → external link
   (`rel="noopener noreferrer"`, `target="_blank"`).

## Mobile Layout

Single column at every width; this page is already narrow-content by design (an
address/contact sheet reads the same on a phone as a desktop). Long resource names
wrap normally; no horizontal scrolling.

## Accessibility Considerations

- One `<h1>` (the resource name); each content block below is an `<h2>`.
- Optional sections (Contact, Eligibility) are omitted from the DOM entirely when
  the backend field is null/blank — no "Phone: —" or empty headings.
- Website links state that they open in a new tab in their accessible name, not
  only via a visual icon.
- Verification/cost badges use icon + text, not colour alone (`states.md` for the
  shared badge pattern also used on `/resources` cards).

## Loading / Empty / Error / Not-Found States

- `loading.tsx` for this route segment renders a skeleton matching this layout's
  shape (heading + a few text blocks) so there's no blank-page flash.
- A slug that doesn't resolve to an active resource (never existed, or has been
  deactivated) calls Next.js `notFound()` and renders this segment's `not-found.tsx`
  — see `states.md`. This is deliberately the same UI for "never existed" and
  "deactivated", since the public API already treats those identically (`404` either
  way) and the frontend has no way to distinguish them, nor should it need to.
- A genuine backend failure (not a 404) renders this segment's `error.tsx` — see
  `states.md`.
