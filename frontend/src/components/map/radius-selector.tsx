"use client";

import { RADIUS_OPTIONS_KM, type RadiusKm } from "@/lib/constants/map";
import { useMapSearch } from "@/lib/map/map-search-context";

export function RadiusSelector() {
  const { radiusKm, setRadiusKm } = useMapSearch();

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor="radiusKm" className="text-sm font-medium text-slate-700">
        Search radius
      </label>
      <select
        id="radiusKm"
        name="radiusKm"
        value={radiusKm}
        onChange={(event) => setRadiusKm(Number(event.target.value) as RadiusKm)}
        className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        {RADIUS_OPTIONS_KM.map((option) => (
          <option key={option} value={option}>
            Within {option} km
          </option>
        ))}
      </select>
    </div>
  );
}
