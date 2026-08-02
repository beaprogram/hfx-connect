"use client";

import { useMapSearch } from "@/lib/map/map-search-context";
import { ResourceListView } from "@/components/resources/resource-list-view";
import { MapExplorerView } from "@/components/resources/map-explorer-view";
import { ViewToggle } from "@/components/resources/view-toggle";
import type { ResourceListParams } from "@/lib/query/resource-list-params";

/**
 * `/resources`'s top-level presentation switch (Milestone 7B — see
 * ADR-013). "List" renders the existing, unmodified Milestone 6
 * `ResourceListView` (server-prefetched, works without any map
 * JavaScript). "Map" renders {@link MapExplorerView}, the nearby-search
 * experience — never the other way around, and never both at once: the map
 * enhances the list, it never replaces it as the only way to discover
 * resources.
 */
export function ResourceExplorer({ params }: { params: ResourceListParams }) {
  const { view } = useMapSearch();

  if (view === "map") {
    return <MapExplorerView params={params} />;
  }

  return <ResourceListView params={params} headerActions={<ViewToggle />} />;
}
