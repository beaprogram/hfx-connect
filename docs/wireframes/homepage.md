# Wireframe — Homepage (`/`)

## Content Hierarchy

```
┌──────────────────────────────────────────────────────────┐
│ Header: "HFX Connect" (h-link) ······· nav: Browse resources│  <- site-wide, all pages
├──────────────────────────────────────────────────────────┤
│ Skip to main content (visually hidden until focused)       │
├──────────────────────────────────────────────────────────┤
│  H1  Find trustworthy Halifax community resources           │
│      One paragraph: what HFX Connect is, who it's for       │
│      [ Browse all resources -> ]  (primary action, single)  │
├──────────────────────────────────────────────────────────┤
│  H2  Browse by category                                     │
│      grid of category cards (name + short description),     │
│      each -> /resources?categoryId=<id>                     │
│      empty state if zero categories load                    │
├──────────────────────────────────────────────────────────┤
│  H2  Recently added                                          │
│      up to 3 resource cards (name, category, city, cost)     │
│      "View all resources ->" link                            │
│      empty state if zero resources exist                     │
├──────────────────────────────────────────────────────────┤
│  H2  How verification works                                  │
│      Short, honest paragraph: resources carry a verification │
│      status; most listings start Unverified; moderation is a │
│      planned capability, not implemented yet (Milestone 9)   │
├──────────────────────────────────────────────────────────┤
│ Footer: copyright, GitHub link                                │  <- site-wide
└──────────────────────────────────────────────────────────┘
```

## Main Actions

1. "Browse all resources" (hero) → `/resources`
2. Category card → `/resources?categoryId=<id>`
3. Resource preview card → `/resources/[slug]`
4. "View all resources" → `/resources`

## Navigation Structure

Header has exactly two destinations at this milestone: the brand (→ `/`) and "Browse
resources" (→ `/resources`). No placeholder links for unbuilt features (login, saved
resources, submissions) — see `docs/wireframes/mobile-navigation.md`.

## Mobile Layout

Single column, sections stack in the same order as desktop. Category grid becomes a
single-column list below ~640px. No layout depends on hover.

## Accessibility Considerations

- One `<h1>`; category/preview/verification sections are `<h2>` under it.
- Category cards and resource cards are `<article>` elements with the card's name as
  the only interactive link inside (see `states.md` and `resource-list.md` for the
  "no nested interactive controls" rule that also applies here).
- Hero primary action is a real `<a>`/`<Link>`, reachable by keyboard, with visible
  focus.

## Loading / Empty / Error Behavior

- Categories and the resource preview are independent server-rendered sections; a
  slow or failing backend does not block the hero from rendering (each section
  fetches and fails independently — see `states.md`).
- Zero categories → the category section explains that categories aren't available
  yet rather than rendering an empty grid.
- Zero resources → the preview section explains that no resources are published yet
  and still links to `/resources` (which has its own, fuller empty state).
- Backend unreachable for either section → a short inline message ("Categories
  couldn't be loaded right now.") in that section only, not a full-page error.
