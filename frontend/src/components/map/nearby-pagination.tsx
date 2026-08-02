"use client";

import { useMapSearch } from "@/lib/map/map-search-context";

/**
 * Button-based (not `Link`-based) pagination for nearby results — page is
 * client state here, not URL state, since the nearby search origin itself
 * is never in the URL (see ADR-013). Mirrors {@link Pagination}'s labels
 * and 0-based-to-1-based translation.
 */
export function NearbyPagination({ totalPages }: { totalPages: number }) {
  const { nearbyPage, setNearbyPage } = useMapSearch();

  if (totalPages <= 1) return null;

  const hasPrevious = nearbyPage > 0;
  const hasNext = nearbyPage < totalPages - 1;

  return (
    <nav aria-label="Nearby-resource pages" className="mt-4 flex items-center justify-center gap-4">
      <button
        type="button"
        onClick={() => setNearbyPage(nearbyPage - 1)}
        disabled={!hasPrevious}
        className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400 disabled:hover:bg-transparent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Previous page
      </button>

      <span className="text-sm text-slate-600">
        Page {nearbyPage + 1} of {totalPages}
      </span>

      <button
        type="button"
        onClick={() => setNearbyPage(nearbyPage + 1)}
        disabled={!hasNext}
        className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400 disabled:hover:bg-transparent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Next page
      </button>
    </nav>
  );
}
