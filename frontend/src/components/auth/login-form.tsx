"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useId, useState, type FormEvent } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { resolveReturnPath } from "@/lib/auth/return-to";

const inputClassName =
  "rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";
const linkClassName =
  "font-medium text-blue-700 underline-offset-2 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

export function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const emailId = useId();
  const passwordId = useId();
  const errorId = useId();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(email, password);
      // A "Sign in to save" link (Milestone 8A) carries `?returnTo=` back to
      // the page the caller started on; only a validated same-origin
      // relative path is ever honored — see lib/auth/return-to.ts.
      router.push(resolveReturnPath(searchParams.get("returnTo"), "/dashboard"));
    } catch {
      // The backend never reveals whether an email is registered (ADR-008) —
      // the frontend preserves that by showing one generic message for every
      // failure, regardless of the underlying status/code.
      setError("Incorrect email or password.");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mx-auto flex w-full max-w-sm flex-col gap-4 px-4 py-12">
      <h1 className="text-2xl font-semibold text-slate-900">Log in</h1>

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
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={inputClassName}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor={passwordId} className="text-sm font-medium text-slate-700">
          Password
        </label>
        <input
          id={passwordId}
          name="password"
          type="password"
          autoComplete="current-password"
          required
          aria-describedby={error ? errorId : undefined}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={inputClassName}
        />
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
        {submitting ? "Logging in…" : "Log in"}
      </button>

      <p className="text-sm text-slate-600">
        Don&apos;t have an account? <Link href="/register" className={linkClassName}>Register</Link>
      </p>
    </form>
  );
}
