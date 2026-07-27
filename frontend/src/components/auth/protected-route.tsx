"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";

/**
 * A client-side UX guard, not a security boundary — see ADR-009's "Frontend
 * Route Guard Is UX-Layer Only" section. Next.js middleware cannot reliably
 * authenticate the browser in this project's direct-frontend-to-backend
 * architecture (the refresh cookie belongs to the backend's own origin, per
 * ADR-006), so there is no way to know the caller's session status before
 * this component runs in the browser. Every protected API call the wrapped
 * page makes is independently authorized by the backend regardless of
 * whether this component ever renders — a direct request to the dashboard's
 * HTML bypasses nothing real.
 *
 * Renders a loading state for both `"loading"` and the brief instant
 * `"unauthenticated"` is true before the redirect effect fires, so protected
 * content is never painted even momentarily.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "unauthenticated") {
      router.replace("/login");
    }
  }, [state.status, router]);

  if (state.status === "authenticated") {
    return <>{children}</>;
  }

  return (
    <p role="status" aria-live="polite" className="px-4 py-12 text-center text-sm text-slate-600">
      Checking your session…
    </p>
  );
}
