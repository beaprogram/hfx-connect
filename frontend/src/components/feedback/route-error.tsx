"use client";

import { useEffect } from "react";
import Link from "next/link";

/**
 * Shared body for every route-segment `error.tsx` — see
 * docs/wireframes/states.md's "Error (Backend Failure, Not a 404)" pattern.
 * Never renders the underlying error's message: Next.js already redacts
 * Server Component error details in production, and the backend's own rule
 * is to never leak internal detail to a response either way (docs/api/README.md).
 */
export function RouteError({ error, onRetry }: { error: Error & { digest?: string }; onRetry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-16 text-center sm:px-6">
      <h1 className="text-xl font-semibold text-slate-900">Something went wrong loading this page</h1>
      <p className="mt-2 text-base text-slate-600">
        The HFX Connect backend couldn&apos;t be reached, or returned an unexpected response.
      </p>
      <div className="mt-6 flex justify-center gap-4">
        <button
          type="button"
          onClick={onRetry}
          className="rounded-md bg-blue-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Try again
        </button>
        <Link
          href="/"
          className="rounded-md border border-slate-300 px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Go home
        </Link>
      </div>
    </div>
  );
}
