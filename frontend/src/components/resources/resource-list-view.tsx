"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import { categoryKeys, resourceKeys } from "@/lib/query/keys";
import {
  COST_TYPE_FILTER_OPTIONS,
  RESOURCE_LIST_PAGE_SIZE,
  VERIFICATION_STATUS_FILTER_OPTIONS,
} from "@/lib/constants/resources";
import type { ResourceListParams } from "@/lib/query/resource-list-params";
import { ResourceFilterForm } from "@/components/resources/resource-filter-form";
import { ResourceGrid } from "@/components/resources/resource-grid";
import { ResourceListSkeleton } from "@/components/resources/resource-list-skeleton";
import { Pagination } from "@/components/resources/pagination";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import { useSavedResourceStatusMap } from "@/lib/query/use-saved-resources";

export function ResourceListView({
  params,
  headerActions,
}: {
  params: ResourceListParams;
  /** Optional content rendered beside the heading (Milestone 7B's List/Map `ViewToggle`) — absent by default so existing callers/tests are unaffected. */
  headerActions?: ReactNode;
}) {
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
      costType: params.costType,
      verificationStatus: params.verificationStatus,
      openNow: params.openNow,
    }),
    queryFn: ({ signal }) =>
      getResources({
        page: params.page,
        size: RESOURCE_LIST_PAGE_SIZE,
        categoryId: params.categoryId,
        sort: params.sort,
        q: params.q,
        costType: params.costType,
        verificationStatus: params.verificationStatus,
        openNow: params.openNow,
        signal,
      }),
  });

  // One batched saved-status lookup for the whole visible page of cards —
  // never one request per card (Milestone 8A).
  const { savedIds } = useSavedResourceStatusMap(resourceQuery.data?.content.map((r) => r.id) ?? []);

  const categories = categoryQuery.data?.content ?? [];
  const selectedCategory = params.categoryId !== undefined ? categories.find((c) => c.id === params.categoryId) : undefined;
  const categoryUnresolvable = params.categoryId !== undefined && categoryQuery.isSuccess && !selectedCategory;
  const hasSearch = params.q !== undefined && params.q.length > 0;
  const hasAdditionalFilters = Boolean(params.costType || params.verificationStatus || params.openNow);
  const hasAnyFilter = hasSearch || Boolean(selectedCategory) || hasAdditionalFilters;

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-12 sm:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Browse resources</h1>
          <p className="mt-2 max-w-2xl text-base text-slate-600">
            Active community resources, browsable by category or by keyword search. Verification status
            is shown on each listing — most listings have not been independently verified yet.
          </p>
        </div>
        {headerActions}
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

      {!categoryUnresolvable && hasAnyFilter && (
        <p className="mt-3 text-sm text-slate-600">
          {hasSearch && (
            <>
              Searching for <span className="font-medium text-slate-900">&ldquo;{params.q}&rdquo;</span>
            </>
          )}
          {hasSearch && selectedCategory && " in "}
          {selectedCategory && <span className="font-medium text-slate-900">{selectedCategory.name}</span>}
          {hasAdditionalFilters && (hasSearch || selectedCategory) && " · "}
          {hasAdditionalFilters && (
            <span className="font-medium text-slate-900">{activeFilterSummary(params)}</span>
          )}
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
          <EmptyState heading={noResultsHeading(hasSearch, params.q, selectedCategory?.name, hasAdditionalFilters)}>
            {hasSearch ? (
              <p>Try a different or shorter search term, or check the spelling.</p>
            ) : hasAdditionalFilters ? (
              <p>Try removing one or more filters.</p>
            ) : (
              !selectedCategory && (
                <Link href="/" className="underline underline-offset-2">
                  Return home
                </Link>
              )
            )}
            {/* The "Reset filters" action already appears once, in the filter-summary line
                above, whenever any filter is set — repeating it here would put two
                identically-labelled links on the page. */}
          </EmptyState>
        )}

        {resourceQuery.isSuccess && resourceQuery.data.content.length > 0 && (
          <>
            <p className="mb-4 text-sm text-slate-600">
              {resultSummary(resourceQuery.data.totalElements, hasSearch, params.q, selectedCategory?.name)}
            </p>
            <ResourceGrid resources={resourceQuery.data.content} savedIds={savedIds} />
            <Pagination
              page={resourceQuery.data.page}
              totalPages={resourceQuery.data.totalPages}
              categoryId={params.categoryId}
              sort={params.sort}
              q={params.q}
              costType={params.costType}
              verificationStatus={params.verificationStatus}
              openNow={params.openNow}
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

/** Describes only the cost/verification/openNow filters — category and search already have their own summary clauses. */
function activeFilterSummary(params: ResourceListParams): string {
  const parts: string[] = [];
  if (params.costType) {
    parts.push(COST_TYPE_FILTER_OPTIONS.find((o) => o.value === params.costType)?.label ?? params.costType);
  }
  if (params.verificationStatus) {
    parts.push(
      VERIFICATION_STATUS_FILTER_OPTIONS.find((o) => o.value === params.verificationStatus)?.label ??
        params.verificationStatus,
    );
  }
  if (params.openNow) {
    parts.push("Open now");
  }
  return parts.join(", ");
}

function noResultsHeading(
  hasSearch: boolean,
  q: string | undefined,
  categoryName: string | undefined,
  hasAdditionalFilters: boolean,
): string {
  if (hasSearch && categoryName) {
    return `No active resources matched "${q}" in ${categoryName}.`;
  }
  if (hasSearch) {
    return `No active resources matched "${q}".`;
  }
  if (categoryName) {
    return `No active resources in ${categoryName} yet.`;
  }
  if (hasAdditionalFilters) {
    return "No active resources match the selected filters.";
  }
  return "No active resources are published yet.";
}
