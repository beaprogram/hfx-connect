"use client";

import Link from "next/link";
import {
  useOrganizationProfileQuery,
  useCreateOrganizationProfileMutation,
  useOrganizationResourcesQuery,
  useOrganizationClaimsQuery,
} from "@/lib/query/use-organization";
import { OrganizationProfileForm } from "@/components/organization/organization-profile-form";
import { OrganizationVerificationBadge } from "@/components/organization/organization-status-badge";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { InlineError } from "@/components/feedback/inline-error";
import { ApiRequestError } from "@/lib/api/errors";

/**
 * The organization dashboard (`/organization`, Milestone 10A). Shows a
 * profile-creation form when the current account has no profile yet, and a
 * status overview otherwise — never claims "verified" before the backend
 * says so (see ADR-017's "Organization Profile UI" guidance).
 */
export function OrganizationDashboard() {
  const profileQuery = useOrganizationProfileQuery();

  if (profileQuery.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading your organization…
      </p>
    );
  }

  const hasNoProfileYet = profileQuery.isError && profileQuery.error instanceof ApiRequestError && profileQuery.error.status === 404;

  if (hasNoProfileYet) {
    return <CreateProfileForm />;
  }

  if (profileQuery.isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <InlineError message="We couldn't load your organization profile right now." />
      </div>
    );
  }

  const profile = profileQuery.data;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <OrganizationNav />
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{profile.name}</h1>
        <div className="mt-2">
          <OrganizationVerificationBadge status={profile.verificationStatus} />
        </div>
      </div>

      <StatusExplanation status={profile.verificationStatus} reason={profile.verificationReason} />

      <Counts />

      <p className="text-sm text-slate-600">
        <Link href="/organization/profile" className="font-medium text-blue-700 underline-offset-2 hover:underline">
          Edit your organization profile
        </Link>
      </p>
    </div>
  );
}

function StatusExplanation({ status, reason }: { status: string; reason?: string | null }) {
  if (status === "PENDING_VERIFICATION") {
    return (
      <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
        Your organization profile must be verified before you can claim resource listings. An administrator will
        review it soon.
      </p>
    );
  }
  if (status === "VERIFIED") {
    return (
      <p role="status" className="rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-900">
        Your organization is verified. You can now claim existing resource listings.
      </p>
    );
  }
  if (status === "REJECTED") {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900">
        <p>Your organization profile was not verified.</p>
        {reason && <p className="mt-1">Reason: &ldquo;{reason}&rdquo;</p>}
        <p className="mt-1">
          You can{" "}
          <Link href="/organization/profile" className="font-medium underline underline-offset-2">
            update your profile
          </Link>{" "}
          and it will be reconsidered.
        </p>
      </div>
    );
  }
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900">
      <p>Your organization has been suspended.</p>
      {reason && <p className="mt-1">Reason: &ldquo;{reason}&rdquo;</p>}
      <p className="mt-1">Resources you already own remain public, but your organization&apos;s public profile is unavailable.</p>
    </div>
  );
}

function Counts() {
  const resourcesQuery = useOrganizationResourcesQuery(0, 1);
  const claimsQuery = useOrganizationClaimsQuery("PENDING_REVIEW", 0, 1);

  return (
    <dl className="grid grid-cols-2 gap-4 text-sm">
      <div className="rounded-lg border border-slate-200 p-4">
        <dt className="text-slate-600">Resources owned</dt>
        <dd className="mt-1 text-xl font-semibold text-slate-900">
          {resourcesQuery.isSuccess ? resourcesQuery.data.totalElements : "—"}
        </dd>
      </div>
      <div className="rounded-lg border border-slate-200 p-4">
        <dt className="text-slate-600">Pending claims</dt>
        <dd className="mt-1 text-xl font-semibold text-slate-900">
          {claimsQuery.isSuccess ? claimsQuery.data.totalElements : "—"}
        </dd>
      </div>
    </dl>
  );
}

function CreateProfileForm() {
  const mutation = useCreateOrganizationProfileMutation();
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Create your organization profile</h1>
        <p className="mt-2 text-sm text-slate-600">
          Your profile starts pending verification. Once an administrator verifies it, you can claim existing
          resource listings that belong to your organization.
        </p>
      </div>
      <OrganizationProfileForm
        submitLabel="Create profile"
        isPending={mutation.isPending}
        onSubmit={async (input) => {
          await mutation.mutateAsync(input);
        }}
      />
    </div>
  );
}
