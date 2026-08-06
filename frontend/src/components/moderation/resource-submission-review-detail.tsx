"use client";

import Link from "next/link";
import {
  useResourceSubmissionModerationDetailQuery,
  useApproveResourceSubmissionMutation,
  useRejectResourceSubmissionMutation,
} from "@/lib/query/use-moderation";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { ModerationDecisionForm } from "@/components/moderation/moderation-decision-form";
import { ResourceSubmissionAuditHistory } from "@/components/moderation/moderation-audit-history";
import { formatDate } from "@/lib/formatting/dates";
import { costTypeLabel } from "@/lib/formatting/labels";
import { InlineError } from "@/components/feedback/inline-error";

const linkClassName =
  "font-medium text-blue-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

export function ResourceSubmissionReviewDetail({ submissionId }: { submissionId: string }) {
  const query = useResourceSubmissionModerationDetailQuery(submissionId);
  const approveMutation = useApproveResourceSubmissionMutation();
  const rejectMutation = useRejectResourceSubmissionMutation();

  if (query.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading submission…
      </p>
    );
  }

  if (query.isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <InlineError message="We couldn't load this submission right now." />
      </div>
    );
  }

  const submission = query.data;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{submission.name}</h1>
        <p className="mt-1 text-sm text-slate-600">{submission.category.name}</p>
        <div className="mt-2">
          <ContributionStatusBadge status={submission.status} />
        </div>
        <p className="mt-1 text-xs text-slate-500">Submitted {formatDate(submission.submittedAt)}</p>
      </div>

      <dl className="grid grid-cols-1 gap-x-6 gap-y-3 rounded-lg border border-slate-200 p-4 text-sm sm:grid-cols-2">
        <Detail label="Short description" value={submission.shortDescription} />
        {submission.fullDescription && <Detail label="Full description" value={submission.fullDescription} />}
        <Detail label="Address" value={[submission.addressLine1, submission.addressLine2].filter(Boolean).join(", ")} />
        <Detail label="City" value={submission.city} />
        <Detail label="Province" value={submission.province} />
        <Detail label="Postal code" value={submission.postalCode} />
        {submission.phone && <Detail label="Phone" value={submission.phone} />}
        {submission.email && <Detail label="Email" value={submission.email} />}
        {submission.websiteUrl && <Detail label="Website" value={submission.websiteUrl} />}
        <Detail label="Cost" value={costTypeLabel(submission.costType)} />
        {submission.eligibilityInformation && <Detail label="Eligibility" value={submission.eligibilityInformation} />}
        {submission.accessibilityInformation && (
          <Detail label="Accessibility" value={submission.accessibilityInformation} />
        )}
      </dl>

      {submission.status === "APPROVED" && submission.resultingResource && (
        <p className="text-sm text-slate-700">
          Published as{" "}
          <Link href={`/resources/${submission.resultingResource.slug}`} className={linkClassName}>
            {submission.resultingResource.name}
          </Link>
          . {submission.reviewReason && <>Reason: &ldquo;{submission.reviewReason}&rdquo;</>}
        </p>
      )}
      {submission.status === "REJECTED" && submission.reviewReason && (
        <p className="text-sm text-slate-700">Rejected. Reason: &ldquo;{submission.reviewReason}&rdquo;</p>
      )}

      {submission.status === "PENDING_REVIEW" && (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Approve</h2>
            <p className="text-sm text-slate-600">Publishes this as a real, verified public resource.</p>
            <ModerationDecisionForm
              actionLabel="Approve"
              isPending={approveMutation.isPending}
              onSubmit={async (reason) => {
                await approveMutation.mutateAsync({ submissionId, reason });
              }}
            />
          </section>
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Reject</h2>
            <p className="text-sm text-slate-600">Never creates a public resource.</p>
            <ModerationDecisionForm
              actionLabel="Reject"
              isPending={rejectMutation.isPending}
              onSubmit={async (reason) => {
                await rejectMutation.mutateAsync({ submissionId, reason });
              }}
            />
          </section>
        </div>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-base font-semibold text-slate-900">Audit history</h2>
        <ResourceSubmissionAuditHistory submissionId={submissionId} />
      </section>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-medium text-slate-900">{label}</dt>
      <dd className="text-slate-700">{value}</dd>
    </div>
  );
}
