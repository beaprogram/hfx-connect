import { getJson, postJson } from "./client";
import {
  resourceSubmissionResponseSchema,
  resourceSubmissionPageResponseSchema,
  type ResourceSubmissionResponse,
  type ResourceSubmissionPageResponse,
} from "@/lib/validation/schemas";
import type { CostType } from "@/lib/validation/schemas";

/**
 * The current authenticated user's resource submissions (Milestone 8B).
 * Every function requires an `accessToken` and hits a `/users/me/...`
 * endpoint — there is no operation anywhere that accepts a user id, the
 * same identity model `lib/api/saved-resources.ts` already established
 * (Milestone 8A). Submitting never creates a public resource; it creates a
 * PENDING_REVIEW item visible only to its owner.
 */

export interface CreateResourceSubmissionInput {
  categoryId: number;
  name: string;
  shortDescription: string;
  fullDescription?: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  province: string;
  postalCode: string;
  phone?: string;
  email?: string;
  websiteUrl?: string;
  costType?: CostType;
  eligibilityInformation?: string;
  accessibilityInformation?: string;
}

export function createResourceSubmission(
  input: CreateResourceSubmissionInput,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ResourceSubmissionResponse> {
  return postJson("/api/v1/users/me/resource-submissions", resourceSubmissionResponseSchema, {
    body: input,
    accessToken,
    signal,
  });
}

export interface GetResourceSubmissionsParams {
  page?: number;
  size?: number;
  /** "submittedAt" (default, newest first), "updatedAt", or "status" — any other value is rejected by the backend with 400 INVALID_SORT. */
  sort?: string;
  accessToken: string;
  signal?: AbortSignal;
}

export function getResourceSubmissions(params: GetResourceSubmissionsParams): Promise<ResourceSubmissionPageResponse> {
  const { page = 0, size = 20, sort, accessToken, signal } = params;
  return getJson("/api/v1/users/me/resource-submissions", resourceSubmissionPageResponseSchema, {
    params: { page, size, sort },
    accessToken,
    signal,
  });
}

export function getResourceSubmission(
  submissionId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ResourceSubmissionResponse> {
  return getJson(`/api/v1/users/me/resource-submissions/${encodeURIComponent(submissionId)}`, resourceSubmissionResponseSchema, {
    accessToken,
    signal,
  });
}

/** Only legal while the submission is still PENDING_REVIEW — the backend rejects an already-resolved one with 400 INVALID_CONTRIBUTION_STATUS. */
export function withdrawResourceSubmission(
  submissionId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ResourceSubmissionResponse> {
  return postJson(
    `/api/v1/users/me/resource-submissions/${encodeURIComponent(submissionId)}/withdraw`,
    resourceSubmissionResponseSchema,
    { accessToken, signal },
  );
}
