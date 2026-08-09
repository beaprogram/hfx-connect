"use client";

import { useOrganizationAuditEventsQuery } from "@/lib/query/use-organization";
import { formatDate } from "@/lib/formatting/dates";
import { organizationAuditEventTypeLabel } from "@/lib/formatting/labels";
import { InlineError } from "@/components/feedback/inline-error";

const PAGE_SIZE = 20;

/** An organization's audit history (Milestone 10A) — ADMIN-only, never rendered on any owner-facing page. */
export function OrganizationAuditHistory({ organizationId }: { organizationId: string }) {
  const query = useOrganizationAuditEventsQuery(organizationId, 0, PAGE_SIZE);

  return (
    <div aria-live="polite">
      {query.isPending && (
        <p role="status" className="text-sm text-slate-600">
          Loading audit history…
        </p>
      )}
      {query.isError && <InlineError message="We couldn't load the audit history right now." />}
      {query.isSuccess && query.data.content.length === 0 && <p className="text-sm text-slate-600">No history yet.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <ul className="flex flex-col gap-3">
          {query.data.content.map((event) => (
            <li key={event.id} className="rounded-lg border border-slate-200 p-3 text-sm">
              <p className="font-medium text-slate-900">{organizationAuditEventTypeLabel(event.eventType)}</p>
              <p className="text-slate-600">
                By {event.actorEmail} on {formatDate(event.createdAt)}
              </p>
              {event.reviewReason && <p className="mt-1 text-slate-700">&ldquo;{event.reviewReason}&rdquo;</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
