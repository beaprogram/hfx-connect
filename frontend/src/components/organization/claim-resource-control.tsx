"use client";

import { useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-provider";
import { useOrganizationProfileQuery, useClaimResourceMutation } from "@/lib/query/use-organization";
import { ApiRequestError } from "@/lib/api/errors";
import { InlineError } from "@/components/feedback/inline-error";

/**
 * The "Claim this listing" control on an active, currently-unowned public
 * resource's detail page (Milestone 10A). Only ever renders anything for a
 * signed-in `ORGANIZATION` account whose own profile is currently
 * `VERIFIED` — this is a UX convenience, not the authorization boundary:
 * the backend independently re-checks role, verification status, and
 * resource ownership on the actual claim request (ADR-017). Never assumes
 * eligibility from anything other than the current account's own fetched
 * profile.
 */
export function ClaimResourceControl({ resourceId }: { resourceId: string }) {
  const { state } = useAuth();
  const profileQuery = useOrganizationProfileQuery();
  const claimMutation = useClaimResourceMutation();
  const [claimed, setClaimed] = useState(false);

  const isOrganization = state.status === "authenticated" && state.user.role === "ORGANIZATION";
  if (!isOrganization) {
    return null;
  }
  if (!profileQuery.isSuccess || profileQuery.data.verificationStatus !== "VERIFIED") {
    return null;
  }

  if (claimed) {
    return (
      <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">
        Claim submitted — pending admin review.{" "}
        <Link href="/organization/claims" className="font-medium underline underline-offset-2">
          View your claims
        </Link>
        .
      </p>
    );
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <button
        type="button"
        onClick={async () => {
          try {
            await claimMutation.mutateAsync(resourceId);
            setClaimed(true);
          } catch {
            // Error is rendered from claimMutation.error below.
          }
        }}
        disabled={claimMutation.isPending}
        className="rounded-md border border-blue-600 px-4 py-2 text-sm font-medium text-blue-700 hover:bg-blue-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {claimMutation.isPending ? "Submitting…" : "Claim this listing"}
      </button>
      {claimMutation.isError && (
        <InlineError
          message={
            claimMutation.error instanceof ApiRequestError
              ? claimMutation.error.message
              : "Something went wrong submitting this claim. Please try again."
          }
        />
      )}
    </div>
  );
}
