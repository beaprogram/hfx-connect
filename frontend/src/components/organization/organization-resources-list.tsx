"use client";

import { useState } from "react";
import Link from "next/link";
import { useOrganizationResourcesQuery } from "@/lib/query/use-organization";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import { Badge } from "@/components/feedback/badge";
import { verificationStatusLabel } from "@/lib/formatting/labels";
import { formatDate } from "@/lib/formatting/dates";

const PAGE_SIZE = 20;

/** The `/organization/resources` page (Milestone 10A) — read-only; no listing-editing UI yet (see the milestone doc's "Known Limitations"). */
export function OrganizationResourcesList() {
  const [page, setPage] = useState(0);
  const query = useOrganizationResourcesQuery(page, PAGE_SIZE);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <OrganizationNav />
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Owned resources</h1>
        <p className="mt-2 text-sm text-slate-600">
          Listing management will be expanded in the next organization feature stage.
        </p>
      </div>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading your resources…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load your resources right now." />
            <button
              type="button"
              onClick={() => query.refetch()}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            >
              Try again
            </button>
          </div>
        )}

        {query.isSuccess && query.data.content.length === 0 && (
          <EmptyState heading="No resources owned yet.">
            Once an administrator approves a claim, the resource will appear here.
          </EmptyState>
        )}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((resource) => (
                <li key={resource.id}>
                  <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex flex-col gap-2">
                      <h3 className="text-base font-semibold text-slate-900">
                        <Link
                          href={`/resources/${resource.slug}`}
                          className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          {resource.name}
                        </Link>
                      </h3>
                      <p className="text-sm text-slate-600">{resource.category.name}</p>
                      <div className="flex flex-wrap gap-2">
                        <Badge tone={resource.active ? "positive" : "neutral"}>{resource.active ? "Active" : "Inactive"}</Badge>
                        <Badge tone={resource.verificationStatus === "VERIFIED" ? "positive" : "neutral"}>
                          {verificationStatusLabel(resource.verificationStatus)}
                        </Badge>
                      </div>
                      {resource.lastVerifiedAt && (
                        <p className="text-xs text-slate-500">Last verified {formatDate(resource.lastVerifiedAt)}</p>
                      )}
                    </div>
                  </article>
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Owned resources pages" className="mt-4 flex items-center justify-center gap-4">
                <button
                  type="button"
                  onClick={() => setPage((current) => current - 1)}
                  disabled={page === 0}
                  className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400 disabled:hover:bg-transparent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                >
                  Previous page
                </button>
                <span className="text-sm text-slate-600">
                  Page {page + 1} of {query.data.totalPages}
                </span>
                <button
                  type="button"
                  onClick={() => setPage((current) => current + 1)}
                  disabled={page >= query.data.totalPages - 1}
                  className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400 disabled:hover:bg-transparent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                >
                  Next page
                </button>
              </nav>
            )}
          </>
        )}
      </div>
    </div>
  );
}
