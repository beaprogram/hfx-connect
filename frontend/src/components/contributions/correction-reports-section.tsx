"use client";

import { useState } from "react";
import Link from "next/link";
import { useCorrectionReportsQuery } from "@/lib/query/use-correction-reports";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { formatDate } from "@/lib/formatting/dates";
import { issueTypeLabel } from "@/lib/formatting/labels";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import type { CorrectionReportResponse } from "@/lib/validation/schemas";

const PAGE_SIZE = 10;

/**
 * The dashboard's My Correction Reports section (Milestone 8B). Mirrors
 * `ResourceSubmissionsSection`'s/`SavedResourcesSection`'s established
 * loading/empty/error/pagination structure.
 */
export function CorrectionReportsSection() {
  const [page, setPage] = useState(0);
  const query = useCorrectionReportsQuery(page, PAGE_SIZE);

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-slate-900">My Correction Reports</h2>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading your correction reports…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load your correction reports right now." />
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
          <EmptyState heading="You haven't reported any issues yet.">
            <Link href="/resources" className="underline underline-offset-2">
              Browse resources to report information
            </Link>
          </EmptyState>
        )}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((report) => (
                <li key={report.id}>
                  <ReportCard report={report} />
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Correction-report pages" className="mt-4 flex items-center justify-center gap-4">
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

function ReportCard({ report }: { report: CorrectionReportResponse }) {
  return (
    <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex flex-col gap-2">
        <h3 className="text-base font-semibold text-slate-900">
          <Link
            href={`/dashboard/correction-reports/${report.id}`}
            className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {report.resource.name}
          </Link>
        </h3>
        <p className="text-sm text-slate-600">{issueTypeLabel(report.issueType)}</p>
        <ContributionStatusBadge status={report.status} />
        <p className="text-xs text-slate-500">Submitted {formatDate(report.submittedAt)}</p>
      </div>
    </article>
  );
}
