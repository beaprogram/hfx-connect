"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";
import {
  getSavedResourceStatus,
  removeSavedResource as apiRemoveSavedResource,
  saveResource as apiSaveResource,
} from "@/lib/api/saved-resources";
import { savedResourceKeys } from "@/lib/query/keys";
import { useAuth } from "@/lib/auth/auth-provider";
import type { SavedResourceStatusResponse } from "@/lib/validation/schemas";

/**
 * Batch saved-status lookup for a set of currently-visible resource ids —
 * called once per listing (a resource grid, the map's nearby list, a single
 * detail page), never once per card (see ADR/milestone doc's "Saved-Status
 * Lookup" section). Disabled entirely while signed out or still restoring a
 * session, so anonymous browsing never triggers a private-data request.
 */
export function useSavedResourceStatusMap(resourceIds: string[]): { savedIds: Set<string>; isLoading: boolean } {
  const { state, getValidAccessToken } = useAuth();
  const userId = state.status === "authenticated" ? state.user.id : null;
  // A stable, order-independent representation of the id set — avoids
  // rebuilding the query (and its key) on every render when the caller
  // passes a new array instance with the same ids.
  const sortedIds = useMemo(() => [...resourceIds].sort(), [resourceIds]);

  const query = useQuery({
    enabled: userId !== null && sortedIds.length > 0,
    queryKey: savedResourceKeys.status(userId ?? "", sortedIds),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) {
        // The enabled guard above should prevent this, but a token can
        // still expire mid-flight; an empty result is the safe fallback,
        // not a thrown error, since "can't confirm saved status right now"
        // isn't a user-facing failure worth its own error state.
        return { savedResourceIds: [] } satisfies SavedResourceStatusResponse;
      }
      return getSavedResourceStatus(sortedIds, token, signal);
    },
  });

  return useMemo(
    () => ({
      savedIds: new Set(query.data?.savedResourceIds ?? []),
      isLoading: query.isLoading,
    }),
    [query.data, query.isLoading],
  );
}

/** Patches every cached status query for `userId` that could contain `resourceId`, so every currently-mounted save control updates immediately, not just the one that was clicked. */
function patchStatusCaches(
  queryClient: ReturnType<typeof useQueryClient>,
  userId: string,
  resourceId: string,
  saved: boolean,
) {
  queryClient.setQueriesData<SavedResourceStatusResponse>(
    { queryKey: [...savedResourceKeys.all(userId), "status"] },
    (data) => {
      if (!data) return data;
      const next = new Set(data.savedResourceIds);
      if (saved) {
        next.add(resourceId);
      } else {
        next.delete(resourceId);
      }
      return { savedResourceIds: [...next] };
    },
  );
  // The dashboard's saved-resource list membership itself changed — let it
  // refetch next time it's viewed rather than trying to patch a paginated,
  // sorted list's content in place.
  queryClient.invalidateQueries({ queryKey: [...savedResourceKeys.all(userId), "list"] });
}

/** Ensures a resource is saved for the current user. Idempotent by construction (see the backend's own PUT semantics) — safe to call for an already-saved resource. */
export function useSaveResourceMutation() {
  const { state, getValidAccessToken } = useAuth();
  const queryClient = useQueryClient();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useMutation({
    mutationFn: async (resourceId: string) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      await apiSaveResource(resourceId, token);
      return resourceId;
    },
    onSuccess: (resourceId) => {
      if (userId) {
        patchStatusCaches(queryClient, userId, resourceId, true);
      }
    },
  });
}

/** Ensures a resource is no longer saved for the current user. Idempotent — safe to call for an already-absent relation. */
export function useRemoveSavedResourceMutation() {
  const { state, getValidAccessToken } = useAuth();
  const queryClient = useQueryClient();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useMutation({
    mutationFn: async (resourceId: string) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      await apiRemoveSavedResource(resourceId, token);
      return resourceId;
    },
    onSuccess: (resourceId) => {
      if (userId) {
        patchStatusCaches(queryClient, userId, resourceId, false);
      }
    },
  });
}
