# User Journeys, User Stories, and Acceptance Criteria

## Journey A: Find a Nearby Service

1. User opens the homepage and selects "Use My Location" (or types an area manually).
2. Browser requests location permission.
3. User enters a keyword such as "food bank" and chooses a search radius.
4. Backend searches text and geographic fields together.
5. Results appear in synchronized map and list views.
6. User opens a resource and checks hours, eligibility, transit access, and
   verification date.
7. A logged-in user saves the resource for later.

This journey is implemented incrementally: Milestones 3-4 deliver steps 5-6 without
location awareness (keyword/category search only), Milestone 6 adds structured hours
and open-now filtering, Milestone 7 adds steps 1-4 (geospatial search and the map),
and Milestone 8 adds step 7 (saving).

## Journey B: Correct Inaccurate Information

1. User opens a resource and selects "Report Incorrect Information."
2. User selects the report type and enters the suggested correction.
3. Backend validates and stores the report.
4. A moderator sees the report in the review queue.
5. The moderator accepts the correction, updates the resource, and records an audit
   entry.
6. The report becomes resolved and the reporting user receives a notification.

This journey is delivered by Milestone 8 (steps 1-3) and Milestone 9 (steps 4-6).

## Representative User Stories

- As a student, I want to search for free resources so I can access support within my
  budget.
- As a user, I want to filter locations that are currently open so I do not travel
  unnecessarily.
- As a newcomer, I want plain-language eligibility information so I know whether a
  service applies to me.
- As a user, I want to save resources so I can revisit them later.
- As a community member, I want to report incorrect details so other users are not
  misled.
- As an organization owner, I want to update my listing so the public sees current
  information.
- As a moderator, I want to compare proposed changes against current data before
  approving them.
- As an administrator, I want an audit history so every change is accountable.

## Acceptance Criteria: Nearby Food Assistance

This story anchors the acceptance criteria for the search and geospatial milestones
(6-7):

- User can enter a keyword and select the "Food Assistance" category.
- User can share their location, or manually enter an area when location is denied or
  unavailable.
- Only active resources inside the chosen radius are returned.
- Results are sorted by distance when coordinates are available.
- Each result card displays verification status and open/closed state.
- Denying location permission does not break the search flow — a manual-location
  fallback and category/keyword search remain fully usable.
- An empty result set explains how to broaden the search rather than showing a blank
  page.

## Acceptance Criteria: Submit and Moderate a Correction Report

This story anchors the acceptance criteria for Milestones 8-9:

- An unauthenticated user attempting to submit a report is prompted to log in; the
  action is not silently dropped.
- A submitted report requires a report type and a non-empty description.
- The report appears in the moderator queue with the current resource data alongside
  the suggested correction, not just the raw report text.
- Approving a report updates the resource and creates a `resource_history` entry in
  the same transaction — a partially applied update must never occur.
- The original reporter's identity is never exposed to the public or to the
  organization that owns the resource.
- The reporting user can see the report's status (`pending`, `resolved`, `rejected`) on
  their own dashboard.

## Acceptance Criteria: Unauthorized Access Is Rejected

This story anchors the authorization tests required across Milestones 5, 9, and 10:

- A request to an admin-only or moderator-only endpoint from a `USER`-role account
  returns `403 Forbidden`, not a silently filtered or empty response.
- An `ORGANIZATION`-role account cannot edit a resource it does not own, even by
  guessing a valid resource ID.
- All authorization checks are enforced on the backend; a hidden frontend button is
  not treated as a security control.
