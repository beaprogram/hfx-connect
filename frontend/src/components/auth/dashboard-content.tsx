"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getCurrentUser } from "@/lib/api/auth";
import { useAuth } from "@/lib/auth/auth-provider";

/**
 * Deliberately minimal: only the safe fields `/users/me` returns. No saved
 * resources, moderation tools, or other fabricated features — see the
 * milestone's explicit "no fake features" requirement.
 */
export function DashboardContent() {
  const { getValidAccessToken, logout } = useAuth();
  const router = useRouter();

  const { data, isPending, isError } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("No valid session.");
      }
      return getCurrentUser(token);
    },
    retry: false,
  });

  async function handleLogout() {
    await logout();
    router.push("/login");
  }

  if (isPending) {
    return (
      <p role="status" aria-live="polite" className="px-4 py-12 text-center text-sm text-slate-600">
        Loading your account…
      </p>
    );
  }

  if (isError || !data) {
    return (
      <p role="alert" className="px-4 py-12 text-center text-sm text-red-700">
        We couldn&apos;t load your account. Please try logging in again.
      </p>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-12">
      <h1 className="text-2xl font-semibold text-slate-900">Your account</h1>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="font-medium text-slate-700">Email</dt>
        <dd className="text-slate-900">{data.email}</dd>
        <dt className="font-medium text-slate-700">Role</dt>
        <dd className="text-slate-900">{data.role}</dd>
        <dt className="font-medium text-slate-700">Status</dt>
        <dd className="text-slate-900">{data.status}</dd>
      </dl>
      <button
        type="button"
        onClick={handleLogout}
        className="w-fit rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Log out
      </button>
    </div>
  );
}
