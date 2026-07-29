import Link from "next/link";
import { CostBadge, HoursStatusBadge, VerificationBadge } from "@/components/resources/status-badges";
import { ExternalWebsiteLink } from "@/components/resources/external-website-link";
import { addressLines } from "@/lib/formatting/address";
import { formatDate } from "@/lib/formatting/dates";
import { costTypeLabel, dayOfWeekLabel, formatLocalTime } from "@/lib/formatting/labels";
import { WEEKLY_DAY_ORDER } from "@/lib/constants/resources";
import type { ResourceResponse } from "@/lib/validation/schemas";

/**
 * Deliberately has no "Accessibility" section — see
 * docs/wireframes/resource-detail.md's "A Note on Scope": the backend does
 * not return an accessibility field, and inventing one would violate this
 * milestone's own "no fabricated values" rule.
 */
export function ResourceDetail({ resource }: { resource: ResourceResponse }) {
  const hasContact = Boolean(resource.phone || resource.email || resource.websiteUrl);
  const lines = addressLines(resource);

  return (
    <article className="mx-auto w-full max-w-3xl px-4 py-12 sm:px-6">
      <p className="text-sm">
        <Link href="/resources" className="text-slate-600 underline underline-offset-2 hover:text-slate-800">
          ← Back to all resources
        </Link>
        {" · "}
        <Link
          href={`/resources?categoryId=${resource.category.id}`}
          className="text-slate-600 underline underline-offset-2 hover:text-slate-800"
        >
          {resource.category.name}
        </Link>
      </p>

      <h1 className="mt-3 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{resource.name}</h1>
      <div className="mt-3 flex flex-wrap gap-2">
        <VerificationBadge status={resource.verificationStatus} />
        <CostBadge costType={resource.costType} />
        <HoursStatusBadge status={resource.hours.hoursStatus} />
      </div>

      {resource.description && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold text-slate-900">About</h2>
          <p className="mt-2 leading-relaxed text-slate-700">{resource.description}</p>
        </section>
      )}

      {lines.length > 0 && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold text-slate-900">Location</h2>
          <address className="mt-2 not-italic leading-relaxed text-slate-700">
            {lines.map((line, index) => (
              <span key={index} className="block">
                {line}
              </span>
            ))}
          </address>
        </section>
      )}

      {hasContact && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold text-slate-900">Contact</h2>
          <ul className="mt-2 space-y-1 leading-relaxed text-slate-700">
            {resource.phone && (
              <li>
                <a
                  href={`tel:${resource.phone}`}
                  className="rounded-sm text-blue-700 underline underline-offset-2 hover:text-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                >
                  {resource.phone}
                </a>
              </li>
            )}
            {resource.email && (
              <li>
                <a
                  href={`mailto:${resource.email}`}
                  className="rounded-sm text-blue-700 underline underline-offset-2 hover:text-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                >
                  {resource.email}
                </a>
              </li>
            )}
            {resource.websiteUrl && (
              <li>
                <ExternalWebsiteLink url={resource.websiteUrl} />
              </li>
            )}
          </ul>
        </section>
      )}

      <section className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">Cost</h2>
        <p className="mt-2 leading-relaxed text-slate-700">
          {costTypeLabel(resource.costType)}
          {resource.costDetails ? ` — ${resource.costDetails}` : ""}
        </p>
      </section>

      {resource.eligibility && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold text-slate-900">Eligibility</h2>
          <p className="mt-2 leading-relaxed text-slate-700">{resource.eligibility}</p>
        </section>
      )}

      <section className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">Hours</h2>
        {resource.hours.hoursStatus === "UNKNOWN" ? (
          <p className="mt-2 leading-relaxed text-slate-700">
            Hours have not been added for this resource yet.
          </p>
        ) : (
          <dl className="mt-2 divide-y divide-slate-200 text-sm leading-relaxed text-slate-700">
            {WEEKLY_DAY_ORDER.map((day) => {
              const entry = resource.hours.weeklyHours.find((e) => e.dayOfWeek === day);
              return (
                <div key={day} className="flex items-center justify-between gap-4 py-1.5">
                  <dt className="font-medium text-slate-900">{dayOfWeekLabel(day)}</dt>
                  <dd>
                    {!entry && <span className="text-slate-500">Hours unavailable</span>}
                    {entry?.closed && <span>Closed</span>}
                    {entry && !entry.closed && entry.opensAt && entry.closesAt && (
                      <span>
                        {formatLocalTime(entry.opensAt)} – {formatLocalTime(entry.closesAt)}
                        {entry.overnight && (
                          <span className="ml-1 text-slate-500">(continues past midnight)</span>
                        )}
                      </span>
                    )}
                  </dd>
                </div>
              );
            })}
          </dl>
        )}
      </section>

      <p className="mt-10 text-sm text-slate-500">Added {formatDate(resource.createdAt)}</p>
    </article>
  );
}
