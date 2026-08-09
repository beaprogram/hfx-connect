import Link from "next/link";

const linkClassName =
  "rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600";

/** Sub-navigation across the four `/organization/**` routes (Milestone 10A). */
export function OrganizationNav() {
  return (
    <nav aria-label="Organization" className="flex flex-wrap gap-1 border-b border-slate-200 pb-2">
      <Link href="/organization" className={linkClassName}>
        Overview
      </Link>
      <Link href="/organization/profile" className={linkClassName}>
        Profile
      </Link>
      <Link href="/organization/resources" className={linkClassName}>
        Resources
      </Link>
      <Link href="/organization/claims" className={linkClassName}>
        Claims
      </Link>
    </nav>
  );
}
