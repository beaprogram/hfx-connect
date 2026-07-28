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
      q: params.q,
    }),
    queryFn: ({ signal }) =>
      getResources({
        page: params.page,
        size: RESOURCE_LIST_PAGE_SIZE,
        categoryId: params.categoryId,
        sort: params.sort,
        q: params.q,
        signal,
      }),
  });

  const categories = categoryQuery.data?.content ?? [];
  const selectedCategory = params.categoryId !== undefined ? categories.find((c) => c.id === params.categoryId) : undefined;
  const categoryUnresolvable = params.categoryId !== undefined && categoryQuery.isSuccess && !selectedCategory;
  const hasSearch = params.q !== undefined && params.q.length > 0;

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-12 sm:px-6">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Browse resources</h1>
      <p className="mt-2 max-w-2xl text-base text-slate-600">
        Active community resources, browsable by category or by keyword search. Verification status
        is shown on each listing — most listings have not been independently verified yet.
      </p>

      <div className="mt-6">
        <ResourceFilterForm categories={categories} categoryId={params.categoryId} sort={params.sort} q={params.q} />
      </div>

      {categoryUnresolvable && (
        <p className="mt-3 text-sm text-amber-700">
          The requested category couldn&apos;t be found — showing all categories instead.{" "}
          <Link href="/resources" className="underline underline-offset-2">
            Reset filters
          </Link>
        </p>
      )}

      {!categoryUnresolvable && (selectedCategory || hasSearch) && (
        <p className="mt-3 text-sm text-slate-600">
          {hasSearch && (
            <>
              Searching for <span className="font-medium text-slate-900">&ldquo;{params.q}&rdquo;</span>
            </>
          )}
          {hasSearch && selectedCategory && " in "}
          {selectedCategory && <span className="font-medium text-slate-900">{selectedCategory.name}</span>}
          {" · "}
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
          <EmptyState heading={noResultsHeading(hasSearch, params.q, selectedCategory?.name)}>
            {hasSearch ? (
              <p>Try a different or shorter search term, or check the spelling.</p>
            ) : (
              !selectedCategory && (
                <Link href="/" className="underline underline-offset-2">
                  Return home
                </Link>
              )
            )}
            {/* The "Reset filters" action already appears once, in the filter-summary line
                above, whenever selectedCategory or hasSearch is set — repeating it here
                would put two identically-labelled links on the page. */}
          </EmptyState>
        )}

        {resourceQuery.isSuccess && resourceQuery.data.content.length > 0 && (
          <>
            <p className="mb-4 text-sm text-slate-600">
              {resultSummary(resourceQuery.data.totalElements, hasSearch, params.q, selectedCategory?.name)}
            </p>
            <ResourceGrid resources={resourceQuery.data.content} />
            <Pagination
              page={resourceQuery.data.page}
              totalPages={resourceQuery.data.totalPages}
              categoryId={params.categoryId}
              sort={params.sort}
              q={params.q}
            />
          </>
        )}
      </div>
    </div>
  );
}

/**
 * An honest summary built only from real API information (total count,
 * the caller's own search phrase, the resolved category name) — never a
 * relevance claim or a fabricated metric. See ADR-010's "No Relevance
 * Ranking" section and the milestone brief's explicit requirement that
 * results not be described as "most relevant."
 */
function resultSummary(total: number, hasSearch: boolean, q: string | undefined, categoryName: string | undefined): string {
  const noun = `resource${total === 1 ? "" : "s"}`;
  if (hasSearch && categoryName) {
    return `${total} ${noun} matching "${q}" in ${categoryName}`;
  }
  if (hasSearch) {
    return `${total} ${noun} matching "${q}"`;
  }
  if (categoryName) {
    return `${total} ${noun} in ${categoryName}`;
  }
  return `${total} ${noun} found`;
}

function noResultsHeading(hasSearch: boolean, q: string | undefined, categoryName: string | undefined): string {
  if (hasSearch && categoryName) {
    return `No active resources matched "${q}" in ${categoryName}.`;
  }
  if (hasSearch) {
    return `No active resources matched "${q}".`;
  }
  if (categoryName) {
    return `No active resources in ${categoryName} yet.`;
  }
  return "No active resources are published yet.";
}
