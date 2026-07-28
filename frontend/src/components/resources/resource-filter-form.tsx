"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef } from "react";
import { RESOURCE_SORT_OPTIONS, type ResourceSort } from "@/lib/constants/resources";
import { buildResourcesHref } from "@/lib/query/resource-list-params";
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
}: {
  categories: CategoryResponse[];
  categoryId?: number;
  sort: ResourceSort;
  q?: string;
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
    const rawQ = data.get("q");
    const selectedQ = typeof rawQ === "string" ? rawQ.trim().replace(/\s+/g, " ") : "";
    if (selectedCategoryId) query.set("categoryId", String(selectedCategoryId));
    if (selectedSort && selectedSort !== "name") query.set("sort", String(selectedSort));
    if (selectedQ) query.set("q", selectedQ);
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
              href={buildResourcesHref({ categoryId, sort })}
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
