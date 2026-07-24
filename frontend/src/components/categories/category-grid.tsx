import { CategoryCard } from "@/components/categories/category-card";
import { EmptyState } from "@/components/feedback/empty-state";
import { InlineError } from "@/components/feedback/inline-error";
import type { CategoryResponse } from "@/lib/validation/schemas";

export function CategoryGrid({
  result,
}: {
  result: { status: "success"; categories: CategoryResponse[] } | { status: "error" };
}) {
  if (result.status === "error") {
    return <InlineError message="Categories couldn't be loaded right now." />;
  }

  if (result.categories.length === 0) {
    return <EmptyState heading="Categories aren't available yet." />;
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {result.categories.map((category) => (
        <CategoryCard key={category.id} category={category} />
      ))}
    </div>
  );
}
