"use client";

import { useState } from "react";
import Link from "next/link";
import { useResourceSubmissionsQuery } from "@/lib/query/use-resource-submissions";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { formatDate } from "@/lib/formatting/dates";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import type { ResourceSubmissionResponse } from "@/lib/validation/schemas";

const PAGE_SIZE = 10;

/**
 * The dashboard's My Resource Submissions section (Milestone 8B). Mirrors
 * `SavedResourcesSection`'s established loading/empty/error/pagination
 * structure (Milestone 8A). No fabricated review-time estimates or counts —
 * every element reflects a real API response.
 */
export function ResourceSubmissionsSection() {
  const [page, setPage] = useState(0);
  const query = useResourceSubmissionsQuery(page, PAGE_SIZE);

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-slate-900">My Resource Submissions</h2>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading your submissions…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load your submissions right now." />
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
          <EmptyState heading="You haven't proposed any resources yet.">
            <Link href="/submit-resource" className="underline underline-offset-2">
              Propose a resource
            </Link>
          </EmptyState>
        )}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((submission) => (
                <li key={submission.id}>
                  <SubmissionCard submission={submission} />
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Resource-submission pages" className="mt-4 flex items-center justify-center gap-4">
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
    </section>
  );
}

function SubmissionCard({ submission }: { submission: ResourceSubmissionResponse }) {
  return (
    <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex flex-col gap-2">
        <h3 className="text-base font-semibold text-slate-900">
          <Link
            href={`/dashboard/submissions/${submission.id}`}
            className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {submission.name}
          </Link>
        </h3>
        <p className="text-sm text-slate-600">{submission.category.name}</p>
        <ContributionStatusBadge status={submission.status} />
        <p className="text-xs text-slate-500">Submitted {formatDate(submission.submittedAt)}</p>
      </div>
    </article>
  );
}
