/**
 * The only two sort values the backend accepts (`docs/api/README.md`'s
 * Resources section) — an unrecognized value returns `400 INVALID_SORT`, so
 * this list is the single source of truth for what the UI is allowed to send.
 */
export const RESOURCE_SORT_OPTIONS = [
  { value: "name", label: "Name: A to Z" },
  { value: "createdAt", label: "Newest added" },
] as const;

export type ResourceSort = (typeof RESOURCE_SORT_OPTIONS)[number]["value"];

export const DEFAULT_RESOURCE_SORT: ResourceSort = "name";

export function isResourceSort(value: string): value is ResourceSort {
  return RESOURCE_SORT_OPTIONS.some((option) => option.value === value);
}

/** Fixed page size for `/resources` — not exposed as a user-facing control. */
export const RESOURCE_LIST_PAGE_SIZE = 12;

/** Monday-Sunday display order for a resource's weekly schedule (Milestone 6B — see ADR-011). */
export const WEEKLY_DAY_ORDER = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

/** Fixed page size for the homepage's "recently added" preview. */
export const RESOURCE_PREVIEW_SIZE = 3;

/**
 * The real `CostType` enum values (Milestone 6B — see ADR-011) — an
 * unrecognized value returns `400 INVALID_COST_TYPE`, so this list is the
 * single source of truth for what the cost filter is allowed to send.
 */
export const COST_TYPE_FILTER_OPTIONS = [
  { value: "FREE", label: "Free" },
  { value: "LOW_COST", label: "Low cost" },
  { value: "PAID", label: "Paid" },
  { value: "UNKNOWN", label: "Cost not listed" },
] as const;

export type CostTypeFilterValue = (typeof COST_TYPE_FILTER_OPTIONS)[number]["value"];

export function isCostTypeFilterValue(value: string): value is CostTypeFilterValue {
  return COST_TYPE_FILTER_OPTIONS.some((option) => option.value === value);
}

/** The real `VerificationStatus` enum values — an unrecognized value returns `400 INVALID_VERIFICATION_STATUS`. */
export const VERIFICATION_STATUS_FILTER_OPTIONS = [
  { value: "VERIFIED", label: "Verified" },
  { value: "UNVERIFIED", label: "Not yet verified" },
] as const;

export type VerificationStatusFilterValue = (typeof VERIFICATION_STATUS_FILTER_OPTIONS)[number]["value"];

export function isVerificationStatusFilterValue(value: string): value is VerificationStatusFilterValue {
  return VERIFICATION_STATUS_FILTER_OPTIONS.some((option) => option.value === value);
}
