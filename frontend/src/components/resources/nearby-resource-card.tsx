"use client";

import Link from "next/link";
import { CostBadge, HoursStatusBadge, VerificationBadge } from "@/components/resources/status-badges";
import { formatCityProvince } from "@/lib/formatting/location";
import { formatDistanceAway } from "@/lib/formatting/distance";
import type { NearbyResourceSummaryResponse } from "@/lib/validation/schemas";

/**
 * The map/list-synchronized counterpart of {@link ResourceCard} — adds
 * distance-from-search-origin and a selectable state shared with the map's
 * markers (see ADR-013's "List and Map Synchronization"). Selection is
 * always communicated through a visible text label and a thicker border,
 * never colour alone.
 */
export function NearbyResourceCard({
  resource,
  selected,
  onSelect,
}: {
  resource: NearbyResourceSummaryResponse;
  selected: boolean;
  onSelect: (id: string) => void;
}) {
  const location = formatCityProvince(resource.city, resource.province);

  return (
    <article
      aria-current={selected ? "true" : undefined}
      className={`relative flex flex-col gap-2 rounded-lg border p-4 transition-colors ${
        selected ? "border-2 border-blue-600 bg-blue-50" : "border-slate-200 hover:border-slate-300"
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-base font-semibold text-slate-900">
          <button
            type="button"
            onClick={() => onSelect(resource.id)}
            className="rounded-sm text-left after:absolute after:inset-0 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {resource.name}
          </button>
        </h3>
        {selected && (
          <span className="whitespace-nowrap rounded-full bg-blue-600 px-2 py-0.5 text-xs font-medium text-white">
            Selected
          </span>
        )}
      </div>
      <p className="text-sm text-slate-600">
        {resource.category.name}
        {location ? ` · ${location}` : ""} · {formatDistanceAway(resource.distanceMeters)}
      </p>
      <div className="mt-1 flex flex-wrap gap-2">
        <CostBadge costType={resource.costType} />
        <VerificationBadge status={resource.verificationStatus} />
        <HoursStatusBadge status={resource.hoursStatus} />
      </div>
      <Link
        href={`/resources/${resource.slug}`}
        className="relative z-10 mt-1 w-fit rounded-sm text-sm font-medium text-blue-700 underline underline-offset-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        View details
      </Link>
    </article>
  );
}
