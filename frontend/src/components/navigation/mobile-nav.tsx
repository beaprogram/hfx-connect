"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth/auth-provider";

const linkClassName =
  "block rounded-sm py-2 text-base font-medium text-slate-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

/**
 * A disclosure-pattern mobile menu (see docs/wireframes/mobile-navigation.md).
 * Only rendered below the desktop breakpoint by CSS (`sm:hidden` on the
 * wrapper) — SiteHeader's own inline nav link covers desktop widths, so
 * there is never a duplicate hidden nav in the DOM.
 */
export function MobileNav() {
  const [open, setOpen] = useState(false);
  const toggleButtonRef = useRef<HTMLButtonElement>(null);
  const firstLinkRef = useRef<HTMLAnchorElement>(null);
  const { state, logout } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (open) {
      firstLinkRef.current?.focus();
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
        toggleButtonRef.current?.focus();
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open]);

  return (
    <div className="sm:hidden">
      <button
        ref={toggleButtonRef}
        type="button"
        aria-expanded={open}
        aria-controls="mobile-nav-panel"
        onClick={() => setOpen((value) => !value)}
        className="rounded-sm border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        {open ? "Close menu" : "Open menu"}
      </button>
      {open && (
        <nav id="mobile-nav-panel" aria-label="Mobile" className="border-t border-slate-200 bg-white px-4 py-3">
          <Link ref={firstLinkRef} href="/resources" onClick={() => setOpen(false)} className={linkClassName}>
            Browse resources
          </Link>
          {state.status === "authenticated" && (
            <>
              <Link href="/dashboard" onClick={() => setOpen(false)} className={linkClassName}>
                Dashboard
              </Link>
              <button
                type="button"
                onClick={async () => {
                  setOpen(false);
                  await logout();
                  router.push("/");
                }}
                className={`${linkClassName} w-full text-left`}
              >
                Log out
              </button>
            </>
          )}
          {state.status === "unauthenticated" && (
            <Link href="/login" onClick={() => setOpen(false)} className={linkClassName}>
              Log in
            </Link>
          )}
        </nav>
      )}
    </div>
  );
}
