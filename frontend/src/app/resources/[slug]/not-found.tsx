import Link from "next/link";

/**
 * Shown for both a genuinely nonexistent slug and a deactivated resource —
 * the public API returns 404 for both identically, so the frontend has no
 * way to (and no need to) distinguish them. See
 * docs/wireframes/resource-detail.md.
 */
export default function ResourceNotFound() {
  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-16 text-center sm:px-6">
      <h1 className="text-xl font-semibold text-slate-900">Resource not found</h1>
      <p className="mt-2 text-base text-slate-600">
        This resource doesn&apos;t exist, or is no longer active.
      </p>
      <Link
        href="/resources"
        className="mt-6 inline-block rounded-md bg-blue-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Browse all resources
      </Link>
    </div>
  );
}
