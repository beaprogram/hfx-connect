# Resume Evidence

This document records, feature by feature, the concrete evidence behind every resume
claim about HFX Connect. It exists so that every resume bullet drafted from this
project is traceable to real, committed, tested work — not aspiration.

**Status: foundation only.** No application features have been implemented yet
(Milestone 1 is documentation-only). Entries are added starting with Milestone 2, once
there is real code, tests, and behavior to describe.

## How an Entry Is Added

Each entry follows this structure:

```markdown
## <Feature Name>

### Product Purpose
Why this feature exists from the user's perspective.

### Technologies Used
Specific libraries, frameworks, and techniques — not just the top-level stack.

### Engineering Complexity
What made this non-trivial: validation, concurrency, transactions, geospatial
queries, authorization, etc.

### Implementation
Where the code lives (file paths / package names) and how it fits the architecture.

### Tests
What is actually tested, and how (unit, integration, Testcontainers, Playwright).

### Evidence
Commit hashes, PR links, or a short reproducible demonstration.

### Potential Resume Wording
Action verb + system/feature + technologies + engineering complexity + verified
result.

### Measurements Still Needed
Anything not yet measured is written as `[MEASURE AFTER DEPLOYMENT]` rather than
invented. Examples: load time, test coverage percentage, real user counts.
```

## Rules

- Never invent user counts, production usage, percentages, performance numbers, test
  coverage, accessibility scores, or user feedback.
- Use `[MEASURE AFTER DEPLOYMENT]` as an explicit placeholder until a real measurement
  exists, and replace it once it does.
- Only document decisions and features that actually exist in the committed code at
  the time of writing.

## Entries

_None yet — the first entries will be added in Milestone 2 once the backend and
frontend are initialized and the first real endpoint exists._
