"use client";

import Link from "next/link";
import { useAdminOwnershipClaimDetailQuery, useApproveOwnershipClaimMutation, useRejectOwnershipClaimMutation } from "@/lib/query/use-organization";
import { OrganizationVerificationBadge, OwnershipClaimStatusBadge } from "@/components/organization/organization-status-badge";
import { ModerationDecisionForm } from "@/components/moderation/moderation-decision-form";
import { InlineError } from "@/components/feedback/inline-error";
import { formatDate } from "@/lib/formatting/dates";

export function AdminOwnershipClaimDetail({ claimId }: { claimId: string }) {
  const query = useAdminOwnershipClaimDetailQuery(claimId);
  const approveMutation = useApproveOwnershipClaimMutation();
  const rejectMutation = useRejectOwnershipClaimMutation();

  if (query.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading claim…
      </p>
    );
  }

  if (query.isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <InlineError message="We couldn't load this claim right now." />
      </div>
    );
  }

  const claim = query.data;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{claim.resource?.name ?? "Resource no longer available"}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <OwnershipClaimStatusBadge status={claim.status} />
        </div>
        <p className="mt-1 text-xs text-slate-500">Requested {formatDate(claim.requestedAt)}</p>
      </div>

      <dl className="grid grid-cols-1 gap-x-6 gap-y-3 rounded-lg border border-slate-200 p-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="font-medium text-slate-900">Claiming organization</dt>
          <dd className="text-slate-700">
            <Link
              href={`/admin/organizations/${claim.organizationId}`}
              className="font-medium text-blue-700 underline-offset-2 hover:underline"
            >
              {claim.organizationName}
            </Link>
          </dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Organization verification</dt>
          <dd>
            <OrganizationVerificationBadge status={claim.organizationVerificationStatus} />
          </dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Resource category</dt>
          <dd className="text-slate-700">{claim.resource?.category.name ?? "—"}</dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Current ownership</dt>
          <dd className="text-slate-700">
            {claim.currentResourceOrganizationId
              ? claim.currentResourceOrganizationId === claim.organizationId
                ? "Owned by this organization"
                : "Owned by a different organization"
              : "Unowned"}
          </dd>
        </div>
      </dl>

      {claim.reviewReason && <p className="text-sm text-slate-700">Reason: &ldquo;{claim.reviewReason}&rdquo;</p>}

      {claim.status === "PENDING_REVIEW" && (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Approve</h2>
            <p className="text-sm text-slate-600">Assigns ownership of this resource to the claiming organization.</p>
            <ModerationDecisionForm
              actionLabel="Approve"
              isPending={approveMutation.isPending}
              onSubmit={async (reason) => {
                await approveMutation.mutateAsync({ claimId, reason });
              }}
            />
          </section>
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Reject</h2>
            <p className="text-sm text-slate-600">The resource is never modified.</p>
            <ModerationDecisionForm
              actionLabel="Reject"
              isPending={rejectMutation.isPending}
              onSubmit={async (reason) => {
                await rejectMutation.mutateAsync({ claimId, reason });
              }}
            />
          </section>
        </div>
      )}
    </div>
  );
}
