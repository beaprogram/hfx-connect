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
  list: (params: {
    page: number;
    size: number;
    categoryId?: number;
    sort?: string;
    q?: string;
    costType?: string;
    verificationStatus?: string;
    openNow?: boolean;
  }) => [...resourceKeys.all, "list", params] as const,
  detail: (slug: string) => [...resourceKeys.all, "detail", slug] as const,
  /**
   * Nearby-search key (Milestone 7B). Coordinates are rounded to 5 decimal
   * places (~1.1m of precision — see ADR-013) purely so a marker's own
   * onClick "recentre" or floating-point noise doesn't mint a spurious new
   * cache entry/refetch for an unchanged search origin; this rounding is
   * never applied to the actual request sent to the backend. Deliberately
   * excludes `selectedResourceId` — selection is UI state, not part of what
   * identifies this server-state query (see NearbyMapView).
   */
  nearby: (params: {
    latitude: number;
    longitude: number;
    radiusKm: number;
    page: number;
    size: number;
    categoryId?: number;
    q?: string;
    costType?: string;
    verificationStatus?: string;
    openNow?: boolean;
  }) =>
    [
      ...resourceKeys.all,
      "nearby",
      {
        ...params,
        latitude: Math.round(params.latitude * 100_000) / 100_000,
        longitude: Math.round(params.longitude * 100_000) / 100_000,
      },
    ] as const,
};
