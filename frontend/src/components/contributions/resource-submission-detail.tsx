"use client";

import Link from "next/link";
import { useResourceSubmissionQuery, useWithdrawResourceSubmissionMutation } from "@/lib/query/use-resource-submissions";
import { ContributionStatusBadge } from "@/components/contributions/contribution-status-badge";
import { costTypeLabel } from "@/lib/formatting/labels";
import { formatDate } from "@/lib/formatting/dates";
import { InlineError } from "@/components/feedback/inline-error";

/**
 * One owned resource submission's detail (Milestone 8B). A submission that
 * doesn't exist or belongs to a different account renders the exact same
 * generic error as any other failure — the backend's 404 never
 * distinguishes the two, and neither does this page.
 */
export function ResourceSubmissionDetail({ submissionId }: { submissionId: string }) {
  const query = useResourceSubmissionQuery(submissionId);
  const withdrawMutation = useWithdrawResourceSubmissionMutation();

  if (query.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading your submission…
      </p>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div className="mx-auto w-full max-w-2xl px-4 py-12">
        <InlineError message="We couldn't find that submission." />
        <Link href="/dashboard" className="mt-4 inline-block text-sm text-blue-700 underline underline-offset-2">
          Back to dashboard
        </Link>
      </div>
    );
  }

  const submission = query.data;

  return (
    <article className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-12">
      <div>
        <Link href="/dashboard" className="text-sm text-slate-600 underline underline-offset-2 hover:text-slate-800">
          ← Back to dashboard
        </Link>
        <h1 className="mt-3 text-2xl font-semibold text-slate-900">{submission.name}</h1>
        <div className="mt-2 flex items-center gap-2">
          <ContributionStatusBadge status={submission.status} />
          <span className="text-sm text-slate-500">Submitted {formatDate(submission.submittedAt)}</span>
        </div>
      </div>

      <p className="text-sm text-slate-700" role="status">
        This submission is not a public resource. It is visible only to you while it awaits review.
      </p>

      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="font-medium text-slate-700">Category</dt>
        <dd className="text-slate-900">{submission.category.name}</dd>
        <dt className="font-medium text-slate-700">Short description</dt>
        <dd className="text-slate-900">{submission.shortDescription}</dd>
        {submission.fullDescription && (
          <>
            <dt className="font-medium text-slate-700">Full description</dt>
            <dd className="text-slate-900">{submission.fullDescription}</dd>
          </>
        )}
        <dt className="font-medium text-slate-700">Address</dt>
        <dd className="text-slate-900">
          {submission.addressLine1}
          {submission.addressLine2 ? `, ${submission.addressLine2}` : ""}, {submission.city}, {submission.province}{" "}
          {submission.postalCode}
        </dd>
        {submission.phone && (
          <>
            <dt className="font-medium text-slate-700">Phone</dt>
            <dd className="text-slate-900">{submission.phone}</dd>
          </>
        )}
        {submission.email && (
          <>
            <dt className="font-medium text-slate-700">Email</dt>
            <dd className="text-slate-900">{submission.email}</dd>
          </>
        )}
        {submission.websiteUrl && (
          <>
            <dt className="font-medium text-slate-700">Website</dt>
            <dd className="text-slate-900">{submission.websiteUrl}</dd>
          </>
        )}
        <dt className="font-medium text-slate-700">Cost</dt>
        <dd className="text-slate-900">{costTypeLabel(submission.costType)}</dd>
        {submission.eligibilityInformation && (
          <>
            <dt className="font-medium text-slate-700">Eligibility</dt>
            <dd className="text-slate-900">{submission.eligibilityInformation}</dd>
          </>
        )}
        {submission.accessibilityInformation && (
          <>
            <dt className="font-medium text-slate-700">Accessibility</dt>
            <dd className="text-slate-900">{submission.accessibilityInformation}</dd>
          </>
        )}
        {submission.withdrawnAt && (
          <>
            <dt className="font-medium text-slate-700">Withdrawn</dt>
            <dd className="text-slate-900">{formatDate(submission.withdrawnAt)}</dd>
          </>
        )}
      </dl>

      {submission.status === "PENDING_REVIEW" && (
        <div>
          <button
            type="button"
            onClick={() => withdrawMutation.mutate(submission.id)}
            disabled={withdrawMutation.isPending}
            className="w-fit rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {withdrawMutation.isPending ? "Withdrawing…" : "Withdraw submission"}
          </button>
          {withdrawMutation.isError && (
            <p role="alert" className="mt-2 text-sm text-red-700">
              We couldn&apos;t withdraw this submission. Please try again.
            </p>
          )}
        </div>
      )}
    </article>
  );
}
