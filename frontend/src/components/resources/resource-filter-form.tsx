"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef } from "react";
import {
  COST_TYPE_FILTER_OPTIONS,
  RESOURCE_SORT_OPTIONS,
  VERIFICATION_STATUS_FILTER_OPTIONS,
  type CostTypeFilterValue,
  type ResourceSort,
  type VerificationStatusFilterValue,
} from "@/lib/constants/resources";
import { buildResourcesHref, type ResourceListParams } from "@/lib/query/resource-list-params";
import type { CategoryResponse } from "@/lib/validation/schemas";

/**
 * A plain `<form method="get">` — works with JavaScript disabled (the
 * "Apply" button submits it and the browser navigates normally). With
 * JavaScript enabled, changing a select calls `router.push` directly for an
 * instant client-side transition instead of waiting for a full navigation;
 * the visible submit button is left in place as a working fallback either
 * way. See docs/wireframes/resource-list.md.
 *
 * <p>The keyword search field (Milestone 6A) is a named input in this same
 * form — submitting it (Enter, or the "Apply" button) naturally carries
 * `categoryId`/`sort` along via the browser's own GET-form serialization,
 * with or without JavaScript, and always resets to page 1 (page is never a
 * field in this form, so it is simply absent from the resulting URL). It
 * deliberately does not auto-submit on every keystroke — see ADR-010 and
 * the milestone brief's explicit "submit-based search is acceptable and
 * often clearer" guidance.
 */
export function ResourceFilterForm({
  categories,
  categoryId,
  sort,
  q,
  costType,
  verificationStatus,
  openNow,
  showSort = true,
}: {
  categories: CategoryResponse[];
  categoryId?: number;
  sort: ResourceSort;
  q?: string;
  costType?: CostTypeFilterValue;
  verificationStatus?: VerificationStatusFilterValue;
  openNow?: boolean;
  /** Hidden in map/nearby mode (Milestone 7B) — nearby results are always ordered by distance; "sort" has no effect there. Defaults to true so existing callers/tests are unaffected. */
  showSort?: boolean;
}) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);

  const hasAnyFilter = Boolean(categoryId || (q && q.length > 0) || costType || verificationStatus || openNow);

  function submitViaRouter() {
    const form = formRef.current;
    if (!form) return;
    const data = new FormData(form);
    const selectedCategoryId = data.get("categoryId");
    const selectedSort = data.get("sort");
    const rawQ = data.get("q");
    const selectedQ = typeof rawQ === "string" ? rawQ.trim().replace(/\s+/g, " ") : "";
    const selectedCostType = data.get("costType");
    const selectedVerificationStatus = data.get("verificationStatus");
    const selectedOpenNow = data.get("openNow") === "true";

    const params: Partial<ResourceListParams> = {
      categoryId: selectedCategoryId ? Number(selectedCategoryId) : undefined,
      sort: (selectedSort as ResourceSort) || undefined,
      q: selectedQ || undefined,
      costType: (selectedCostType as CostTypeFilterValue) || undefined,
      verificationStatus: (selectedVerificationStatus as VerificationStatusFilterValue) || undefined,
      openNow: selectedOpenNow || undefined,
    };
    router.push(buildResourcesHref(params));
  }

  return (
    <form
      ref={formRef}
      method="get"
      action="/resources"
      className="flex flex-wrap items-end gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        submitViaRouter();
      }}
    >
      <div className="flex flex-col gap-1">
        <label htmlFor="q" className="text-sm font-medium text-slate-700">
          Search
        </label>
        <div className="flex items-center gap-2">
          <input
            id="q"
            name="q"
            type="search"
            defaultValue={q ?? ""}
            placeholder="Resource name, description, or address"
            autoComplete="off"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          />
          {q && (
            <Link
              href={buildResourcesHref({ categoryId, sort, costType, verificationStatus, openNow })}
              className="whitespace-nowrap rounded-sm text-sm font-medium text-slate-600 underline underline-offset-2 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
              aria-label="Clear search"
            >
              Clear search
            </Link>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="categoryId" className="text-sm font-medium text-slate-700">
          Category
        </label>
        <select
          id="categoryId"
          name="categoryId"
          defaultValue={categoryId ?? ""}
          onChange={submitViaRouter}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          <option value="">All categories</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
      </div>

      {showSort && (
        <div className="flex flex-col gap-1">
          <label htmlFor="sort" className="text-sm font-medium text-slate-700">
            Sort
          </label>
          <select
            id="sort"
            name="sort"
            defaultValue={sort}
            onChange={submitViaRouter}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {RESOURCE_SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="flex flex-col gap-1">
        <label htmlFor="costType" className="text-sm font-medium text-slate-700">
          Cost
        </label>
        <select
          id="costType"
          name="costType"
          defaultValue={costType ?? ""}
          onChange={submitViaRouter}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          <option value="">All cost types</option>
          {COST_TYPE_FILTER_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="verificationStatus" className="text-sm font-medium text-slate-700">
          Verification
        </label>
        <select
          id="verificationStatus"
          name="verificationStatus"
          defaultValue={verificationStatus ?? ""}
          onChange={submitViaRouter}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          <option value="">All resources</option>
          {VERIFICATION_STATUS_FILTER_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1">
        <span className="text-sm font-medium text-slate-700">Availability</span>
        <label className="flex items-center gap-2 py-2 text-sm text-slate-900">
          <input
            id="openNow"
            name="openNow"
            type="checkbox"
            value="true"
            defaultChecked={openNow ?? false}
            onChange={submitViaRouter}
            className="h-4 w-4 rounded-sm border-slate-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          />
          Open now
        </label>
      </div>

      <button
        type="submit"
        className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Apply
      </button>

      {hasAnyFilter && (
        <Link
          href="/resources"
          className="whitespace-nowrap rounded-sm text-sm font-medium text-slate-600 underline underline-offset-2 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Reset all filters
        </Link>
      )}
    </form>
  );
}
