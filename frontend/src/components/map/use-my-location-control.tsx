"use client";

import { useEffect } from "react";
import { useGeolocation } from "@/lib/map/use-geolocation";
import { useMapSearch } from "@/lib/map/map-search-context";

/**
 * The one and only place geolocation is ever requested from — always in
 * direct response to this button's activation, never automatically (see
 * ADR-013's "Browser Geolocation Flow"). Status text uses `role="status"`
 * (polite, non-interrupting) rather than `role="alert"`, since permission
 * denial/timeout/unavailable are all normal, recoverable outcomes here, not
 * application failures.
 */
export function UseMyLocationControl() {
  const { status, coordinates, requestLocation } = useGeolocation();
  const { applyGeolocatedCentre } = useMapSearch();

  // Commits once per successful fix — `coordinates` only gets a new object
  // reference when a fresh position arrives, so this doesn't re-run on
  // every render or repeatedly recentre the map.
  useEffect(() => {
    if (status === "success" && coordinates) {
      applyGeolocatedCentre(coordinates);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, coordinates]);

  const isBusy = status === "requesting";
  const buttonLabel =
    status === "idle" || status === "requesting"
      ? "Use my location"
      : status === "success"
        ? "Update my location"
        : "Try again";

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={requestLocation}
        disabled={isBusy}
        aria-busy={isBusy}
        className="whitespace-nowrap rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        {buttonLabel}
      </button>
      <p role="status" className="min-h-[1.25rem] text-xs text-slate-600">
        {statusMessage(status)}
      </p>
    </div>
  );
}

function statusMessage(status: ReturnType<typeof useGeolocation>["status"]): string {
  switch (status) {
    case "idle":
      return "";
    case "requesting":
      return "Finding your location…";
    case "success":
      return "Location found — showing resources near you.";
    case "permission-denied":
      return "Location permission was not granted. You can still move the map or browse resources near central Halifax.";
    case "unavailable":
      return "Your location could not be determined. Try again or search another area on the map.";
    case "timeout":
      return "Finding your location took too long. Try again, or search another area on the map.";
  }
}
