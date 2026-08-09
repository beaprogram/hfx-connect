"use client";

import { useOrganizationProfileQuery, useUpdateOrganizationProfileMutation } from "@/lib/query/use-organization";
import { OrganizationProfileForm } from "@/components/organization/organization-profile-form";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { InlineError } from "@/components/feedback/inline-error";

/** The `/organization/profile` edit page (Milestone 10A) — only reachable once a profile already exists; `/organization` itself handles first-time creation. */
export function OrganizationProfilePage() {
  const profileQuery = useOrganizationProfileQuery();
  const updateMutation = useUpdateOrganizationProfileMutation();

  if (profileQuery.isPending) {
    return (
      <p role="status" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading your organization…
      </p>
    );
  }

  if (profileQuery.isError || !profileQuery.data) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <InlineError message="We couldn't load your organization profile right now. If you haven't created one yet, go to the organization overview page first." />
      </div>
    );
  }

  const profile = profileQuery.data;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <OrganizationNav />
      <h1 className="text-2xl font-semibold text-slate-900">Edit organization profile</h1>
      <OrganizationProfileForm
        initial={profile}
        submitLabel="Save changes"
        isPending={updateMutation.isPending}
        verifiedWarning={profile.verificationStatus === "VERIFIED"}
        onSubmit={async (input) => {
          await updateMutation.mutateAsync(input);
        }}
      />
    </div>
  );
}
