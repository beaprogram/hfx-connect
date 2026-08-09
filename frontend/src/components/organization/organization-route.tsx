"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { buildLoginHref } from "@/lib/auth/return-to";

/**
 * A client-side UX guard for `/organization/**` (Milestone 10A) — same
 * "not a security boundary" caveat as {@link ModerationRoute}/{@link
 * ProtectedRoute} (ADR-009/ADR-017): every `/api/v1/organizations/me/**`
 * call is independently authorized by the backend regardless of whether
 * this component ever renders. A signed-in-but-wrong-role account (USER/
 * MODERATOR/ADMIN) gets a distinct "access denied" state rather than being
 * redirected to login. Note this guard only checks the account's
 * `ORGANIZATION` role — it deliberately says nothing about whether the
 * organization profile itself is verified; each page decides how to render
 * a pending/rejected/suspended profile.
 */
export function OrganizationRoute({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const isOrganization = state.status === "authenticated" && state.user.role === "ORGANIZATION";

  useEffect(() => {
    if (state.status === "unauthenticated") {
      router.replace(buildLoginHref(pathname));
    }
  }, [state.status, router, pathname]);

  if (state.status === "authenticated" && isOrganization) {
    return <>{children}</>;
  }

  if (state.status === "authenticated" && !isOrganization) {
    return (
      <div role="alert" className="mx-auto max-w-xl px-4 py-12 text-center">
        <h1 className="text-lg font-semibold text-slate-900">Access denied</h1>
        <p className="mt-2 text-sm text-slate-600">
          This area is only available to organization accounts. If you believe this is a mistake, contact a site
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
