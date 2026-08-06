"use client";

import Link from "next/link";
import {
  useCorrectionReportModerationDetailQuery,
  useApproveCorrectionReportMutation,
  useRejectCorrectionReportMutation,
} from "@/lib/query/use-moderation";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { ModerationDecisionForm } from "@/components/moderation/moderation-decision-form";
import { CorrectionApprovalForm } from "@/components/moderation/correction-approval-form";
import { CorrectionReportAuditHistory } from "@/components/moderation/moderation-audit-history";
import { formatDate } from "@/lib/formatting/dates";
import { issueTypeLabel } from "@/lib/formatting/labels";
import { InlineError } from "@/components/feedback/inline-error";

const linkClassName =
  "font-medium text-blue-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

export function CorrectionReportReviewDetail({ reportId }: { reportId: string }) {
  const query = useCorrectionReportModerationDetailQuery(reportId);
  const approveMutation = useApproveCorrectionReportMutation();
  const rejectMutation = useRejectCorrectionReportMutation();

  if (query.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading report…
      </p>
    );
  }

  if (query.isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <InlineError message="We couldn't load this report right now." />
      </div>
    );
  }

  const report = query.data;
  const current = report.currentResource;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{report.resource.name}</h1>
        <p className="mt-1 text-sm text-slate-600">{issueTypeLabel(report.issueType)}</p>
        <div className="mt-2">
          <ContributionStatusBadge status={report.status} />
        </div>
        <p className="mt-1 text-xs text-slate-500">Submitted {formatDate(report.submittedAt)}</p>
        {report.resource.resourceId && (
          <Link href={`/resources/${report.resource.slug}`} className={`${linkClassName} mt-1 inline-block text-sm`}>
            View current public listing
          </Link>
        )}
        {!report.resource.resourceId && (
          <p className="mt-1 text-sm text-slate-600">The target resource has since been deleted.</p>
        )}
      </div>

      <section>
        <h2 className="text-base font-semibold text-slate-900">Explanation</h2>
        <p className="mt-1 text-sm text-slate-700">{report.explanation}</p>
      </section>

      {current && (
        <section className="overflow-x-auto">
          <h2 className="text-base font-semibold text-slate-900">Current vs. proposed</h2>
          <table className="mt-2 w-full min-w-[28rem] table-auto border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left">
                <th scope="col" className="py-2 pr-4 font-medium text-slate-700">
                  Field
                </th>
                <th scope="col" className="py-2 pr-4 font-medium text-slate-700">
                  Current
                </th>
                <th scope="col" className="py-2 font-medium text-slate-700">
                  Proposed
                </th>
              </tr>
            </thead>
            <tbody>
              <ComparisonRow label="Name" current={current.name} proposed={report.proposedName} />
              <ComparisonRow label="Description" current={current.description} proposed={report.proposedDescription} />
              <ComparisonRow label="Address" current={current.addressLine1} proposed={report.proposedAddressLine1} />
              <ComparisonRow label="City" current={current.city} proposed={report.proposedCity} />
              <ComparisonRow label="Province" current={current.province} proposed={report.proposedProvince} />
              <ComparisonRow label="Postal code" current={current.postalCode} proposed={report.proposedPostalCode} />
              <ComparisonRow label="Phone" current={current.phone ?? ""} proposed={report.proposedPhone} />
              <ComparisonRow label="Email" current={current.email ?? ""} proposed={report.proposedEmail} />
              <ComparisonRow label="Website" current={current.websiteUrl ?? ""} proposed={report.proposedWebsiteUrl} />
            </tbody>
          </table>
        </section>
      )}

      {report.status === "APPROVED" && (
        <p className="text-sm text-slate-700">
          Approved.{" "}
          {report.appliedToResourceAt
            ? "The reported information was reviewed and applied."
            : "The reported information was reviewed."}{" "}
          {report.reviewReason && <>Reason: &ldquo;{report.reviewReason}&rdquo;</>}
        </p>
      )}
      {report.status === "REJECTED" && report.reviewReason && (
        <p className="text-sm text-slate-700">Rejected. Reason: &ldquo;{report.reviewReason}&rdquo;</p>
      )}

      {report.status === "PENDING_REVIEW" && (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Approve</h2>
            <CorrectionApprovalForm
              issueType={report.issueType}
              isPending={approveMutation.isPending}
              onSubmit={async (input) => {
                await approveMutation.mutateAsync({ reportId, input });
              }}
            />
          </section>
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Reject</h2>
            <p className="text-sm text-slate-600">Never modifies the target resource.</p>
            <ModerationDecisionForm
              actionLabel="Reject"
              isPending={rejectMutation.isPending}
              onSubmit={async (reason) => {
                await rejectMutation.mutateAsync({ reportId, reason });
              }}
            />
          </section>
        </div>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-base font-semibold text-slate-900">Audit history</h2>
        <CorrectionReportAuditHistory reportId={reportId} />
      </section>
    </div>
  );
}

function ComparisonRow({ label, current, proposed }: { label: string; current: string; proposed?: string | null }) {
  if (!proposed) {
    return null;
  }
  return (
    <tr className="border-b border-slate-100">
      <th scope="row" className="py-2 pr-4 font-normal text-slate-700">
        {label}
      </th>
      <td className="py-2 pr-4 text-slate-600">{current || "—"}</td>
      <td className="py-2 font-medium text-slate-900">{proposed}</td>
    </tr>
  );
}
