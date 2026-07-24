import { getJson } from "./client";
import { categoryPageResponseSchema, type CategoryPageResponse } from "@/lib/validation/schemas";

export interface GetCategoriesParams {
  page?: number;
  size?: number;
  /** Omit to return categories in any state; the frontend always passes `true` for public browsing. */
  active?: boolean;
  signal?: AbortSignal;
}

export function getCategories(params: GetCategoriesParams = {}): Promise<CategoryPageResponse> {
  const { page = 0, size = 50, active, signal } = params;
  return getJson("/api/v1/categories", categoryPageResponseSchema, {
    params: { page, size, active },
    signal,
  });
}
