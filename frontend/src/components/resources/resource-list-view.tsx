"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import { categoryKeys, resourceKeys } from "@/lib/query/keys";
import { RESOURCE_LIST_PAGE_SIZE } from "@/lib/constants/resources";
import type { ResourceListParams } from "@/lib/query/resource-list-params";
import { ResourceFilterForm } from "@/components/resources/resource-filter-form";
import { ResourceGrid } from "@/components/resources/resource-grid";
import { ResourceListSkeleton } from "@/components/resources/resource-list-skeleton";
import { Pagination } from "@/components/resources/pagination";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";

export function ResourceListView({ params }: { params: ResourceListParams }) {
  const categoryQuery = useQuery({
    queryKey: categoryKeys.list({ active: true }),
    queryFn: ({ signal }) => getCategories({ active: true, size: 50, signal }),
  });

  const resourceQuery = useQuery({
    queryKey: resourceKeys.list({
      page: params.page,
      size: RESOURCE_LIST_PAGE_SIZE,
      categoryId: params.categoryId,
      sort: params.sort,
    }),
    queryFn: ({ signal }) =>
      getResources({
        page: params.page,
        size: RESOURCE_LIST_PAGE_SIZE,
        categoryId: params.categoryId,
        sort: params.sort,
        signal,
      }),
  });

  const categories = categoryQuery.data?.content ?? [];
  const selectedCategory = params.categoryId !== undefined ? categories.find((c) => c.id === params.categoryId) : undefined;
  const categoryUnresolvable = params.categoryId !== undefined && categoryQuery.isSuccess && !selectedCategory;

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-12 sm:px-6">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Browse resources</h1>
      <p className="mt-2 max-w-2xl text-base text-slate-600">
        Active community resources, browsable by category. Verification status is shown on each
        listing — most listings have not been independently verified yet.
      </p>

      <div className="mt-6">
        <ResourceFilterForm categories={categories} categoryId={params.categoryId} sort={params.sort} />
      </div>

      {categoryUnresolvable && (
        <p className="mt-3 text-sm text-amber-700">
          The requested category couldn&apos;t be found — showing all categories instead.{" "}
          <Link href="/resources" className="underline underline-offset-2">
            Reset filters
          </Link>
        </p>
      )}

      {!categoryUnresolvable && selectedCategory && (
        <p className="mt-3 text-sm text-slate-600">
          Filtering by <span className="font-medium text-slate-900">{selectedCategory.name}</span> ·{" "}
          <Link href="/resources" className="underline underline-offset-2">
            Reset filters
          </Link>
        </p>
      )}

      <div className="mt-6" aria-live="polite">
        {resourceQuery.isPending && <ResourceListSkeleton />}

        {resourceQuery.isError && (
          <InlineError message="Resources couldn't be loaded right now. Please try again in a moment." />
        )}

        {resourceQuery.isSuccess && resourceQuery.data.content.length === 0 && (
          <EmptyState
            heading={
              selectedCategory
                ? `No active resources in ${selectedCategory.name} yet.`
                : "No active resources are published yet."
            }
          >
            {/* The "Reset filters" action already appears once, in the filter-summary line
                above, whenever selectedCategory is set — repeating it here would put two
                identically-labelled links on the page. */}
            {!selectedCategory && (
              <Link href="/" className="underline underline-offset-2">
                Return home
              </Link>
            )}
          </EmptyState>
        )}

        {resourceQuery.isSuccess && resourceQuery.data.content.length > 0 && (
          <>
            <p className="mb-4 text-sm text-slate-600">
              {resourceQuery.data.totalElements} resource{resourceQuery.data.totalElements === 1 ? "" : "s"} found
            </p>
            <ResourceGrid resources={resourceQuery.data.content} />
            <Pagination
              page={resourceQuery.data.page}
              totalPages={resourceQuery.data.totalPages}
              categoryId={params.categoryId}
              sort={params.sort}
            />
          </>
        )}
      </div>
    </div>
  );
}
