# Wireframes — Milestone 4 (Public Frontend)

Low-fidelity, text-based layout plans written before implementation, per
`docs/development-workflow.md`'s milestone process. These are content-hierarchy and
structure plans, not visual design — see each page's own file for what it covers.

- [Homepage](homepage.md)
- [Resource List (`/resources`)](resource-list.md)
- [Interactive Map (`/resources`, Map view)](map-explorer.md)
- [Resource Detail (`/resources/[slug]`)](resource-detail.md)
- [Mobile Navigation](mobile-navigation.md)
- [Loading, Empty, and Error States](states.md)

## Deviations From These Plans

Recorded in `docs/development-log/2026-07-24.md` and in each page's own file where the
implementation ended up differing meaningfully from what's sketched here (for example,
the resource-list filter/sort controls ended up as a single progressive-enhancement
`<form>` rather than two separate controls, so the page keeps working with JavaScript
disabled — see `resource-list.md`).
