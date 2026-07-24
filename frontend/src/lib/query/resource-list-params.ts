import { DEFAULT_RESOURCE_SORT, isResourceSort, type ResourceSort } from "@/lib/constants/resources";

export interface ResourceListParams {
  page: number;
  categoryId?: number;
  sort: ResourceSort;
}

export type RawSearchParams = Record<string, string | string[] | undefined>;

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/**
 * Converts raw, untrusted URL search params into a safe, typed shape.
 * Every invalid input (negative/non-numeric page, an unsupported sort value,
 * a malformed categoryId, a repeated query parameter) falls back to a safe
 * default instead of throwing — see docs/wireframes/resource-list.md's "URL
 * State" section.
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

  return { page, categoryId, sort };
}

/** Builds a `/resources` href from a params object, omitting default/empty values for cleaner URLs. */
export function buildResourcesHref(params: Partial<ResourceListParams>): string {
  const query = new URLSearchParams();
  if (params.categoryId !== undefined) query.set("categoryId", String(params.categoryId));
  if (params.sort !== undefined && params.sort !== DEFAULT_RESOURCE_SORT) query.set("sort", params.sort);
  if (params.page !== undefined && params.page > 0) query.set("page", String(params.page));

  const queryString = query.toString();
  return queryString ? `/resources?${queryString}` : "/resources";
}
