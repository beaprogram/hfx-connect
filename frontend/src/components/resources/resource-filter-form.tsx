"use client";

import { useRouter } from "next/navigation";
import { useRef } from "react";
import { RESOURCE_SORT_OPTIONS, type ResourceSort } from "@/lib/constants/resources";
import type { CategoryResponse } from "@/lib/validation/schemas";

/**
 * A plain `<form method="get">` — works with JavaScript disabled (the
 * "Apply" button submits it and the browser navigates normally). With
 * JavaScript enabled, changing a select calls `router.push` directly for an
 * instant client-side transition instead of waiting for a full navigation;
 * the visible submit button is left in place as a working fallback either
 * way. See docs/wireframes/resource-list.md.
 */
export function ResourceFilterForm({
  categories,
  categoryId,
  sort,
}: {
  categories: CategoryResponse[];
  categoryId?: number;
  sort: ResourceSort;
}) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);

  function submitViaRouter() {
    const form = formRef.current;
    if (!form) return;
    const data = new FormData(form);
    const query = new URLSearchParams();
    const selectedCategoryId = data.get("categoryId");
    const selectedSort = data.get("sort");
    if (selectedCategoryId) query.set("categoryId", String(selectedCategoryId));
    if (selectedSort && selectedSort !== "name") query.set("sort", String(selectedSort));
    const queryString = query.toString();
    router.push(queryString ? `/resources?${queryString}` : "/resources");
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

      <button
        type="submit"
        className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Apply
      </button>
    </form>
  );
}
