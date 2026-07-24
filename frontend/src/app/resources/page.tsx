import { HydrationBoundary, dehydrate } from "@tanstack/react-query";
import type { Metadata } from "next";
import { getQueryClient } from "@/lib/query/get-query-client";
import { categoryKeys, resourceKeys } from "@/lib/query/keys";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import { parseResourceListParams, type RawSearchParams } from "@/lib/query/resource-list-params";
import { RESOURCE_LIST_PAGE_SIZE } from "@/lib/constants/resources";
import { ResourceListView } from "@/components/resources/resource-list-view";

export const metadata: Metadata = {
  title: "Browse resources | HFX Connect",
  description: "Browse active Halifax community resources by category, filterable and sortable.",
};

export default async function ResourcesPage({ searchParams }: { searchParams: Promise<RawSearchParams> }) {
  const params = parseResourceListParams(await searchParams);
  const queryClient = getQueryClient();

  // prefetchQuery does not throw on failure — it stores the error in the
  // cache for useQuery to surface client-side, which is exactly the
  // error-state behavior this page wants.
  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: resourceKeys.list({
        page: params.page,
        size: RESOURCE_LIST_PAGE_SIZE,
        categoryId: params.categoryId,
        sort: params.sort,
      }),
      queryFn: () =>
        getResources({
          page: params.page,
          size: RESOURCE_LIST_PAGE_SIZE,
          categoryId: params.categoryId,
          sort: params.sort,
        }),
    }),
    queryClient.prefetchQuery({
      queryKey: categoryKeys.list({ active: true }),
      queryFn: () => getCategories({ active: true, size: 50 }),
    }),
  ]);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ResourceListView params={params} />
    </HydrationBoundary>
  );
}
