"use client";

import { useResourceSubmissionAuditEventsQuery, useCorrectionReportAuditEventsQuery } from "@/lib/query/use-moderation";
import { formatDate } from "@/lib/formatting/dates";
import { moderationActionLabel } from "@/lib/formatting/labels";
import { InlineError } from "@/components/feedback/inline-error";
import type { ModerationAuditEvent } from "@/lib/validation/schemas";

const PAGE_SIZE = 20;

/**
 * A contribution's moderation audit history (Milestone 9A) —
 * moderator/admin-only, never rendered on any owner-facing page. Snapshots
 * are shown as plain key/value text, never through unsafe HTML.
 */
export function ResourceSubmissionAuditHistory({ submissionId }: { submissionId: string }) {
  const query = useResourceSubmissionAuditEventsQuery(submissionId, 0, PAGE_SIZE);
  return <AuditHistoryList query={query} />;
}

export function CorrectionReportAuditHistory({ reportId }: { reportId: string }) {
  const query = useCorrectionReportAuditEventsQuery(reportId, 0, PAGE_SIZE);
  return <AuditHistoryList query={query} />;
}

function AuditHistoryList({
  query,
}: {
  query: { isPending: boolean; isError: boolean; isSuccess: boolean; data?: { content: ModerationAuditEvent[] } };
}) {
  return (
    <div aria-live="polite">
      {query.isPending && (
        <p role="status" className="text-sm text-slate-600">
          Loading audit history…
        </p>
      )}
      {query.isError && <InlineError message="We couldn't load the audit history right now." />}
      {query.isSuccess && query.data && query.data.content.length === 0 && (
        <p className="text-sm text-slate-600">No moderation history yet.</p>
      )}
      {query.isSuccess && query.data && query.data.content.length > 0 && (
        <ul className="flex flex-col gap-3">
          {query.data.content.map((event) => (
            <li key={event.id} className="rounded-lg border border-slate-200 p-3 text-sm">
              <p className="font-medium text-slate-900">{moderationActionLabel(event.action)}</p>
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
