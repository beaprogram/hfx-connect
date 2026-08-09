"use client";

import {
  useAdminOrganizationDetailQuery,
  useVerifyOrganizationMutation,
  useRejectOrganizationMutation,
  useSuspendOrganizationMutation,
} from "@/lib/query/use-organization";
import { OrganizationVerificationBadge } from "@/components/organization/organization-status-badge";
import { ModerationDecisionForm } from "@/components/moderation/moderation-decision-form";
import { OrganizationAuditHistory } from "@/components/organization/organization-audit-history";
import { InlineError } from "@/components/feedback/inline-error";
import { formatDate } from "@/lib/formatting/dates";

export function AdminOrganizationDetail({ organizationId }: { organizationId: string }) {
  const query = useAdminOrganizationDetailQuery(organizationId);
  const verifyMutation = useVerifyOrganizationMutation();
  const rejectMutation = useRejectOrganizationMutation();
  const suspendMutation = useSuspendOrganizationMutation();

  if (query.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading organization…
      </p>
    );
  }

  if (query.isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <InlineError message="We couldn't load this organization right now." />
      </div>
    );
  }

  const organization = query.data;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{organization.name}</h1>
        <div className="mt-2">
          <OrganizationVerificationBadge status={organization.verificationStatus} />
        </div>
        <p className="mt-1 text-xs text-slate-500">Submitted {formatDate(organization.createdAt)}</p>
      </div>

      <dl className="grid grid-cols-1 gap-x-6 gap-y-3 rounded-lg border border-slate-200 p-4 text-sm sm:grid-cols-2">
        {organization.description && <Detail label="Description" value={organization.description} />}
        {organization.websiteUrl && <Detail label="Website" value={organization.websiteUrl} />}
        {organization.publicEmail && <Detail label="Public email" value={organization.publicEmail} />}
        {organization.phone && <Detail label="Phone" value={organization.phone} />}
        {organization.addressLine1 && <Detail label="Address" value={organization.addressLine1} />}
        {organization.city && <Detail label="City" value={organization.city} />}
        {organization.province && <Detail label="Province" value={organization.province} />}
      </dl>

      {(organization.verificationStatus === "REJECTED" || organization.verificationStatus === "SUSPENDED") &&
        organization.verificationReason && (
          <p className="text-sm text-slate-700">Reason: &ldquo;{organization.verificationReason}&rdquo;</p>
        )}

      {organization.verificationStatus === "PENDING_VERIFICATION" && (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Verify</h2>
            <p className="text-sm text-slate-600">Allows this organization to claim resource listings.</p>
            <ModerationDecisionForm
              actionLabel="Verify"
              isPending={verifyMutation.isPending}
              onSubmit={async (reason) => {
                await verifyMutation.mutateAsync({ organizationId, reason });
              }}
            />
          </section>
          <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <h2 className="text-base font-semibold text-slate-900">Reject</h2>
            <p className="text-sm text-slate-600">The profile is not deleted; the reason is visible to the owner.</p>
            <ModerationDecisionForm
              actionLabel="Reject"
              isPending={rejectMutation.isPending}
              onSubmit={async (reason) => {
                await rejectMutation.mutateAsync({ organizationId, reason });
              }}
            />
          </section>
        </div>
      )}

      {organization.verificationStatus === "VERIFIED" && (
        <section className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
          <h2 className="text-base font-semibold text-slate-900">Suspend</h2>
          <p className="text-sm text-slate-600">
            Resources this organization already owns remain public and owned; only its public profile becomes
            unavailable.
          </p>
          <ModerationDecisionForm
            actionLabel="Suspend"
            isPending={suspendMutation.isPending}
            onSubmit={async (reason) => {
              await suspendMutation.mutateAsync({ organizationId, reason });
            }}
          />
        </section>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-base font-semibold text-slate-900">Audit history</h2>
        <OrganizationAuditHistory organizationId={organizationId} />
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
