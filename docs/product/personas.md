# Target Users and Personas

HFX Connect serves five distinct user types, each with a different relationship to the
data in the system. Authorization design (to be documented in
`docs/architecture/security-architecture.md` when Milestone 5 implements
authentication) will be built around these same five roles.

## User Types

| User Type | Relationship to the Data |
|---|---|
| Public visitor | Reads public listings and events; no account required |
| Registered user | Saves resources, submits new listings, files correction reports |
| Organization | Manages its own claimed/approved listings and events |
| Moderator | Reviews submissions and reports, edits resources, sets verification status |
| Administrator | Manages users, organizations, categories, and full audit history |

## Personas

### International Student — primary persona

**Context:** Recently arrived in Halifax, limited local knowledge, may not own a car
or have a Canadian phone plan yet.

**Goals:** Find affordable services, study locations, newcomer support, campus-adjacent
events, and transit-accessible options.

**Pain points:** Confusing municipal and organization websites, unclear eligibility
requirements, uncertain or outdated operating hours.

**Representative example — Arup, international graduate student:** Arup wants a quiet
study location near campus, a free career workshop this weekend, and a volunteer
opportunity reachable by bus. He needs filters for distance, cost, opening hours, and
category, and wants confidence that the information was recently verified. This
persona anchors the acceptance criteria for the search and resource-detail workflows
(see [user-journeys-and-stories.md](user-journeys-and-stories.md)).

### Newcomer or Recent Immigrant

**Context:** Needs settlement, language, employment, and community support, often
without an existing local network to ask for recommendations.

**Goals:** Clear, plain-language service descriptions, explicit eligibility
information, language support, and sources they can trust.

**Pain points:** Information fragmentation across sources, and terminology that
assumes familiarity with Canadian systems (health cards, SIN, provincial programs).

### Community Organization Representative

**Context:** Maintains programs, events, and resource information for their
organization, often for multiple third-party listings they do not control.

**Goals:** Claim their listing, keep details current, create events, see how their
listing is being reported on or corrected.

**Pain points:** Outdated third-party copies of their information circulating online,
and repeated manual update requests from the people they serve.

### Moderator or Administrator

**Context:** Responsible for the trustworthiness of the whole directory.

**Goals:** An efficient review queue, full change history, duplicate detection before
approving new submissions, and an audit log they can rely on.

**Pain points:** Spam submissions, conflicting edits from multiple sources, incomplete
listings, and stale data that nobody has flagged yet.

## How Personas Drive Scope

The MVP prioritizes the International Student and Newcomer personas as searchers/
readers, because they represent the largest and most underserved audience and their
needs (cost, eligibility, hours, distance, trust) define the core search and detail
page requirements. Organization and Moderator personas are prioritized next because
without their workflows the directory cannot stay accurate over time — see
[mvp-scope.md](mvp-scope.md) for the resulting feature-priority ordering.
