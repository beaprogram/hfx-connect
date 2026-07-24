import { ResourceListSkeleton } from "@/components/resources/resource-list-skeleton";

export default function Loading() {
  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-12 sm:px-6">
      <div className="h-8 w-64 rounded bg-slate-200" aria-hidden="true" />
      <div className="mt-3 h-4 w-96 max-w-full rounded bg-slate-100" aria-hidden="true" />
      <div className="mt-8">
        <ResourceListSkeleton />
      </div>
    </div>
  );
}
