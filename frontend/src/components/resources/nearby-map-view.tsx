"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getNearbyResources } from "@/lib/api/resources";
import { resourceKeys } from "@/lib/query/keys";
import { NEARBY_PAGE_SIZE } from "@/lib/constants/map";
import { useMapSearch } from "@/lib/map/map-search-context";
import { NearbyResourceCard } from "@/components/resources/nearby-resource-card";
import { useSavedResourceStatusMap } from "@/lib/query/use-saved-resources";
import { UseMyLocationControl } from "@/components/map/use-my-location-control";
import { RadiusSelector } from "@/components/map/radius-selector";
import { SearchThisAreaButton } from "@/components/map/search-this-area-button";
import { NearbyPagination } from "@/components/map/nearby-pagination";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import type { CostTypeFilterValue, VerificationStatusFilterValue } from "@/lib/constants/resources";

// Leaflet reads `window`/`document` at import time — this must never run
// during server rendering (see ADR-013's "Client-Only Map Rendering").
const NearbyMap = dynamic(() => import("@/components/map/nearby-map").then((mod) => mod.NearbyMap), {
  ssr: false,
  loading: () => (
    <div className="flex h-full w-full items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-sm text-slate-500">
      Loading map…
    </div>
  ),
});

export interface NearbyMapViewFilters {
  q?: string;
  categoryId?: number;
  costType?: CostTypeFilterValue;
  verificationStatus?: VerificationStatusFilterValue;
  openNow?: boolean;
}

export function NearbyMapView({ filters }: { filters: NearbyMapViewFilters }) {
  const {
    centre,
    centreSource,
    radiusKm,
    nearbyPage,
    selectedResourceId,
    setSelectedResourceId,
    setPendingCentre,
    resetNearbyPage,
  } = useMapSearch();
  const [mobilePanel, setMobilePanel] = useState<"map" | "list">("map");

  const filterKey = JSON.stringify(filters);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => resetNearbyPage(), [filterKey, centre?.latitude, centre?.longitude, radiusKm]);

  const query = useQuery({
    enabled: centre !== null,
    queryKey: resourceKeys.nearby({
      latitude: centre?.latitude ?? 0,
      longitude: centre?.longitude ?? 0,
      radiusKm,
      page: nearbyPage,
      size: NEARBY_PAGE_SIZE,
      ...filters,
    }),
    queryFn: ({ signal }) =>
      getNearbyResources({
        latitude: centre!.latitude,
        longitude: centre!.longitude,
        radiusKm,
        page: nearbyPage,
        size: NEARBY_PAGE_SIZE,
        ...filters,
        signal,
      }),
  });

  const results = query.data?.content ?? [];
  // One batched saved-status lookup for the whole current result page —
  // never one request per card (see ADR/milestone doc's "Saved-Status
  // Lookup" section).
  const { savedIds } = useSavedResourceStatusMap(results.map((r) => r.id));

  // A selection that no longer appears in the current result page (a new
  // search, a filter change, or pagination) is cleared rather than left
  // pointing at stale data.
  useEffect(() => {
    if (selectedResourceId && !results.some((r) => r.id === selectedResourceId)) {
      setSelectedResourceId(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results]);

  if (!centre) return null;

  return (
    <div>
      <div className="flex flex-wrap items-end gap-4">
        <UseMyLocationControl />
        <RadiusSelector />
      </div>

      <p className="mt-3 text-sm text-slate-600" aria-live="polite">
        {centreSource === "default" && "Showing resources near central Halifax."}
        {centreSource === "geolocation" && "Showing resources near your location."}
        {centreSource === "manual" && "Showing resources near the selected map area."}
      </p>

      <div className="mt-4 lg:hidden">
        <div role="group" aria-label="Map panel" className="inline-flex rounded-md border border-slate-300 p-0.5">
          {(["map", "list"] as const).map((panel) => (
            <button
              key={panel}
              type="button"
              aria-pressed={mobilePanel === panel}
              onClick={() => setMobilePanel(panel)}
              className={`rounded-sm px-3 py-1.5 text-sm font-medium capitalize focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
                mobilePanel === panel ? "bg-slate-900 text-white" : "text-slate-700 hover:bg-slate-50"
              }`}
            >
              {panel}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className={`${mobilePanel === "list" ? "block" : "hidden"} lg:block`} aria-live="polite">
          {query.isPending && <p className="text-sm text-slate-600">Loading nearby resources…</p>}

          {query.isError && (
            <InlineError message="Nearby resources couldn't be loaded right now. Please try again in a moment." />
          )}

          {query.isSuccess && results.length === 0 && (
            <EmptyState heading="No resources found nearby.">
              <p>Try increasing the search radius, clearing filters, or moving the map to another area.</p>
            </EmptyState>
          )}

          {query.isSuccess && results.length > 0 && (
            <>
              <p className="mb-3 text-sm text-slate-600">
                {query.data.totalElements} resource{query.data.totalElements === 1 ? "" : "s"} within {radiusKm} km
              </p>
              <ul className="flex flex-col gap-3">
                {results.map((resource) => (
                  <li key={resource.id}>
                    <NearbyResourceCard
                      resource={resource}
                      selected={resource.id === selectedResourceId}
                      onSelect={setSelectedResourceId}
                      isSaved={savedIds.has(resource.id)}
                    />
                  </li>
                ))}
              </ul>
              <NearbyPagination totalPages={query.data.totalPages} />
            </>
          )}
        </div>

        <div className={`relative h-[60vh] lg:h-[calc(100vh-20rem)] lg:min-h-[500px] ${mobilePanel === "map" ? "block" : "hidden"} lg:block`}>
          <SearchThisAreaButton />
          <NearbyMap
            centre={centre}
            results={results}
            selectedResourceId={selectedResourceId}
            onSelectResource={setSelectedResourceId}
            onPendingCentreChange={setPendingCentre}
          />
        </div>
      </div>
    </div>
  );
}
