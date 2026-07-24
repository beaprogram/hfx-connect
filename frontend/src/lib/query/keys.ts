/**
 * Centralized query-key factories. Resource-list keys deliberately include
 * every parameter that changes the result set (page, size, categoryId, sort)
 * so a filtered/sorted/paginated view never shares a cache entry with a
 * different one — see docs/architecture/frontend-architecture.md.
 */

export const categoryKeys = {
  all: ["categories"] as const,
  list: (params: { active?: boolean }) => [...categoryKeys.all, "list", params] as const,
};

export const resourceKeys = {
  all: ["resources"] as const,
  list: (params: { page: number; size: number; categoryId?: number; sort?: string }) =>
    [...resourceKeys.all, "list", params] as const,
  detail: (slug: string) => [...resourceKeys.all, "detail", slug] as const,
};
