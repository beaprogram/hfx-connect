/**
 * Centralized query-key factories. Resource-list keys deliberately include
 * every parameter that changes the result set (page, size, categoryId, sort,
 * q) so a filtered/sorted/paginated/searched view never shares a cache entry
 * with a different one — see docs/architecture/frontend-architecture.md.
 * `q` deliberately does not affect `categoryKeys` — search is a resource-only
 * concept (ADR-010).
 */

export const categoryKeys = {
  all: ["categories"] as const,
  list: (params: { active?: boolean }) => [...categoryKeys.all, "list", params] as const,
};

export const resourceKeys = {
  all: ["resources"] as const,
  list: (params: { page: number; size: number; categoryId?: number; sort?: string; q?: string }) =>
    [...resourceKeys.all, "list", params] as const,
  detail: (slug: string) => [...resourceKeys.all, "detail", slug] as const,
};
