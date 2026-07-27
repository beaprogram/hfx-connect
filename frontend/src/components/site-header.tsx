import Link from "next/link";
import { MobileNav } from "@/components/navigation/mobile-nav";
import { AuthNav } from "@/components/auth/auth-nav";

export function SiteHeader() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4 sm:px-6">
        <Link
          href="/"
          className="rounded-sm text-lg font-semibold tracking-tight text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          HFX Connect
        </Link>
        <nav aria-label="Primary" className="hidden items-center gap-6 sm:flex">
          <Link
            href="/resources"
            className="rounded-sm text-sm font-medium text-slate-700 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Browse resources
          </Link>
          <AuthNav />
        </nav>
        <MobileNav />
      </div>
    </header>
  );
}
