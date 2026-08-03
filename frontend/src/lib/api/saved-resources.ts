import { getJson, postJson, putNoContent, deleteNoContent } from "./client";
import {
  savedResourcePageResponseSchema,
  savedResourceStatusResponseSchema,
  type SavedResourcePageResponse,
  type SavedResourceStatusResponse,
} from "@/lib/validation/schemas";

/**
 * The current authenticated user's saved resources (Milestone 8A). Every
 * function here requires an `accessToken` and hits a `/users/me/...`
 * endpoint — there is no operation anywhere that accepts a user id, because
 * the backend derives the caller's identity from the Bearer token alone
 * (ADR-009). No token is ever logged.
 */

/** Ensures `resourceId` is saved for the current user. Idempotent — safe to call again for an already-saved resource. */
export function saveResource(resourceId: string, accessToken: string, signal?: AbortSignal): Promise<void> {
  return putNoContent(`/api/v1/users/me/saved-resources/${encodeURIComponent(resourceId)}`, { accessToken, signal });
}

/** Ensures `resourceId` is no longer saved for the current user. Idempotent — safe to call again for an already-absent relation. */
export function removeSavedResource(resourceId: string, accessToken: string, signal?: AbortSignal): Promise<void> {
  return deleteNoContent(`/api/v1/users/me/saved-resources/${encodeURIComponent(resourceId)}`, {
    accessToken,
    signal,
  });
}

export interface GetSavedResourcesParams {
  page?: number;
  size?: number;
  /** "savedAt" (default, newest first) or "name" — any other value is rejected by the backend with 400 INVALID_SORT. */
  sort?: string;
  accessToken: string;
  signal?: AbortSignal;
}

export function getSavedResources(params: GetSavedResourcesParams): Promise<SavedResourcePageResponse> {
  const { page = 0, size = 20, sort, accessToken, signal } = params;
  return getJson("/api/v1/users/me/saved-resources", savedResourcePageResponseSchema, {
    params: { page, size, sort },
    accessToken,
    signal,
  });
}

/**
 * Checks saved status for a batch of resource ids in one request — never
 * one request per visible resource card. `resourceIds` is deduplicated
 * client-side before sending purely to keep the request small; the backend
 * independently normalizes duplicates regardless.
 */
export function getSavedResourceStatus(
  resourceIds: string[],
  accessToken: string,
  signal?: AbortSignal,
): Promise<SavedResourceStatusResponse> {
  const uniqueIds = [...new Set(resourceIds)];
  return postJson("/api/v1/users/me/saved-resources/status", savedResourceStatusResponseSchema, {
    body: { resourceIds: uniqueIds },
    accessToken,
    signal,
  });
}
