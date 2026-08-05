"use client";

import Link from "next/link";
import { useId, useState, type FormEvent } from "react";
import { useCreateCorrectionReportMutation } from "@/lib/query/use-correction-reports";
import { ApiRequestError } from "@/lib/api/errors";
import type { CreateCorrectionReportInput } from "@/lib/api/correction-reports";
import type { IssueType } from "@/lib/validation/schemas";

const inputClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const labelClassName = "text-sm font-medium text-slate-700";
const linkClassName =
  "font-medium text-blue-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

const ISSUE_TYPE_LABELS: Record<IssueType, string> = {
  GENERAL_INFORMATION: "General information is wrong",
  ADDRESS: "Address is wrong",
  CONTACT_INFORMATION: "Phone, email, or website is wrong",
  OPERATING_HOURS: "Operating hours are wrong",
  ELIGIBILITY: "Eligibility information is wrong",
  ACCESSIBILITY: "Accessibility information is wrong",
  COST: "Cost information is wrong",
  RESOURCE_CLOSED: "This resource has closed",
  DUPLICATE_RESOURCE: "This is a duplicate of another listing",
  OTHER: "Something else",
};

/**
 * Reports an issue on an existing, active resource (Milestone 8B).
 * Submitting never modifies the target resource — the backend always
 * creates a PENDING_REVIEW report, and this form never claims otherwise.
 * Proposed fields are all optional: an explanation-only report (e.g.
 * RESOURCE_CLOSED or OTHER) is valid on its own.
 */
export function CorrectionReportForm({ resourceId, resourceName }: { resourceId: string; resourceName: string }) {
  const createMutation = useCreateCorrectionReportMutation();

  const issueTypeId = useId();
  const explanationId = useId();
  const proposedNameId = useId();
  const proposedAddressLine1Id = useId();
  const proposedCityId = useId();
  const proposedProvinceId = useId();
  const proposedPostalCodeId = useId();
  const proposedPhoneId = useId();
  const proposedEmailId = useId();
  const proposedWebsiteId = useId();
  const errorSummaryId = useId();

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (createMutation.isPending) {
      return;
    }
    setFormError(null);
    setFieldErrors({});

    const data = new FormData(event.currentTarget);
    const input: CreateCorrectionReportInput = {
      issueType: String(data.get("issueType")) as IssueType,
      explanation: String(data.get("explanation") ?? "").trim(),
      proposedName: emptyToUndefined(data.get("proposedName")),
      proposedAddressLine1: emptyToUndefined(data.get("proposedAddressLine1")),
      proposedCity: emptyToUndefined(data.get("proposedCity")),
      proposedProvince: emptyToUndefined(data.get("proposedProvince")),
      proposedPostalCode: emptyToUndefined(data.get("proposedPostalCode")),
      proposedPhone: emptyToUndefined(data.get("proposedPhone")),
      proposedEmail: emptyToUndefined(data.get("proposedEmail")),
      proposedWebsiteUrl: emptyToUndefined(data.get("proposedWebsiteUrl")),
    };

    try {
      await createMutation.mutateAsync({ resourceId, input });
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.fieldErrors) {
        setFieldErrors(cause.fieldErrors);
        setFormError("Please fix the highlighted fields below.");
      } else if (cause instanceof ApiRequestError && cause.status === 409) {
        setFormError("You already have a pending report for this issue on this resource.");
      } else {
        setFormError("Something went wrong submitting your report. Please try again.");
      }
    }
  }

  if (createMutation.isSuccess) {
    const report = createMutation.data;
    return (
      <div className="mx-auto flex w-full max-w-xl flex-col gap-4 px-4 py-12">
        <h1 className="text-2xl font-semibold text-slate-900">Report received</h1>
        <p role="status" className="text-sm text-slate-700">
          Your report has been sent for review. This does not change {resourceName} yet — you can track its status
          from your dashboard.
        </p>
        <Link href={`/dashboard/correction-reports/${report.id}`} className={linkClassName}>
          View your report
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mx-auto flex w-full max-w-xl flex-col gap-6 px-4 py-12">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Report incorrect information</h1>
        <p className="mt-1 text-sm text-slate-600">
          About: <span className="font-medium text-slate-900">{resourceName}</span>
        </p>
        <p className="mt-1 text-sm text-slate-600">
          Reporting an issue does not change this listing immediately — it sends your report for review.
        </p>
      </div>

      {formError && (
        <div id={errorSummaryId} role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          {formError}
        </div>
      )}

      <div className="flex flex-col gap-1">
        <label htmlFor={issueTypeId} className={labelClassName}>
          What&apos;s wrong?
        </label>
        <select id={issueTypeId} name="issueType" required defaultValue="" className={inputClassName}>
          <option value="" disabled>
            Select an issue
          </option>
          {(Object.keys(ISSUE_TYPE_LABELS) as IssueType[]).map((issueType) => (
            <option key={issueType} value={issueType}>
              {ISSUE_TYPE_LABELS[issueType]}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor={explanationId} className={labelClassName}>
          Explanation
        </label>
        <textarea
          id={explanationId}
          name="explanation"
          required
          rows={4}
          placeholder="What did you notice? Include details that will help a reviewer verify this."
          aria-invalid={Boolean(fieldErrors.explanation)}
          aria-describedby={fieldErrors.explanation ? `${explanationId}-error` : undefined}
          className={inputClassName}
        />
        {fieldErrors.explanation && (
          <p id={`${explanationId}-error`} className="text-sm text-red-700">
            {fieldErrors.explanation}
          </p>
        )}
      </div>

      <fieldset className="flex flex-col gap-4 rounded-lg border border-slate-200 p-4">
        <legend className="px-1 text-sm font-medium text-slate-700">Suggested correction (optional)</legend>

        <Field id={proposedNameId} name="proposedName" label="Corrected name" error={fieldErrors.proposedName} />
        <Field id={proposedAddressLine1Id} name="proposedAddressLine1" label="Corrected address" error={fieldErrors.proposedAddressLine1} />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field id={proposedCityId} name="proposedCity" label="City" error={fieldErrors.proposedCity} />
          <Field id={proposedProvinceId} name="proposedProvince" label="Province" placeholder="NS" error={fieldErrors.proposedProvince} />
          <Field id={proposedPostalCodeId} name="proposedPostalCode" label="Postal code" placeholder="B3H 4R2" error={fieldErrors.proposedPostalCode} />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field id={proposedPhoneId} name="proposedPhone" label="Phone" type="tel" error={fieldErrors.proposedPhone} />
          <Field id={proposedEmailId} name="proposedEmail" label="Email" type="email" error={fieldErrors.proposedEmail} />
        </div>
        <Field id={proposedWebsiteId} name="proposedWebsiteUrl" label="Website" type="url" placeholder="https://example.org" error={fieldErrors.proposedWebsiteUrl} />
      </fieldset>

      <button
        type="submit"
        disabled={createMutation.isPending}
        className="w-fit rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {createMutation.isPending ? "Submitting…" : "Submit report"}
      </button>
    </form>
  );
}

function Field({
  id,
  name,
  label,
  error,
  type = "text",
  placeholder,
}: {
  id: string;
  name: string;
  label: string;
  error?: string;
  type?: string;
  placeholder?: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className={labelClassName}>
        {label}
      </label>
      <input
        id={id}
        name={name}
        type={type}
        placeholder={placeholder}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className={inputClassName}
      />
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}

function emptyToUndefined(value: FormDataEntryValue | null): string | undefined {
  const trimmed = String(value ?? "").trim();
  return trimmed.length === 0 ? undefined : trimmed;
}
