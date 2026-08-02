import { getJson } from "./client";
import {
  nearbyResourcePageResponseSchema,
  resourcePageResponseSchema,
  resourceResponseSchema,
  type NearbyResourcePageResponse,
  type ResourcePageResponse,
  type ResourceResponse,
} from "@/lib/validation/schemas";
import { RESOURCE_LIST_PAGE_SIZE } from "@/lib/constants/resources";
import { DEFAULT_RADIUS_KM, NEARBY_PAGE_SIZE } from "@/lib/constants/map";

export interface GetResourcesParams {
  page?: number;
  size?: number;
  categoryId?: number;
  sort?: string;
  /** Optional keyword search (ADR-010) — a blank/empty value is omitted from the request entirely, matching "no filter". */
  q?: string;
  /** Optional exact CostType match (Milestone 6B — see ADR-011). */
  costType?: string;
  /** Optional exact VerificationStatus match. */
  verificationStatus?: string;
  /** true narrows to currently-open resources; false/absent is omitted entirely, matching "no filter" (the backend's own default). */
  openNow?: boolean;
  signal?: AbortSignal;
}

export function getResources(params: GetResourcesParams = {}): Promise<ResourcePageResponse> {
  const { page = 0, size = RESOURCE_LIST_PAGE_SIZE, categoryId, sort, q, costType, verificationStatus, openNow, signal } =
    params;
  return getJson("/api/v1/resources", resourcePageResponseSchema, {
    params: {
      page,
      size,
      categoryId,
      sort,
      q: q && q.length > 0 ? q : undefined,
      costType,
      verificationStatus,
      openNow: openNow ? "true" : undefined,
    },
    signal,
  });
}

export function getResourceBySlug(slug: string, signal?: AbortSignal): Promise<ResourceResponse> {
  return getJson(`/api/v1/resources/slug/${encodeURIComponent(slug)}`, resourceResponseSchema, { signal });
}

export interface GetNearbyResourcesParams {
  latitude: number;
  longitude: number;
  radiusKm?: number;
  page?: number;
  size?: number;
  categoryId?: number;
  q?: string;
  costType?: string;
  verificationStatus?: string;
  openNow?: boolean;
  signal?: AbortSignal;
}

/**
 * `GET /api/v1/resources/nearby` (Milestone 7A — see ADR-012/ADR-013). Public,
 * no access token involved. `latitude`/`longitude` must be finite and within
 * their valid ranges before a request is ever sent — an invalid coordinate
 * throws synchronously rather than reaching the network, since a malformed
 * value here would otherwise silently become a confusing 400 from the
 * backend. Every optional filter mirrors {@link getResources}'s own
 * semantics exactly (same blank-is-omitted, false-is-omitted rules).
 */
export function getNearbyResources(params: GetNearbyResourcesParams): Promise<NearbyResourcePageResponse> {
  const {
    latitude,
    longitude,
    radiusKm = DEFAULT_RADIUS_KM,
    page = 0,
    size = NEARBY_PAGE_SIZE,
    categoryId,
    q,
    costType,
    verificationStatus,
    openNow,
    signal,
  } = params;

  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    throw new RangeError("getNearbyResources: latitude must be a finite number between -90 and 90.");
  }
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new RangeError("getNearbyResources: longitude must be a finite number between -180 and 180.");
  }
  if (!Number.isFinite(radiusKm) || radiusKm <= 0) {
    throw new RangeError("getNearbyResources: radiusKm must be a finite number greater than 0.");
  }

  return getJson("/api/v1/resources/nearby", nearbyResourcePageResponseSchema, {
    params: {
      latitude,
      longitude,
      radiusKm,
      page,
      size,
      categoryId,
      q: q && q.length > 0 ? q : undefined,
      costType,
      verificationStatus,
      openNow: openNow ? "true" : undefined,
    },
    signal,
  });
}
