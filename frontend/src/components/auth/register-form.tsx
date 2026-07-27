"use client";

import Link from "next/link";
import { useId, useState, type FormEvent } from "react";
import { register } from "@/lib/api/auth";
import { ApiRequestError } from "@/lib/api/errors";

const inputClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const linkClassName =
  "font-medium text-blue-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

/**
 * Registration deliberately does not log the caller in on success (the
 * backend endpoint doesn't return a session — see docs/api/README.md) —
 * this shows a success message and a link to `/login` rather than
 * fabricating an auto-login the backend doesn't support.
 */
export function RegisterForm() {
  const emailId = useId();
  const passwordId = useId();
  const passwordHintId = useId();
  const errorId = useId();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [succeeded, setSucceeded] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({ email, password });
      setSucceeded(true);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 409) {
        setError("An account with this email already exists.");
      } else if (cause instanceof ApiRequestError && cause.fieldErrors) {
        setFieldErrors(cause.fieldErrors);
        setError("Please fix the highlighted fields below.");
      } else {
        setError("Registration failed. Please try again.");
      }
      setSubmitting(false);
    }
  }

  if (succeeded) {
    return (
      <div className="mx-auto flex w-full max-w-sm flex-col gap-4 px-4 py-12">
        <h1 className="text-2xl font-semibold text-slate-900">Account created</h1>
        <p role="status" className="text-sm text-slate-700">
          Your account has been created. You can now log in.
        </p>
        <Link href="/login" className={linkClassName}>
          Go to login
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mx-auto flex w-full max-w-sm flex-col gap-4 px-4 py-12">
      <h1 className="text-2xl font-semibold text-slate-900">Create an account</h1>

      <div className="flex flex-col gap-1">
        <label htmlFor={emailId} className="text-sm font-medium text-slate-700">
          Email
        </label>
        <input
          id={emailId}
          name="email"
          type="email"
          autoComplete="email"
          required
          aria-invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? `${emailId}-error` : undefined}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={inputClassName}
        />
        {fieldErrors.email && (
          <p id={`${emailId}-error`} className="text-sm text-red-700">
            {fieldErrors.email}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor={passwordId} className="text-sm font-medium text-slate-700">
          Password
        </label>
        <input
          id={passwordId}
          name="password"
          type="password"
          autoComplete="new-password"
          required
          aria-describedby={`${passwordHintId}${fieldErrors.password ? ` ${passwordId}-error` : ""}`}
          aria-invalid={Boolean(fieldErrors.password)}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={inputClassName}
        />
        <p id={passwordHintId} className="text-xs text-slate-500">
          At least 8 characters. Very common passwords are not accepted.
        </p>
        {fieldErrors.password && (
          <p id={`${passwordId}-error`} className="text-sm text-red-700">
            {fieldErrors.password}
          </p>
        )}
      </div>

      {error && (
        <p id={errorId} role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-60"
      >
        {submitting ? "Creating account…" : "Create account"}
      </button>

      <p className="text-sm text-slate-600">
        Already have an account? <Link href="/login" className={linkClassName}>Log in</Link>
      </p>
    </form>
  );
}
