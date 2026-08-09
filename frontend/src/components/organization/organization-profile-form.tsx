"use client";

import { useId, useState, type FormEvent } from "react";
import { ApiRequestError } from "@/lib/api/errors";
import type { OrganizationProfileInput } from "@/lib/api/organization";
import type { OrganizationResponse } from "@/lib/validation/schemas";

const inputClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const labelClassName = "text-sm font-medium text-slate-700";

/**
 * The organization profile create/update form (Milestone 10A) — one shape
 * for both, matching the backend's own `OrganizationProfileRequest`. Field
 * errors from the backend's `ValidationException` are shown inline next to
 * the field they name, the same pattern the registration/resource-submission
 * forms already use.
 */
export function OrganizationProfileForm({
  initial,
  submitLabel,
  onSubmit,
  isPending,
  verifiedWarning,
}: {
  initial?: OrganizationResponse;
  submitLabel: string;
  onSubmit: (input: OrganizationProfileInput) => Promise<void>;
  isPending: boolean;
  /** Shown when editing an already-VERIFIED profile — identity-significant changes reset verification (ADR-017). */
  verifiedWarning?: boolean;
}) {
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const ids = {
    name: useId(),
    description: useId(),
    websiteUrl: useId(),
    publicEmail: useId(),
    phone: useId(),
    addressLine1: useId(),
    city: useId(),
    province: useId(),
    postalCode: useId(),
  };

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;
    setError(null);
    setFieldErrors({});

    const data = new FormData(event.currentTarget);
    const input: OrganizationProfileInput = {
      name: String(data.get("name") ?? "").trim(),
      description: optional(data, "description"),
      websiteUrl: optional(data, "websiteUrl"),
      publicEmail: optional(data, "publicEmail"),
      phone: optional(data, "phone"),
      addressLine1: optional(data, "addressLine1"),
      city: optional(data, "city"),
      province: optional(data, "province"),
      postalCode: optional(data, "postalCode"),
    };

    try {
      await onSubmit(input);
    } catch (cause) {
      if (cause instanceof ApiRequestError) {
        setError(cause.message);
        setFieldErrors(cause.fieldErrors ?? {});
      } else {
        setError("Something went wrong saving this profile. Please try again.");
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {error && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {error}
        </div>
      )}
      {verifiedWarning && (
        <p role="status" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          Changing the organization name or website will reset this profile to pending verification.
        </p>
      )}

      <Field id={ids.name} name="name" label="Organization name" required defaultValue={initial?.name} error={fieldErrors.name} />
      <div className="flex flex-col gap-1">
        <label htmlFor={ids.description} className={labelClassName}>
          Description
        </label>
        <textarea
          id={ids.description}
          name="description"
          rows={3}
          defaultValue={initial?.description ?? ""}
          className={inputClassName}
        />
        {fieldErrors.description && <p className="text-sm text-red-700">{fieldErrors.description}</p>}
      </div>
      <Field id={ids.websiteUrl} name="websiteUrl" label="Website" defaultValue={initial?.websiteUrl} error={fieldErrors.websiteUrl} />
      <Field
        id={ids.publicEmail}
        name="publicEmail"
        label="Public contact email"
        defaultValue={initial?.publicEmail}
        error={fieldErrors.publicEmail}
      />
      <Field id={ids.phone} name="phone" label="Phone" defaultValue={initial?.phone} error={fieldErrors.phone} />
      <Field
        id={ids.addressLine1}
        name="addressLine1"
        label="Address"
        defaultValue={initial?.addressLine1}
        error={fieldErrors.addressLine1}
      />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Field id={ids.city} name="city" label="City" defaultValue={initial?.city} error={fieldErrors.city} />
        <Field id={ids.province} name="province" label="Province" defaultValue={initial?.province} error={fieldErrors.province} />
        <Field
          id={ids.postalCode}
          name="postalCode"
          label="Postal code"
          defaultValue={initial?.postalCode}
          error={fieldErrors.postalCode}
        />
      </div>

      <button
        type="submit"
        disabled={isPending}
        className="w-fit rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {isPending ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}

function optional(data: FormData, name: string): string | undefined {
  const value = String(data.get(name) ?? "").trim();
  return value.length > 0 ? value : undefined;
}

function Field({
  id,
  name,
  label,
  defaultValue,
  required,
  error,
}: {
  id: string;
  name: string;
  label: string;
  defaultValue?: string | null;
  required?: boolean;
  error?: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className={labelClassName}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <input
        id={id}
        name={name}
        type="text"
        required={required}
        defaultValue={defaultValue ?? ""}
        className={inputClassName}
        aria-invalid={error ? true : undefined}
      />
      {error && <p className="text-sm text-red-700">{error}</p>}
    </div>
  );
}
