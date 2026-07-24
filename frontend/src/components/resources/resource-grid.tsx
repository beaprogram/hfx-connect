import { ResourceCard } from "@/components/resources/resource-card";
import type { ResourceSummaryResponse } from "@/lib/validation/schemas";

export function ResourceGrid({ resources }: { resources: ResourceSummaryResponse[] }) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {resources.map((resource) => (
        <ResourceCard key={resource.id} resource={resource} />
      ))}
    </div>
  );
}
