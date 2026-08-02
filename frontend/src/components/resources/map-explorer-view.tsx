"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { getCategories } from "@/lib/api/categories";
import { categoryKeys } from "@/lib/query/keys";
import { ResourceFilterForm } from "@/components/resources/resource-filter-form";
import { ViewToggle } from "@/components/resources/view-toggle";
import { NearbyMapView } from "@/components/resources/nearby-map-view";
import type { ResourceListParams } from "@/lib/query/resource-list-params";

/**
 * The Map-mode counterpart of {@link ResourceListView} — same header/filter
 * pattern, but drives {@link NearbyMapView} (a geospatial nearby search)
 * instead of the plain paginated list. Kept as a fully separate component
 * from `ResourceListView` rather than a shared/branching one, so the
 * existing, already-tested list experience is never put at risk by map-mode
 * changes — see ADR-013's "Two Top-Level View Components, Not One Branching
 * Component".
 */
export function MapExplorerView({ params }: { params: ResourceListParams }) {
  const categoryQuery = useQuery({
    queryKey: categoryKeys.list({ active: true }),
    queryFn: ({ signal }) => getCategories({ active: true, size: 50, signal }),
  });

  const categories = categoryQuery.data?.content ?? [];
  const selectedCategory = params.categoryId !== undefined ? categories.find((c) => c.id === params.categoryId) : undefined;
  const categoryUnresolvable = params.categoryId !== undefined && categoryQuery.isSuccess && !selectedCategory;

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-12 sm:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Browse resources</h1>
          <p className="mt-2 max-w-2xl text-base text-slate-600">
            Active community resources near a map location you choose. Verification status is shown
            on each listing — most listings have not been independently verified yet.
          </p>
        </div>
        <ViewToggle />
      </div>

      <div className="mt-6">
        <ResourceFilterForm
          categories={categories}
          categoryId={params.categoryId}
          sort={params.sort}
          q={params.q}
          costType={params.costType}
          verificationStatus={params.verificationStatus}
          openNow={params.openNow}
          showSort={false}
        />
      </div>

      {categoryUnresolvable && (
        <p className="mt-3 text-sm text-amber-700">
          The requested category couldn&apos;t be found — showing all categories instead.{" "}
          <Link href="/resources" className="underline underline-offset-2">
            Reset filters
          </Link>
        </p>
      )}

      <div className="mt-6">
        <NearbyMapView
          filters={{
            q: params.q,
            categoryId: selectedCategory ? params.categoryId : undefined,
            costType: params.costType,
            verificationStatus: params.verificationStatus,
            openNow: params.openNow,
          }}
        />
      </div>
    </div>
  );
}
