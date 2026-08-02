"use client";

import { useMapSearch, type ExplorerView } from "@/lib/map/map-search-context";

const OPTIONS: { value: ExplorerView; label: string }[] = [
  { value: "list", label: "List" },
  { value: "map", label: "Map" },
];

/**
 * The top-level List/Map presentation switch (see ADR-013's "View-Mode
 * Design"). "List" is the plain, existing non-geospatial browsing
 * experience, unchanged since Milestone 6. "Map" activates the nearby-
 * search experience (defaulting to central Halifax on first activation).
 * Selected state is communicated through more than colour: `aria-pressed`
 * plus a filled background and bold text.
 */
export function ViewToggle() {
  const { view, setView } = useMapSearch();

  return (
    <div role="group" aria-label="Presentation" className="inline-flex rounded-md border border-slate-300 p-0.5">
      {OPTIONS.map((option) => {
        const active = view === option.value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={active}
            onClick={() => setView(option.value)}
            className={`rounded-sm px-3 py-1.5 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
              active ? "bg-slate-900 text-white" : "text-slate-700 hover:bg-slate-50"
            }`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
