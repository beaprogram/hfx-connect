"use client";

import { useId, useState, type FormEvent } from "react";
import { ApiRequestError } from "@/lib/api/errors";

const textareaClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const labelClassName = "text-sm font-medium text-slate-700";

/**
 * A reason-only moderation decision form — used for resource-submission
 * approve/reject and correction-report reject (Milestone 9A). A real form
 * submission, not `confirm()` (see ADR-016): the reason is always required
 * and visible to the contribution's owner, so it is validated and shown
 * back on error like any other form field.
 */
export function ModerationDecisionForm({
  actionLabel,
  onSubmit,
  isPending,
}: {
  actionLabel: string;
  onSubmit: (reason: string) => Promise<void>;
  isPending: boolean;
}) {
  const reasonId = useId();
  const errorSummaryId = useId();
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;
    setError(null);

    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    if (reason.length < 5) {
      setError("A review reason of at least 5 characters is required.");
      return;
    }

    try {
      await onSubmit(reason);
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
      <button
        type="submit"
        disabled={isPending}
        className="w-fit rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {isPending ? "Submitting…" : actionLabel}
      </button>
    </form>
  );
}
