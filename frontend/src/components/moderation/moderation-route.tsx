"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { buildLoginHref } from "@/lib/auth/return-to";

/**
 * A client-side UX guard for `/moderation/**` — same "not a security
 * boundary" caveat as {@link ProtectedRoute} (ADR-009/ADR-016): every
 * `/api/v1/moderation/**` call is independently authorized by the backend
 * regardless of whether this component ever renders. This guard adds one
 * more check {@code ProtectedRoute} doesn't make — a signed-in-but-
 * insufficient-role account (USER/ORGANIZATION) gets a distinct "access
 * denied" state rather than being redirected to login (they *are* signed
 * in; the problem is their role, not their session).
 *
 * <p>Renders a loading state for `"loading"` and the brief instant
 * `"unauthenticated"` is true before the redirect effect fires, so
 * protected content is never painted even momentarily — the same flash
 * prevention {@code ProtectedRoute} already established.
 */
export function ModerationRoute({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const isModerator = state.status === "authenticated" && (state.user.role === "MODERATOR" || state.user.role === "ADMIN");

  useEffect(() => {
    if (state.status === "unauthenticated") {
      router.replace(buildLoginHref(pathname));
    }
  }, [state.status, router, pathname]);

  if (state.status === "authenticated" && isModerator) {
    return <>{children}</>;
  }

  if (state.status === "authenticated" && !isModerator) {
    return (
      <div role="alert" className="mx-auto max-w-xl px-4 py-12 text-center">
        <h1 className="text-lg font-semibold text-slate-900">Access denied</h1>
        <p className="mt-2 text-sm text-slate-600">
          Moderation is only available to moderator and admin accounts. If you believe this is a mistake, contact a
          site administrator.
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
