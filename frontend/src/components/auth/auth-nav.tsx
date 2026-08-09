"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";

const linkClassName =
  "rounded-sm text-sm font-medium text-slate-700 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

/**
 * Reflects the current session in the desktop nav only — see
 * `components/navigation/mobile-nav.tsx` for the equivalent mobile links.
 * Hides itself entirely during the initial session-restoration attempt
 * (`state.status === "loading"`) so it never flashes a signed-out link that
 * a moment later turns out to be wrong once the refresh cookie is checked.
 */
export function AuthNav() {
  const { state, logout } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  if (state.status === "loading") {
    return null;
  }

  if (state.status === "authenticated") {
    const isModerator = state.user.role === "MODERATOR" || state.user.role === "ADMIN";
    const isOrganization = state.user.role === "ORGANIZATION";
    const isAdmin = state.user.role === "ADMIN";
    return (
      <div className="flex items-center gap-4">
        <Link href="/dashboard" className={linkClassName}>
          Dashboard
        </Link>
        {isModerator && (
          <Link href="/moderation" className={linkClassName}>
            Moderation
          </Link>
        )}
        {isOrganization && (
          <Link href="/organization" className={linkClassName}>
            Organization
          </Link>
        )}
        {isAdmin && (
          <Link href="/admin/organizations" className={linkClassName}>
            Administration
          </Link>
        )}
        <button type="button" onClick={handleLogout} className={linkClassName}>
          Log out
        </button>
      </div>
    );
  }

  return (
    <Link href="/login" className={linkClassName}>
      Log in
    </Link>
  );
}
