"use client";

import { Fragment } from "react";
import Link from "next/link";
import { useCorrectionReportQuery, useWithdrawCorrectionReportMutation } from "@/lib/query/use-correction-reports";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { issueTypeLabel } from "@/lib/formatting/labels";
import { formatDate } from "@/lib/formatting/dates";
import { InlineError } from "@/components/feedback/inline-error";

const PROPOSED_FIELD_LABELS = {
  proposedName: "Proposed name",
  proposedDescription: "Proposed description",
  proposedAddressLine1: "Proposed address line 1",
  proposedAddressLine2: "Proposed address line 2",
  proposedCity: "Proposed city",
  proposedProvince: "Proposed province",
  proposedPostalCode: "Proposed postal code",
  proposedPhone: "Proposed phone",
  proposedEmail: "Proposed email",
  proposedWebsiteUrl: "Proposed website",
  proposedCostDetails: "Proposed cost details",
  proposedEligibility: "Proposed eligibility",
} as const;

/**
 * One owned correction report's detail (Milestone 8B). A report that
 * doesn't exist or belongs to a different account renders the exact same
 * generic error as any other failure.
 */
export function CorrectionReportDetail({ reportId }: { reportId: string }) {
  const query = useCorrectionReportQuery(reportId);
  const withdrawMutation = useWithdrawCorrectionReportMutation();

  if (query.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading your report…
      </p>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div className="mx-auto w-full max-w-2xl px-4 py-12">
        <InlineError message="We couldn't find that report." />
        <Link href="/dashboard" className="mt-4 inline-block text-sm text-blue-700 underline underline-offset-2">
          Back to dashboard
        </Link>
      </div>
    );
  }

  const report = query.data;
  const proposedEntries = Object.entries(PROPOSED_FIELD_LABELS).filter(
    ([key]) => report[key as keyof typeof PROPOSED_FIELD_LABELS],
  );

  return (
    <article className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-12">
      <div>
        <Link href="/dashboard" className="text-sm text-slate-600 underline underline-offset-2 hover:text-slate-800">
          ← Back to dashboard
        </Link>
        <h1 className="mt-3 text-2xl font-semibold text-slate-900">
          {report.resource.resourceId ? (
            <Link href={`/resources/${report.resource.slug}`} className="underline-offset-2 hover:underline">
              {report.resource.name}
            </Link>
          ) : (
            report.resource.name
          )}
        </h1>
        {!report.resource.resourceId && (
          <p className="mt-1 text-sm text-slate-500">This resource no longer exists.</p>
        )}
        <div className="mt-2 flex items-center gap-2">
          <ContributionStatusBadge status={report.status} />
          <span className="text-sm text-slate-500">Submitted {formatDate(report.submittedAt)}</span>
        </div>
      </div>

      <p className="text-sm text-slate-700" role="status">
        This report has not changed the resource. It is visible only to you while it awaits review.
      </p>

      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="font-medium text-slate-700">Issue</dt>
        <dd className="text-slate-900">{issueTypeLabel(report.issueType)}</dd>
        <dt className="font-medium text-slate-700">Explanation</dt>
        <dd className="text-slate-900">{report.explanation}</dd>
        {proposedEntries.map(([key, label]) => (
          <Fragment key={key}>
            <dt className="font-medium text-slate-700">{label}</dt>
            <dd className="text-slate-900">{report[key as keyof typeof PROPOSED_FIELD_LABELS]}</dd>
          </Fragment>
        ))}
        {report.proposedCostType && (
          <>
            <dt className="font-medium text-slate-700">Proposed cost</dt>
            <dd className="text-slate-900">{report.proposedCostType}</dd>
          </>
        )}
        {report.withdrawnAt && (
          <>
            <dt className="font-medium text-slate-700">Withdrawn</dt>
            <dd className="text-slate-900">{formatDate(report.withdrawnAt)}</dd>
          </>
        )}
      </dl>

      {report.status === "PENDING_REVIEW" && (
        <div>
          <button
            type="button"
            onClick={() => withdrawMutation.mutate(report.id)}
            disabled={withdrawMutation.isPending}
            className="w-fit rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {withdrawMutation.isPending ? "Withdrawing…" : "Withdraw report"}
          </button>
          {withdrawMutation.isError && (
            <p role="alert" className="mt-2 text-sm text-red-700">
              We couldn&apos;t withdraw this report. Please try again.
            </p>
          )}
        </div>
      )}
    </article>
  );
}
