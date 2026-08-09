"use client";

import { useId, useState } from "react";
import Link from "next/link";
import { useOrganizationClaimsQuery, useWithdrawOwnershipClaimMutation } from "@/lib/query/use-organization";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { OwnershipClaimStatusBadge } from "@/components/organization/organization-status-badge";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import { formatDate } from "@/lib/formatting/dates";
import type { OwnershipClaimStatus } from "@/lib/validation/schemas";

const PAGE_SIZE = 20;

const STATUS_OPTIONS: { value: OwnershipClaimStatus | ""; label: string }[] = [
  { value: "", label: "All statuses" },
  { value: "PENDING_REVIEW", label: "Pending review" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "WITHDRAWN", label: "Withdrawn" },
];

/** The `/organization/claims` page (Milestone 10A) — only the current organization's own claims; never another organization's. */
export function OrganizationClaimsList() {
  const [status, setStatus] = useState<OwnershipClaimStatus | "">("");
  const [page, setPage] = useState(0);
  const statusId = useId();
  const query = useOrganizationClaimsQuery(status === "" ? undefined : status, page, PAGE_SIZE);
  const withdrawMutation = useWithdrawOwnershipClaimMutation();

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <OrganizationNav />
      <h1 className="text-2xl font-semibold text-slate-900">Resource claims</h1>

      <div className="flex flex-col gap-1">
        <label htmlFor={statusId} className="text-sm font-medium text-slate-700">
          Status
        </label>
        <select
          id={statusId}
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as OwnershipClaimStatus | "");
            setPage(0);
          }}
          className="w-fit rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading your claims…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load your claims right now." />
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
          <EmptyState heading="No claims yet.">
            Visit a resource&apos;s page and select &ldquo;Claim this listing&rdquo; if it belongs to your organization.
          </EmptyState>
        )}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((claim) => (
                <li key={claim.id}>
                  <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex flex-col gap-2">
                      <h3 className="text-base font-semibold text-slate-900">
                        {claim.resource ? (
                          <Link
                            href={`/resources/${claim.resource.slug}`}
                            className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                          >
                            {claim.resource.name}
                          </Link>
                        ) : (
                          "Resource no longer available"
                        )}
                      </h3>
                      <OwnershipClaimStatusBadge status={claim.status} />
                      <p className="text-xs text-slate-500">Requested {formatDate(claim.requestedAt)}</p>
                      {claim.reviewReason && <p className="text-sm text-slate-700">&ldquo;{claim.reviewReason}&rdquo;</p>}
                      {claim.status === "APPROVED" && (
                        <p className="text-sm text-green-800">This resource is now owned by your organization.</p>
                      )}
                    </div>
                    {claim.status === "PENDING_REVIEW" && (
                      <button
                        type="button"
                        onClick={() => withdrawMutation.mutate(claim.id)}
                        disabled={withdrawMutation.isPending}
                        className="w-fit rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
                      >
                        Withdraw
                      </button>
                    )}
                  </article>
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Resource claims pages" className="mt-4 flex items-center justify-center gap-4">
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
    </div>
  );
}
