"use client";

import { useId, useState } from "react";
import Link from "next/link";
import { useResourceSubmissionQueueQuery } from "@/lib/query/use-moderation";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { formatDate } from "@/lib/formatting/dates";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import type { ContributionStatus } from "@/lib/validation/schemas";

const PAGE_SIZE = 20;

const STATUS_OPTIONS: { value: ContributionStatus | ""; label: string }[] = [
  { value: "PENDING_REVIEW", label: "Pending review" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "WITHDRAWN", label: "Withdrawn" },
];

/**
 * The resource-submission moderation queue (Milestone 9A). Defaults to
 * PENDING_REVIEW, oldest submitted first — matching what the backend itself
 * defaults to when no filter is sent. Submitter identity is never shown
 * (see ADR-016) — a moderator reviews content, not who sent it.
 */
export function ResourceSubmissionQueue() {
  const [status, setStatus] = useState<ContributionStatus>("PENDING_REVIEW");
  const [sort, setSort] = useState<"submittedAt" | "updatedAt">("submittedAt");
  const [page, setPage] = useState(0);
  const statusId = useId();
  const sortId = useId();

  const query = useResourceSubmissionQueueQuery({ status, page, size: PAGE_SIZE, sort });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor={statusId} className="text-sm font-medium text-slate-700">
            Status
          </label>
          <select
            id={statusId}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ContributionStatus);
              setPage(0);
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={sortId} className="text-sm font-medium text-slate-700">
            Sort
          </label>
          <select
            id={sortId}
            value={sort}
            onChange={(event) => {
              setSort(event.target.value as "submittedAt" | "updatedAt");
              setPage(0);
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            <option value="submittedAt">Oldest submitted first</option>
            <option value="updatedAt">Recently updated first</option>
          </select>
        </div>
      </div>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading the queue…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load the moderation queue right now." />
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
          <EmptyState heading="Nothing here right now." />
        )}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((item) => (
                <li key={item.id}>
                  <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex flex-col gap-2">
                      <h3 className="text-base font-semibold text-slate-900">
                        <Link
                          href={`/moderation/resource-submissions/${item.id}`}
                          className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          {item.name}
                        </Link>
                      </h3>
                      <p className="text-sm text-slate-600">{item.category.name}</p>
                      <p className="text-sm text-slate-700">{item.shortDescription}</p>
                      <ContributionStatusBadge status={item.status} />
                      <p className="text-xs text-slate-500">Submitted {formatDate(item.submittedAt)}</p>
                    </div>
                  </article>
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Resource-submission queue pages" className="mt-4 flex items-center justify-center gap-4">
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
