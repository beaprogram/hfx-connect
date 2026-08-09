"use client";

import { useId, useState } from "react";
import Link from "next/link";
import { useAdminOrganizationQueueQuery } from "@/lib/query/use-organization";
import { OrganizationVerificationBadge } from "@/components/organization/organization-status-badge";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import { formatDate } from "@/lib/formatting/dates";
import type { OrganizationVerificationStatus } from "@/lib/validation/schemas";

const PAGE_SIZE = 20;

const STATUS_OPTIONS: { value: OrganizationVerificationStatus | ""; label: string }[] = [
  { value: "PENDING_VERIFICATION", label: "Pending verification" },
  { value: "VERIFIED", label: "Verified" },
  { value: "REJECTED", label: "Rejected" },
  { value: "SUSPENDED", label: "Suspended" },
  { value: "", label: "All statuses" },
];

/** The ADMIN organization-verification queue (`/admin/organizations`, Milestone 10A). Defaults to PENDING_VERIFICATION, oldest submitted first. */
export function AdminOrganizationQueue() {
  const [verificationStatus, setVerificationStatus] = useState<OrganizationVerificationStatus | "">("PENDING_VERIFICATION");
  const [sort, setSort] = useState<"createdAt" | "name">("createdAt");
  const [page, setPage] = useState(0);
  const statusId = useId();
  const sortId = useId();

  const query = useAdminOrganizationQueueQuery({
    verificationStatus: verificationStatus === "" ? undefined : verificationStatus,
    page,
    size: PAGE_SIZE,
    sort,
  });

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold text-slate-900">Organizations</h1>

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor={statusId} className="text-sm font-medium text-slate-700">
            Status
          </label>
          <select
            id={statusId}
            value={verificationStatus}
            onChange={(event) => {
              setVerificationStatus(event.target.value as OrganizationVerificationStatus | "");
              setPage(0);
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={sortId} className="text-sm font-medium text-slate-700">
            Sort
          </label>
          <select
            id={sortId}
            value={sort}
            onChange={(event) => {
              setSort(event.target.value as "createdAt" | "name");
              setPage(0);
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            <option value="createdAt">Oldest submitted first</option>
            <option value="name">Name</option>
          </select>
        </div>
      </div>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading organizations…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load the organization queue right now." />
            <button
              type="button"
              onClick={() => query.refetch()}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            >
              Try again
            </button>
          </div>
        )}

        {query.isSuccess && query.data.content.length === 0 && <EmptyState heading="Nothing here right now." />}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((org) => (
                <li key={org.id}>
                  <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex flex-col gap-2">
                      <h3 className="text-base font-semibold text-slate-900">
                        <Link
                          href={`/admin/organizations/${org.id}`}
                          className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          {org.name}
                        </Link>
                      </h3>
                      <OrganizationVerificationBadge status={org.verificationStatus} />
                      <p className="text-xs text-slate-500">Submitted {formatDate(org.createdAt)}</p>
                    </div>
                  </article>
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Organization queue pages" className="mt-4 flex items-center justify-center gap-4">
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

      <p className="text-sm">
        <Link href="/admin/resource-claims" className="font-medium text-blue-700 underline-offset-2 hover:underline">
          Review resource-ownership claims
        </Link>
      </p>
    </div>
  );
}
