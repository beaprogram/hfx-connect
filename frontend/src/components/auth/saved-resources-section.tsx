"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { getSavedResources } from "@/lib/api/saved-resources";
import { useRemoveSavedResourceMutation } from "@/lib/query/use-saved-resources";
import { savedResourceKeys } from "@/lib/query/keys";
import { useAuth } from "@/lib/auth/auth-provider";
import { CostBadge, HoursStatusBadge, VerificationBadge } from "@/components/resources/status-badges";
import { formatCityProvince } from "@/lib/formatting/location";
import { formatDate } from "@/lib/formatting/dates";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import type { SavedResourceSummaryResponse } from "@/lib/validation/schemas";

const PAGE_SIZE = 10;

/**
 * The dashboard's saved-resources section (Milestone 8A). Reuses the same
 * card-sized resource summary the public list already shows — the backend's
 * saved-resource response deliberately has no separate "detail" fields
 * (description, address, ...) beyond that summary, so this section never
 * fetches or fabricates a longer description per card; see ADR/milestone
 * doc's "Query and Pagination Strategy" section.
 */
export function SavedResourcesSection() {
  const { state, getValidAccessToken } = useAuth();
  const [page, setPage] = useState(0);
  const removeMutation = useRemoveSavedResourceMutation();
  const userId = state.status === "authenticated" ? state.user.id : null;

  const query = useQuery({
    enabled: userId !== null,
    queryKey: savedResourceKeys.list(userId ?? "", { page, size: PAGE_SIZE }),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return getSavedResources({ page, size: PAGE_SIZE, accessToken: token, signal });
    },
  });

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold text-slate-900">Saved Resources</h2>

      <div aria-live="polite">
        {query.isPending && (
          <p role="status" className="text-sm text-slate-600">
            Loading your saved resources…
          </p>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-2">
            <InlineError message="We couldn't load your saved resources right now." />
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
          <EmptyState heading="You have not saved any resources yet.">
            <Link href="/resources" className="underline underline-offset-2">
              Browse resources
            </Link>
          </EmptyState>
        )}

        {query.isSuccess && query.data.content.length > 0 && (
          <>
            <ul className="flex flex-col gap-3">
              {query.data.content.map((saved) => (
                <li key={saved.resource.id}>
                  <SavedResourceCard
                    saved={saved}
                    onRemove={() => removeMutation.mutate(saved.resource.id)}
                    isRemoving={removeMutation.isPending && removeMutation.variables === saved.resource.id}
                  />
                </li>
              ))}
            </ul>

            {query.data.totalPages > 1 && (
              <nav aria-label="Saved-resource pages" className="mt-4 flex items-center justify-center gap-4">
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
    </section>
  );
}

function SavedResourceCard({
  saved,
  onRemove,
  isRemoving,
}: {
  saved: SavedResourceSummaryResponse;
  onRemove: () => void;
  isRemoving: boolean;
}) {
  const { resource, savedAt } = saved;
  const location = formatCityProvince(resource.city, resource.province);

  return (
    <article className="flex flex-col gap-2 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex flex-col gap-2">
        <h3 className="text-base font-semibold text-slate-900">
          <Link
            href={`/resources/${resource.slug}`}
            className="rounded-sm underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            {resource.name}
          </Link>
        </h3>
        <p className="text-sm text-slate-600">
          {resource.category.name}
          {location ? ` · ${location}` : ""}
        </p>
        <div className="flex flex-wrap gap-2">
          <CostBadge costType={resource.costType} />
          <VerificationBadge status={resource.verificationStatus} />
          <HoursStatusBadge status={resource.hoursStatus} />
        </div>
        <p className="text-xs text-slate-500">Saved {formatDate(savedAt)}</p>
      </div>
      <button
        type="button"
        onClick={onRemove}
        disabled={isRemoving}
        aria-label={`Remove ${resource.name} from saved resources`}
        className="w-fit shrink-0 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        {isRemoving ? "Removing…" : "Remove"}
      </button>
    </article>
  );
}
