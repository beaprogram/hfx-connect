import { DEFAULT_RESOURCE_SORT, isResourceSort, type ResourceSort } from "@/lib/constants/resources";

/** Mirrors the backend's own normalization (ADR-010) — see `normalizeQuery` below. */
const MAX_QUERY_LENGTH = 100;

export interface ResourceListParams {
  page: number;
  categoryId?: number;
  sort: ResourceSort;
  q?: string;
}

export type RawSearchParams = Record<string, string | string[] | undefined>;

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/**
 * Trims and collapses whitespace exactly like the backend's own
 * `ResourceSearchQuery.normalize` (see ADR-010) — a blank/whitespace-only
 * value becomes "no keyword filter" (`undefined`), not an error. An
 * over-length value is truncated rather than rejected: the frontend has no
 * validation-error UI for a URL parameter a user could easily have hand-
 * edited, and the backend independently re-validates and rejects an
 * over-length query with its own `400 INVALID_SEARCH_QUERY` regardless —
 * this is a best-effort, safe fallback, not the source of truth.
 */
function normalizeQuery(raw: string | undefined): string | undefined {
  if (raw === undefined) return undefined;
  const collapsed = raw.trim().replace(/\s+/g, " ");
  if (collapsed.length === 0) return undefined;
  return collapsed.length > MAX_QUERY_LENGTH ? collapsed.slice(0, MAX_QUERY_LENGTH) : collapsed;
}

/**
 * Converts raw, untrusted URL search params into a safe, typed shape.
 * Every invalid input (negative/non-numeric page, an unsupported sort value,
 * a malformed categoryId, a repeated query parameter, a blank/over-length
 * search query) falls back to a safe default instead of throwing — see
 * docs/wireframes/resource-list.md's "URL State" section.
 */
export function parseResourceListParams(searchParams: RawSearchParams): ResourceListParams {
  const rawPage = firstValue(searchParams.page);
  const parsedPage = rawPage !== undefined ? Number.parseInt(rawPage, 10) : 0;
  const page = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 0;

  const rawCategoryId = firstValue(searchParams.categoryId);
  const parsedCategoryId = rawCategoryId !== undefined ? Number.parseInt(rawCategoryId, 10) : NaN;
  const categoryId = Number.isFinite(parsedCategoryId) && parsedCategoryId > 0 ? parsedCategoryId : undefined;

  const rawSort = firstValue(searchParams.sort);
  const sort = rawSort !== undefined && isResourceSort(rawSort) ? rawSort : DEFAULT_RESOURCE_SORT;

  const q = normalizeQuery(firstValue(searchParams.q));

  return { page, categoryId, sort, q };
}

/** Builds a `/resources` href from a params object, omitting default/empty values for cleaner URLs. */
export function buildResourcesHref(params: Partial<ResourceListParams>): string {
  const query = new URLSearchParams();
  if (params.categoryId !== undefined) query.set("categoryId", String(params.categoryId));
  if (params.sort !== undefined && params.sort !== DEFAULT_RESOURCE_SORT) query.set("sort", params.sort);
  if (params.q !== undefined && params.q.length > 0) query.set("q", params.q);
  if (params.page !== undefined && params.page > 0) query.set("page", String(params.page));

  const queryString = query.toString();
  return queryString ? `/resources?${queryString}` : "/resources";
}
