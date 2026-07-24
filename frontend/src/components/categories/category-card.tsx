import Link from "next/link";
import type { CategoryResponse } from "@/lib/validation/schemas";

export function CategoryCard({ category }: { category: CategoryResponse }) {
  return (
    <article className="relative rounded-lg border border-slate-200 p-4 transition-colors hover:border-slate-300">
      <h3 className="text-base font-semibold text-slate-900">
        <Link
          href={`/resources?categoryId=${category.id}`}
          className="rounded-sm after:absolute after:inset-0 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          {category.name}
        </Link>
      </h3>
      {category.description && <p className="mt-1 text-sm text-slate-600">{category.description}</p>}
    </article>
  );
}
