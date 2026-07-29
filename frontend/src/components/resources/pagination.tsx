import Link from "next/link";
import { buildResourcesHref } from "@/lib/query/resource-list-params";
import type { CostTypeFilterValue, ResourceSort, VerificationStatusFilterValue } from "@/lib/constants/resources";

/**
 * Compact, accessible pagination: Previous/Next plus a human "Page X of Y"
 * (the backend's `page` is 0-based; this display translates it to the
 * 1-based number a person expects — see docs/wireframes/resource-list.md).
 */
export function Pagination({
  page,
  totalPages,
  categoryId,
  sort,
  q,
  costType,
  verificationStatus,
  openNow,
}: {
  page: number;
  totalPages: number;
  categoryId?: number;
  sort: ResourceSort;
  q?: string;
  costType?: CostTypeFilterValue;
  verificationStatus?: VerificationStatusFilterValue;
  openNow?: boolean;
}) {
  if (totalPages <= 1) return null;

  const hasPrevious = page > 0;
  const hasNext = page < totalPages - 1;

  return (
    <nav aria-label="Resource pages" className="mt-8 flex items-center justify-center gap-4">
      {hasPrevious ? (
        <Link
          href={buildResourcesHref({ categoryId, sort, q, costType, verificationStatus, openNow, page: page - 1 })}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Previous page
        </Link>
      ) : (
        <span aria-disabled="true" className="rounded-md border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-400">
          Previous page
        </span>
      )}

      <span className="text-sm text-slate-600">
        Page {page + 1} of {totalPages}
      </span>

      {hasNext ? (
        <Link
          href={buildResourcesHref({ categoryId, sort, q, costType, verificationStatus, openNow, page: page + 1 })}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Next page
        </Link>
      ) : (
        <span aria-disabled="true" className="rounded-md border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-400">
          Next page
        </span>
      )}
    </nav>
  );
}
