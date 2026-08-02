"use client";

import { useMapSearch } from "@/lib/map/map-search-context";

/**
 * Appears only after the map has moved meaningfully (see
 * `SEARCH_THIS_AREA_THRESHOLD_METERS`); activating it commits the map's
 * current centre as the new search origin. Searches around the map centre
 * using the currently-selected radius — this is not a rectangular
 * viewport-bounds search (the backend endpoint is radius-based; see
 * ADR-012/ADR-013).
 */
export function SearchThisAreaButton() {
  const { hasPendingSearch, searchThisArea } = useMapSearch();

  if (!hasPendingSearch) return null;

  return (
    <button
      type="button"
      onClick={searchThisArea}
      className="absolute left-1/2 top-3 z-[1000] -translate-x-1/2 rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-900 shadow-md hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
    >
      Search this area
    </button>
  );
}
