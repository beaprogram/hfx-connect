"use client";

import { SaveResourceButton } from "@/components/resources/save-resource-button";
import { useSavedResourceStatusMap } from "@/lib/query/use-saved-resources";

/**
 * The detail page's save control (Milestone 8A) — a thin client boundary
 * around `SaveResourceButton` so `ResourceDetail` itself (and the Server
 * Component page that renders it) never needs `"use client"`. Looks up
 * saved status for this one resource — a batch of one is still the correct
 * shape to reuse `useSavedResourceStatusMap` with, since a detail page only
 * ever has one resource visible at a time.
 */
export function ResourceDetailSaveControl({ resourceId, resourceName }: { resourceId: string; resourceName: string }) {
  const { savedIds, isLoading } = useSavedResourceStatusMap([resourceId]);

  return (
    <SaveResourceButton resourceId={resourceId} resourceName={resourceName} isSaved={isLoading ? undefined : savedIds.has(resourceId)} />
  );
}
