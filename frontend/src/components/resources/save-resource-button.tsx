"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { buildLoginHref } from "@/lib/auth/return-to";
import { useSaveResourceMutation, useRemoveSavedResourceMutation } from "@/lib/query/use-saved-resources";

interface SaveResourceButtonProps {
  resourceId: string;
  resourceName: string;
  /** Whether this resource is currently saved — the caller looks this up once for every visible resource via `useSavedResourceStatusMap`, never per-button. `undefined` while that status is still loading. */
  isSaved: boolean | undefined;
  className?: string;
}

/**
 * The one save/remove control every resource card and the detail page share
 * (Milestone 8A). Renders one of three states: signed-out ("Sign in to
 * save", linking back to the current page after login), signed-in-and-
 * unknown (a neutral "Save" while the batched status lookup is still
 * loading), or signed-in-and-known (Save/Remove, reflecting confirmed
 * server state).
 */
export function SaveResourceButton({ resourceId, resourceName, isSaved, className }: SaveResourceButtonProps) {
  const { state } = useAuth();

  if (state.status === "loading") {
    return null;
  }

  if (state.status === "unauthenticated") {
    return <SignInToSaveLink resourceName={resourceName} className={className} />;
  }

  return (
    <AuthenticatedSaveButton
      resourceId={resourceId}
      resourceName={resourceName}
      isSaved={isSaved ?? false}
      className={className}
    />
  );
}

function SignInToSaveLink({ resourceName, className }: { resourceName: string; className?: string }) {
  // Reads the current page only after mount — reading window during render
  // would make the client's first paint diverge from the server-rendered
  // "/login" fallback (a hydration mismatch), the same reasoning
  // map-search-context.tsx's own URL-restoration effect documents. This is
  // the intentional fix-up-after-mount half of that trade-off, not a
  // mirrored-state anti-pattern.
  const [href, setHref] = useState("/login");
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    setHref(buildLoginHref(window.location.pathname + window.location.search));
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  return (
    <Link
      href={href}
      className={
        className ??
        "rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      }
      aria-label={`Sign in to save ${resourceName}`}
    >
      Sign in to save
    </Link>
  );
}

function AuthenticatedSaveButton({
  resourceId,
  resourceName,
  isSaved,
  className,
}: {
  resourceId: string;
  resourceName: string;
  isSaved: boolean;
  className?: string;
}) {
  const saveMutation = useSaveResourceMutation();
  const removeMutation = useRemoveSavedResourceMutation();
  const isPending = saveMutation.isPending || removeMutation.isPending;
  const hasError = saveMutation.isError || removeMutation.isError;

  function handleClick() {
    if (isPending) return; // Guards against a duplicate request from a fast repeated click.
    if (isSaved) {
      removeMutation.mutate(resourceId);
    } else {
      saveMutation.mutate(resourceId);
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={handleClick}
        disabled={isPending}
        aria-pressed={isSaved}
        aria-label={isSaved ? `Remove ${resourceName} from saved resources` : `Save ${resourceName}`}
        className={
          className ??
          `rounded-md border px-3 py-1.5 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-60 ${
            isSaved
              ? "border-blue-600 bg-blue-50 text-blue-700 hover:bg-blue-100"
              : "border-slate-300 text-slate-700 hover:bg-slate-50"
          }`
        }
      >
        {isPending ? "Saving…" : isSaved ? "Saved ✓" : "Save"}
      </button>
      {hasError && (
        <p role="alert" className="text-xs text-red-700">
          {isSaved ? "Couldn't remove this resource. Try again." : "Couldn't save this resource. Try again."}
        </p>
      )}
    </div>
  );
}
