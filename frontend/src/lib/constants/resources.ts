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

/** Fixed page size for the homepage's "recently added" preview. */
export const RESOURCE_PREVIEW_SIZE = 3;
