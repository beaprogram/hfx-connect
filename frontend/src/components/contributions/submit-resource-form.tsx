"use client";

import Link from "next/link";
import { useId, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { getCategories } from "@/lib/api/categories";
import { useCreateResourceSubmissionMutation } from "@/lib/query/use-resource-submissions";
import { ApiRequestError } from "@/lib/api/errors";
import { InlineError } from "@/components/feedback/inline-error";
import type { CreateResourceSubmissionInput } from "@/lib/api/resource-submissions";

const inputClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const labelClassName = "text-sm font-medium text-slate-700";
const linkClassName =
  "font-medium text-blue-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

/**
 * Proposes a new community resource (Milestone 8B). Submitting never
 * publishes a resource — the backend always starts it PENDING_REVIEW, and
 * this form never claims otherwise. Reuses the same category-loading
 * (`getCategories`), field-error-mapping (`ApiRequestError.fieldErrors`),
 * and loading/success-state conventions already established by
 * `register-form.tsx` and `resource-filter-form.tsx`.
 */
export function SubmitResourceForm() {
  const categoryQuery = useQuery({ queryKey: ["categories", "submission-form"], queryFn: () => getCategories({ active: true, size: 100 }) });
  const createMutation = useCreateResourceSubmissionMutation();

  const nameId = useId();
  const categoryId = useId();
  const shortDescriptionId = useId();
  const fullDescriptionId = useId();
  const addressLine1Id = useId();
  const addressLine2Id = useId();
  const cityId = useId();
  const provinceId = useId();
  const postalCodeId = useId();
  const phoneId = useId();
  const emailId = useId();
  const websiteId = useId();
  const costTypeId = useId();
  const eligibilityId = useId();
  const accessibilityId = useId();
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
    const input: CreateResourceSubmissionInput = {
      categoryId: Number(data.get("categoryId")),
      name: String(data.get("name") ?? "").trim(),
      shortDescription: String(data.get("shortDescription") ?? "").trim(),
      fullDescription: emptyToUndefined(data.get("fullDescription")),
      addressLine1: String(data.get("addressLine1") ?? "").trim(),
      addressLine2: emptyToUndefined(data.get("addressLine2")),
      city: String(data.get("city") ?? "").trim(),
      province: String(data.get("province") ?? "").trim(),
      postalCode: String(data.get("postalCode") ?? "").trim(),
      phone: emptyToUndefined(data.get("phone")),
      email: emptyToUndefined(data.get("email")),
      websiteUrl: emptyToUndefined(data.get("websiteUrl")),
      costType: (data.get("costType") as CreateResourceSubmissionInput["costType"]) || undefined,
      eligibilityInformation: emptyToUndefined(data.get("eligibilityInformation")),
      accessibilityInformation: emptyToUndefined(data.get("accessibilityInformation")),
    };

    try {
      await createMutation.mutateAsync(input);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.fieldErrors) {
        setFieldErrors(cause.fieldErrors);
        setFormError("Please fix the highlighted fields below.");
      } else if (cause instanceof ApiRequestError && cause.status === 409) {
        setFormError("You already have a pending submission for this name and category.");
      } else {
        setFormError("Something went wrong submitting your proposal. Please try again.");
      }
    }
  }

  if (createMutation.isSuccess) {
    const submission = createMutation.data;
    return (
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-4 px-4 py-12">
        <h1 className="text-2xl font-semibold text-slate-900">Submission received</h1>
        <p role="status" className="text-sm text-slate-700">
          Your submission has been sent for review. It is not public yet — you can track its status from your
          dashboard.
        </p>
        <Link href={`/dashboard/submissions/${submission.id}`} className={linkClassName}>
          View your submission
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-12">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Propose a resource</h1>
        <p className="mt-1 text-sm text-slate-600">
          Tell us about a community resource that isn&apos;t listed yet. Submissions are reviewed before they appear
          publicly — this does not publish anything immediately.
        </p>
      </div>

      {formError && (
        <div id={errorSummaryId} role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          {formError}
        </div>
      )}

      <div className="flex flex-col gap-1">
        <label htmlFor={categoryId} className={labelClassName}>
          Category
        </label>
        {categoryQuery.isError && <InlineError message="We couldn't load categories. Please try reloading the page." />}
        <select
          id={categoryId}
          name="categoryId"
          required
          disabled={categoryQuery.isPending}
          aria-invalid={Boolean(fieldErrors.categoryId)}
          aria-describedby={fieldErrors.categoryId ? `${categoryId}-error` : undefined}
          className={inputClassName}
        >
          <option value="">Select a category</option>
          {categoryQuery.data?.content.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        {fieldErrors.categoryId && (
          <p id={`${categoryId}-error`} className="text-sm text-red-700">
            {fieldErrors.categoryId}
          </p>
        )}
      </div>

      <Field id={nameId} name="name" label="Resource name" required error={fieldErrors.name} autoComplete="off" />

      <div className="flex flex-col gap-1">
        <label htmlFor={shortDescriptionId} className={labelClassName}>
          Short description
        </label>
        <textarea
          id={shortDescriptionId}
          name="shortDescription"
          required
          rows={2}
          aria-invalid={Boolean(fieldErrors.shortDescription)}
          aria-describedby={fieldErrors.shortDescription ? `${shortDescriptionId}-error` : undefined}
          className={inputClassName}
        />
        {fieldErrors.shortDescription && (
          <p id={`${shortDescriptionId}-error`} className="text-sm text-red-700">
            {fieldErrors.shortDescription}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor={fullDescriptionId} className={labelClassName}>
          Full description (optional)
        </label>
        <textarea
          id={fullDescriptionId}
          name="fullDescription"
          rows={4}
          aria-invalid={Boolean(fieldErrors.fullDescription)}
          aria-describedby={fieldErrors.fullDescription ? `${fullDescriptionId}-error` : undefined}
          className={inputClassName}
        />
        {fieldErrors.fullDescription && (
          <p id={`${fullDescriptionId}-error`} className="text-sm text-red-700">
            {fieldErrors.fullDescription}
          </p>
        )}
      </div>

      <Field id={addressLine1Id} name="addressLine1" label="Address line 1" required error={fieldErrors.addressLine1} autoComplete="address-line1" />
      <Field id={addressLine2Id} name="addressLine2" label="Address line 2 (optional)" error={fieldErrors.addressLine2} autoComplete="address-line2" />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Field id={cityId} name="city" label="City" required error={fieldErrors.city} autoComplete="address-level2" />
        <Field id={provinceId} name="province" label="Province" required error={fieldErrors.province} placeholder="NS" autoComplete="address-level1" />
        <Field id={postalCodeId} name="postalCode" label="Postal code" required error={fieldErrors.postalCode} placeholder="B3H 4R2" autoComplete="postal-code" />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field id={phoneId} name="phone" label="Phone (optional)" error={fieldErrors.phone} type="tel" autoComplete="tel" />
        <Field id={emailId} name="email" label="Email (optional)" error={fieldErrors.email} type="email" autoComplete="email" />
      </div>

      <Field id={websiteId} name="websiteUrl" label="Website (optional)" error={fieldErrors.websiteUrl} type="url" placeholder="https://example.org" autoComplete="url" />

      <div className="flex flex-col gap-1">
        <label htmlFor={costTypeId} className={labelClassName}>
          Cost
        </label>
        <select id={costTypeId} name="costType" defaultValue="UNKNOWN" className={inputClassName}>
          <option value="FREE">Free</option>
          <option value="LOW_COST">Low cost</option>
          <option value="PAID">Paid</option>
          <option value="UNKNOWN">Not sure</option>
        </select>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor={eligibilityId} className={labelClassName}>
          Eligibility information (optional)
        </label>
        <textarea id={eligibilityId} name="eligibilityInformation" rows={2} className={inputClassName} />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor={accessibilityId} className={labelClassName}>
          Accessibility information (optional)
        </label>
        <textarea id={accessibilityId} name="accessibilityInformation" rows={2} className={inputClassName} />
      </div>

      <button
        type="submit"
        disabled={createMutation.isPending}
        className="w-fit rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {createMutation.isPending ? "Submitting…" : "Submit for review"}
      </button>
    </form>
  );
}

function Field({
  id,
  name,
  label,
  required,
  error,
  type = "text",
  placeholder,
  autoComplete,
}: {
  id: string;
  name: string;
  label: string;
  required?: boolean;
  error?: string;
  type?: string;
  placeholder?: string;
  autoComplete?: string;
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
        required={required}
        placeholder={placeholder}
        autoComplete={autoComplete}
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
