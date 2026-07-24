import Link from "next/link";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import { CategoryGrid } from "@/components/categories/category-grid";
import { ResourceGrid } from "@/components/resources/resource-grid";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import { RESOURCE_PREVIEW_SIZE } from "@/lib/constants/resources";
import type { CategoryResponse, ResourceSummaryResponse } from "@/lib/validation/schemas";

/**
 * Independent per-section data fetching (Promise.allSettled, not sequential
 * awaits): a failure loading categories must not block the resource preview
 * from rendering, and vice versa — see docs/wireframes/homepage.md.
 */
export default async function Home() {
  const [categoriesResult, resourcesResult] = await Promise.allSettled([
    getCategories({ active: true, size: 50 }),
    getResources({ size: RESOURCE_PREVIEW_SIZE, sort: "createdAt" }),
  ]);

  const categories: { status: "success"; categories: CategoryResponse[] } | { status: "error" } =
    categoriesResult.status === "fulfilled"
      ? { status: "success", categories: categoriesResult.value.content }
      : { status: "error" };

  const resources: { status: "success"; resources: ResourceSummaryResponse[] } | { status: "error" } =
    resourcesResult.status === "fulfilled"
      ? { status: "success", resources: resourcesResult.value.content }
      : { status: "error" };

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-16 px-4 py-12 sm:px-6 sm:py-16">
      <section>
        <p className="text-sm font-medium uppercase tracking-wide text-blue-700">Halifax, Nova Scotia</p>
        <h1 className="mt-3 max-w-2xl text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">
          Find trustworthy Halifax community resources
        </h1>
        <p className="mt-4 max-w-2xl text-lg leading-relaxed text-slate-600">
          HFX Connect brings together food assistance, study spaces, employment support, newcomer
          services, and recreation programs into one searchable directory — so you don&apos;t have to
          piece it together from a dozen different websites.
        </p>
        <Link
          href="/resources"
          className="mt-6 inline-block rounded-md bg-blue-700 px-5 py-3 text-sm font-semibold text-white hover:bg-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          Browse all resources
        </Link>
      </section>

      <section>
        <h2 className="text-xl font-semibold text-slate-900">Browse by category</h2>
        <div className="mt-4">
          <CategoryGrid result={categories} />
        </div>
      </section>

      <section>
        <h2 className="text-xl font-semibold text-slate-900">Recently added</h2>
        <div className="mt-4">
          {resources.status === "error" && <InlineError message="Recently added resources couldn't be loaded right now." />}
          {resources.status === "success" && resources.resources.length === 0 && (
            <EmptyState heading="No active resources are published yet.">
              Check back soon, or <Link href="/resources" className="underline underline-offset-2">browse all resources</Link>.
            </EmptyState>
          )}
          {resources.status === "success" && resources.resources.length > 0 && (
            <>
              <ResourceGrid resources={resources.resources} />
              <Link
                href="/resources"
                className="mt-4 inline-block rounded-sm text-sm font-medium text-blue-700 underline underline-offset-2 hover:text-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
              >
                View all resources
              </Link>
            </>
          )}
        </div>
      </section>

      <section>
        <h2 className="text-xl font-semibold text-slate-900">How verification works</h2>
        <p className="mt-4 max-w-2xl text-base leading-relaxed text-slate-600">
          Every listing carries a verification status, shown on its own page. Most listings start{" "}
          <strong className="font-medium text-slate-800">Not yet verified</strong> — publishing a
          listing does not automatically mean it has been checked for accuracy. A structured
          moderation and correction workflow that reviews and updates that status is planned for a
          later milestone, not available yet.
        </p>
      </section>
    </div>
  );
}
