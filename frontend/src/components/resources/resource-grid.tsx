import { ResourceCard } from "@/components/resources/resource-card";
import type { ResourceSummaryResponse } from "@/lib/validation/schemas";

/** `savedIds` comes from one batched status lookup for the whole grid (see `useSavedResourceStatusMap`) — never fetched per card. `undefined` while signed out or that lookup hasn't resolved yet. */
export function ResourceGrid({
  resources,
  savedIds,
}: {
  resources: ResourceSummaryResponse[];
  savedIds?: Set<string>;
}) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {resources.map((resource) => (
        <ResourceCard key={resource.id} resource={resource} isSaved={savedIds?.has(resource.id)} />
      ))}
    </div>
  );
}
