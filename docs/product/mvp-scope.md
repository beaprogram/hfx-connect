# MVP Scope and Feature Priorities

## Capabilities by User Type

| User Type | MVP Capabilities |
|---|---|
| Public | Browse, search, filter, map/list view, resource detail, events, report inaccurate information |
| Registered user | Save resources, submit resources, track submissions and reports |
| Organization | Manage approved listings and create events after verification |
| Moderator | Review submissions and reports, edit resources, mark verification status |
| Administrator | Manage users, organizations, categories, audit history, and system settings |

## Priority Classification

| Priority | Features | Rationale |
|---|---|---|
| Must have | Resources, categories, search, resource detail, authentication, saves, submissions, moderation, deployment | Without these, there is no usable product — this is the smallest set that satisfies the core journey in [problem-and-vision.md](problem-and-vision.md) |
| Should have | Map, distance sorting, structured hours, verification badge, events, audit history | High user value, but the product is still usable and testable without them (list view substitutes for the map; unstructured hours are acceptable short-term) |
| Could have | Transit stops, email notifications, organization analytics, natural-language search | Genuine enhancements, but none block the core discover → verify → correct loop |
| Later | Mobile app, recommendation engine, multilingual content, advanced personalization | Explicitly out of scope until the core product has real users and feedback |

## Roadmap Alignment

This priority ordering maps directly onto the milestone roadmap:

1. **Milestones 1-4** deliver the "must have" resource/category domain and public
   frontend without authentication yet (an anonymous read-only vertical slice first).
2. **Milestone 5** adds authentication, unlocking saves, submissions, and role-based
   access — the remaining "must have" items.
3. **Milestones 6-7** deliver "should have" search, filtering, and geospatial/map
   features.
4. **Milestones 8-10** deliver the remaining "must have" moderation loop plus the
   "should have" events and organization management.
5. **Milestone 11-12** harden and ship the MVP; "could have" and "later" items are
   deliberately excluded from the 12-week plan.

## Why Search Comes Before Maps

Search and list-based discovery are ranked above the interactive map because they are
usable without location permission, without a mapping API budget, and without
JavaScript-heavy client rendering — they are the accessible baseline. The map is a
enhancement on top of the same underlying data, not a replacement for it (see the
accessibility requirement that map content always has a list alternative).

## Content Scope

The MVP targets 30-50 manually verified Halifax listings across six categories: Food,
Study, Employment, Newcomer Support, Recreation, and Events. This number is small
enough to verify by hand (protecting the "trustworthy" part of the product vision) and
large enough to be genuinely useful for the primary personas.
