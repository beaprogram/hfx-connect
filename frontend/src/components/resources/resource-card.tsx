import Link from "next/link";
import { CostBadge, VerificationBadge } from "@/components/resources/status-badges";
import { formatCityProvince } from "@/lib/formatting/location";
import type { ResourceSummaryResponse } from "@/lib/validation/schemas";

export function ResourceCard({ resource }: { resource: ResourceSummaryResponse }) {
  const location = formatCityProvince(resource.city, resource.province);

  return (
    <article className="relative flex flex-col gap-2 rounded-lg border border-slate-200 p-4 transition-colors hover:border-slate-300">
      <h3 className="text-base font-semibold text-slate-900">
        <Link
          href={`/resources/${resource.slug}`}
          className="rounded-sm after:absolute after:inset-0 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          {resource.name}
        </Link>
      </h3>
      <p className="text-sm text-slate-600">
        {resource.category.name}
        {location ? ` · ${location}` : ""}
      </p>
      <div className="mt-1 flex flex-wrap gap-2">
        <CostBadge costType={resource.costType} />
        <VerificationBadge status={resource.verificationStatus} />
      </div>
    </article>
  );
}
