import { getJson, postJson } from "./client";
import {
  correctionReportResponseSchema,
  correctionReportPageResponseSchema,
  type CorrectionReportResponse,
  type CorrectionReportPageResponse,
  type IssueType,
  type CostType,
} from "@/lib/validation/schemas";

/**
 * Reporting an issue on an existing resource, and the current authenticated
 * user's own correction reports (Milestone 8B). Every function requires an
 * `accessToken` — there is no operation anywhere that accepts a user id.
 * Submitting never modifies the target resource; it creates a
 * PENDING_REVIEW item visible only to its owner.
 */

export interface CreateCorrectionReportInput {
  issueType: IssueType;
  explanation: string;
  proposedName?: string;
  proposedDescription?: string;
  proposedAddressLine1?: string;
  proposedAddressLine2?: string;
  proposedCity?: string;
  proposedProvince?: string;
  proposedPostalCode?: string;
  proposedPhone?: string;
  proposedEmail?: string;
  proposedWebsiteUrl?: string;
  proposedCostType?: CostType;
  proposedCostDetails?: string;
  proposedEligibility?: string;
}

export function createCorrectionReport(
  resourceId: string,
  input: CreateCorrectionReportInput,
  accessToken: string,
  signal?: AbortSignal,
): Promise<CorrectionReportResponse> {
  return postJson(`/api/v1/resources/${encodeURIComponent(resourceId)}/correction-reports`, correctionReportResponseSchema, {
    body: input,
    accessToken,
    signal,
  });
}

export interface GetCorrectionReportsParams {
  page?: number;
  size?: number;
  /** "submittedAt" (default, newest first), "updatedAt", or "status" — any other value is rejected by the backend with 400 INVALID_SORT. */
  sort?: string;
  accessToken: string;
  signal?: AbortSignal;
}

export function getCorrectionReports(params: GetCorrectionReportsParams): Promise<CorrectionReportPageResponse> {
  const { page = 0, size = 20, sort, accessToken, signal } = params;
  return getJson("/api/v1/users/me/correction-reports", correctionReportPageResponseSchema, {
    params: { page, size, sort },
    accessToken,
    signal,
  });
}

export function getCorrectionReport(
  reportId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<CorrectionReportResponse> {
  return getJson(`/api/v1/users/me/correction-reports/${encodeURIComponent(reportId)}`, correctionReportResponseSchema, {
    accessToken,
    signal,
  });
}

/** Only legal while the report is still PENDING_REVIEW. */
export function withdrawCorrectionReport(
  reportId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<CorrectionReportResponse> {
  return postJson(
    `/api/v1/users/me/correction-reports/${encodeURIComponent(reportId)}/withdraw`,
    correctionReportResponseSchema,
    { accessToken, signal },
  );
}
