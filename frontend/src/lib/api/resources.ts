import { getJson } from "./client";
import {
  resourcePageResponseSchema,
  resourceResponseSchema,
  type ResourcePageResponse,
  type ResourceResponse,
} from "@/lib/validation/schemas";
import { RESOURCE_LIST_PAGE_SIZE } from "@/lib/constants/resources";

export interface GetResourcesParams {
  page?: number;
  size?: number;
  categoryId?: number;
  sort?: string;
  signal?: AbortSignal;
}

export function getResources(params: GetResourcesParams = {}): Promise<ResourcePageResponse> {
  const { page = 0, size = RESOURCE_LIST_PAGE_SIZE, categoryId, sort, signal } = params;
  return getJson("/api/v1/resources", resourcePageResponseSchema, {
    params: { page, size, categoryId, sort },
    signal,
  });
}

export function getResourceBySlug(slug: string, signal?: AbortSignal): Promise<ResourceResponse> {
  return getJson(`/api/v1/resources/slug/${encodeURIComponent(slug)}`, resourceResponseSchema, { signal });
}
