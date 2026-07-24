# Wireframe — Mobile Navigation

## Structure

At narrow widths, the header collapses the inline nav link into a disclosure button:

```
┌───────────────────────────────┐
│ HFX Connect            [ ☰ Menu ] │  <- button, aria-expanded, aria-controls
├───────────────────────────────┤
│ (menu panel, hidden by default)   │
│   Browse resources                │
└───────────────────────────────┘
```

Only one destination exists at this milestone (`/resources`), so the "menu" is a
single link — still implemented as a real disclosure pattern (not hard-coded open)
so it scales cleanly when Milestone 5+ adds more nav destinations, without pretending
those destinations exist now.

## Behavior

- The toggle button has `aria-expanded="false"|"true"` and `aria-controls` pointing
  at the panel's id.
- Opening the menu moves focus into the panel; `Escape` closes it and returns focus
  to the toggle button; clicking a link inside closes it.
- The panel is reachable and operable with keyboard alone (Tab into the button,
  Enter/Space to toggle, Tab through links, Escape to close).
- Above the desktop breakpoint, the panel and toggle button are not rendered at all
  (the existing inline nav from `SiteHeader` is used directly) — there is no hidden
  duplicate nav in the DOM at desktop widths.

## Accessibility Considerations

- Toggle button has an accessible name that changes with state ("Open menu" /
  "Close menu"), not just an icon.
- Skip-to-main-content link (already present in `layout.tsx`) works identically
  whether or not the mobile menu is open.
- No interaction in the menu relies on hover.
