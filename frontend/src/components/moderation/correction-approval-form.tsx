"use client";

import { useId, useState, type FormEvent } from "react";
import { ApiRequestError } from "@/lib/api/errors";
import type { ApproveCorrectionReportInput } from "@/lib/api/moderation";
import type { IssueType } from "@/lib/validation/schemas";

const textareaClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const labelClassName = "text-sm font-medium text-slate-700";

const UNSUPPORTED_APPLICATION_ISSUE_TYPES: IssueType[] = ["OPERATING_HOURS", "DUPLICATE_RESOURCE"];

/**
 * The correction-report approval decision form (Milestone 9A) — explicit
 * about what gets applied, never an arbitrary field-editing surface (see
 * ADR-016). `applyProposedChanges` is disabled (with an explanation) for
 * issue types with no automated field mapping; `deactivateResource` is only
 * shown for RESOURCE_CLOSED reports.
 */
export function CorrectionApprovalForm({
  issueType,
  onSubmit,
  isPending,
}: {
  issueType: IssueType;
  onSubmit: (input: ApproveCorrectionReportInput) => Promise<void>;
  isPending: boolean;
}) {
  const reasonId = useId();
  const applyId = useId();
  const deactivateId = useId();
  const errorSummaryId = useId();
  const [error, setError] = useState<string | null>(null);

  const applicationUnsupported = UNSUPPORTED_APPLICATION_ISSUE_TYPES.includes(issueType);
  const canDeactivate = issueType === "RESOURCE_CLOSED";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;
    setError(null);

    const data = new FormData(event.currentTarget);
    const reason = String(data.get("reason") ?? "").trim();
    if (reason.length < 5) {
      setError("A review reason of at least 5 characters is required.");
      return;
    }

    const input: ApproveCorrectionReportInput = {
      reason,
      applyProposedChanges: !applicationUnsupported && data.get("applyProposedChanges") === "on",
      deactivateResource: canDeactivate && data.get("deactivateResource") === "on",
    };

    try {
      await onSubmit(input);
    } catch (cause) {
      if (cause instanceof ApiRequestError) {
        setError(cause.fieldErrors?.reason ?? cause.message);
      } else {
        setError("Something went wrong recording this decision. Please try again.");
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3">
      {error && (
        <div id={errorSummaryId} role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="flex flex-col gap-1">
        <label htmlFor={reasonId} className={labelClassName}>
          Reason
        </label>
        <textarea id={reasonId} name="reason" required rows={3} className={textareaClassName} />
      </div>

      <div className="flex items-start gap-2">
        <input
          id={applyId}
          name="applyProposedChanges"
          type="checkbox"
          disabled={applicationUnsupported}
          className="mt-0.5"
        />
        <label htmlFor={applyId} className="text-sm text-slate-700">
          Apply the supported proposed changes to the resource
          {applicationUnsupported && (
            <span className="block text-xs text-slate-500">
              This issue type has no supported automatic field application. Handle it manually, then reject with an
              explanation, or approve with this box left unchecked.
            </span>
          )}
        </label>
      </div>

      {canDeactivate && (
        <div className="flex items-start gap-2">
          <input id={deactivateId} name="deactivateResource" type="checkbox" className="mt-0.5" />
          <label htmlFor={deactivateId} className="text-sm text-slate-700">
            Deactivate this resource (it no longer appears publicly)
          </label>
        </div>
      )}

      <button
        type="submit"
        disabled={isPending}
        className="w-fit rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {isPending ? "Submitting…" : "Approve"}
      </button>
    </form>
  );
}
