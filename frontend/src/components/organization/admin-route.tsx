"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { buildLoginHref } from "@/lib/auth/return-to";

/**
 * A client-side UX guard for `/admin/**` (Milestone 10A) — same
 * "not a security boundary" caveat as {@link ModerationRoute} (ADR-017).
 * Deliberately `ADMIN`-only, not `hasAnyRole(ADMIN, MODERATOR)` like {@link
 * ModerationRoute}: organization verification and ownership-claim review
 * are not extended to `MODERATOR` (see ADR-017's "Authorization Matrix").
 */
export function AdminRoute({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const isAdmin = state.status === "authenticated" && state.user.role === "ADMIN";

  useEffect(() => {
    if (state.status === "unauthenticated") {
      router.replace(buildLoginHref(pathname));
    }
  }, [state.status, router, pathname]);

  if (state.status === "authenticated" && isAdmin) {
    return <>{children}</>;
  }

  if (state.status === "authenticated" && !isAdmin) {
    return (
      <div role="alert" className="mx-auto max-w-xl px-4 py-12 text-center">
        <h1 className="text-lg font-semibold text-slate-900">Access denied</h1>
        <p className="mt-2 text-sm text-slate-600">
          Administration is only available to admin accounts. If you believe this is a mistake, contact a site
          administrator.
        </p>
      </div>
    );
  }

  return (
    <p role="status" aria-live="polite" className="px-4 py-12 text-center text-sm text-slate-600">
      Checking your session…
    </p>
  );
}
