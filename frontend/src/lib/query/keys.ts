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

/**
 * Saved-resource keys (Milestone 8A) — private, per-account data. Every key
 * is rooted in `userId` (the current authenticated account's id), never the
 * access-token string itself: a token rotates on every refresh (ADR-008)
 * while the account it represents does not, so keying on the token would
 * mint a pointless new cache entry on every silent refresh. Rooting on
 * `userId` instead of a token also means account A's cache can never be
 * addressed by account B's queries, even before any explicit logout/
 * account-switch cache clearing runs (see `lib/auth/auth-provider.tsx`).
 */
export const savedResourceKeys = {
  all: (userId: string) => ["saved-resources", userId] as const,
  list: (userId: string, params: { page: number; size: number; sort?: string }) =>
    [...savedResourceKeys.all(userId), "list", params] as const,
  /** `resourceIds` sorted so two requests for the same set of ids (in a different order) share one cache entry. */
  status: (userId: string, resourceIds: string[]) =>
    [...savedResourceKeys.all(userId), "status", [...resourceIds].sort()] as const,
};

/**
 * Resource-submission keys (Milestone 8B) — private, per-account data,
 * rooted in `userId` for the same reason `savedResourceKeys` already is.
 */
export const resourceSubmissionKeys = {
  all: (userId: string) => ["resource-submissions", userId] as const,
  list: (userId: string, params: { page: number; size: number; sort?: string }) =>
    [...resourceSubmissionKeys.all(userId), "list", params] as const,
  detail: (userId: string, submissionId: string) =>
    [...resourceSubmissionKeys.all(userId), "detail", submissionId] as const,
};

/**
 * Correction-report keys (Milestone 8B) — private, per-account data, rooted
 * in `userId` for the same reason `savedResourceKeys` already is.
 */
export const correctionReportKeys = {
  all: (userId: string) => ["correction-reports", userId] as const,
  list: (userId: string, params: { page: number; size: number; sort?: string }) =>
    [...correctionReportKeys.all(userId), "list", params] as const,
  detail: (userId: string, reportId: string) =>
    [...correctionReportKeys.all(userId), "detail", reportId] as const,
};
