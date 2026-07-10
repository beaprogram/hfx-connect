# Problem, Vision, and Success Criteria

## Problem Statement

Community information in Halifax is fragmented across municipal websites, individual
organization websites, printed flyers, informal social media posts, and word of mouth.
Someone looking for a food bank, a free workshop, a quiet study space, a newcomer
settlement service, or a recreation program has to check several different sources, and
still cannot easily tell whether a listing is current, whether it is free, who is
eligible, or whether it is open right now. This creates unnecessary friction and can
lead to missed services, wasted trips, or people giving up the search entirely.

The problem is most acute for people who are new to the city and do not yet have an
informal network to ask: international students, newcomers and recent immigrants, and
long-time residents who need a service they have never had to look for before.

## Product Vision

> Create the most understandable and trustworthy student-focused directory of Halifax
> community resources, with location-aware search and transparent verification.

HFX Connect consolidates scattered community information into a single, searchable
directory, adds a visible verification history so users can judge how trustworthy a
listing is, and gives every user an easy way to flag information that has gone stale.
Organizations can maintain their own listings instead of having outdated third-party
copies circulate, and moderators keep the overall directory trustworthy through a
transparent review process.

## Non-Goals for the MVP

HFX Connect intentionally does not attempt to be:

- A replacement for emergency services, or a source of medical, legal, or immigration
  advice.
- A social network, a messaging system, or a public discussion forum.
- A native iOS or Android application.
- A system built on microservices, Kubernetes, or advanced AI features before the core
  product (search, submission, moderation) works reliably.
- A platform that automatically scrapes every community website. All MVP content is
  manually sourced and verified so that trust claims are honest.

Deferring these deliberately keeps early milestones focused on a working, testable
vertical slice rather than a wide, shallow feature set.

## Measurable Success Criteria

| Metric | MVP Target | How It Is Measured |
|---|---|---|
| Resource coverage | 30-50 manually verified listings across at least six categories | Count from the admin dashboard / database |
| Search usefulness | At least 80% of test users find a resource they were looking for | Moderated usability test with a written task list |
| Performance | Resource list and detail pages load quickly on a mid-range mobile device | Lighthouse performance score and backend API response-time logging |
| Reliability | No critical errors in the primary user workflows (search, save, submit, report, moderate) | Automated end-to-end test suite run in CI |
| Accessibility | Every core workflow is fully usable with a keyboard alone | Manual keyboard walkthrough plus automated `axe` checks |
| User validation | Feedback collected from at least five real student or newcomer testers | Written feedback notes referenced in the development log |

These targets are intentionally deferred until the relevant functionality exists.
Milestone 1 does not claim any of them are met yet; they exist here so every later
milestone can be measured against a fixed, honest bar instead of a shifting one.

## Related Documents

- [Personas](personas.md)
- [MVP Scope and Feature Priorities](mvp-scope.md)
- [User Journeys and Stories](user-journeys-and-stories.md)
