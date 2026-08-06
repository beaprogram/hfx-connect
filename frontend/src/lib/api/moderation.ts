import { getJson, postJson } from "./client";
import {
  moderationSubmissionQueuePageSchema,
  moderationSubmissionDetailSchema,
  moderationCorrectionQueuePageSchema,
  moderationCorrectionDetailSchema,
  moderationAuditEventPageSchema,
  type ModerationSubmissionQueuePage,
  type ModerationSubmissionDetail,
  type ModerationCorrectionQueuePage,
  type ModerationCorrectionDetail,
  type ModerationAuditEventPage,
  type ContributionType,
  type ModerationDecision,
} from "@/lib/validation/schemas";

/**
 * The moderator/admin-only moderation API (Milestone 9A) — every function
 * requires an `accessToken` and hits `/api/v1/moderation/**`, which the
 * backend restricts to a current MODERATOR/ADMIN account regardless of what
 * this client sends (ADR-016: backend authorization is the security
 * boundary, this client is not). No function here accepts a moderator's own
 * identity or a resulting status as an input — those are always the
 * backend's own decision.
 */

export interface ModerationQueueParams {
  status?: string;
  page?: number;
  size?: number;
  sort?: string;
  accessToken: string;
  signal?: AbortSignal;
}

export interface ResourceSubmissionQueueParams extends ModerationQueueParams {
  categoryId?: number;
}

export function getResourceSubmissionQueue(params: ResourceSubmissionQueueParams): Promise<ModerationSubmissionQueuePage> {
  const { status, categoryId, page = 0, size = 20, sort, accessToken, signal } = params;
  return getJson("/api/v1/moderation/resource-submissions", moderationSubmissionQueuePageSchema, {
    params: { status, categoryId, page, size, sort },
    accessToken,
    signal,
  });
}

export function getResourceSubmissionForModeration(
  submissionId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ModerationSubmissionDetail> {
  return getJson(`/api/v1/moderation/resource-submissions/${encodeURIComponent(submissionId)}`, moderationSubmissionDetailSchema, {
    accessToken,
    signal,
  });
}

export function approveResourceSubmission(
  submissionId: string,
  reason: string,
  accessToken: string,
): Promise<ModerationSubmissionDetail> {
  return postJson(
    `/api/v1/moderation/resource-submissions/${encodeURIComponent(submissionId)}/approve`,
    moderationSubmissionDetailSchema,
    { body: { reason }, accessToken },
  );
}

export function rejectResourceSubmission(
  submissionId: string,
  reason: string,
  accessToken: string,
): Promise<ModerationSubmissionDetail> {
  return postJson(
    `/api/v1/moderation/resource-submissions/${encodeURIComponent(submissionId)}/reject`,
    moderationSubmissionDetailSchema,
    { body: { reason }, accessToken },
  );
}

export interface CorrectionReportQueueParams extends ModerationQueueParams {
  issueType?: string;
}

export function getCorrectionReportQueue(params: CorrectionReportQueueParams): Promise<ModerationCorrectionQueuePage> {
  const { status, issueType, page = 0, size = 20, sort, accessToken, signal } = params;
  return getJson("/api/v1/moderation/correction-reports", moderationCorrectionQueuePageSchema, {
    params: { status, issueType, page, size, sort },
    accessToken,
    signal,
  });
}

export function getCorrectionReportForModeration(
  reportId: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ModerationCorrectionDetail> {
  return getJson(`/api/v1/moderation/correction-reports/${encodeURIComponent(reportId)}`, moderationCorrectionDetailSchema, {
    accessToken,
    signal,
  });
}

export interface ApproveCorrectionReportInput {
  reason: string;
  applyProposedChanges: boolean;
  deactivateResource: boolean;
}

export function approveCorrectionReport(
  reportId: string,
  input: ApproveCorrectionReportInput,
  accessToken: string,
): Promise<ModerationCorrectionDetail> {
  return postJson(`/api/v1/moderation/correction-reports/${encodeURIComponent(reportId)}/approve`, moderationCorrectionDetailSchema, {
    body: input,
    accessToken,
  });
}

export function rejectCorrectionReport(
  reportId: string,
  reason: string,
  accessToken: string,
): Promise<ModerationCorrectionDetail> {
  return postJson(`/api/v1/moderation/correction-reports/${encodeURIComponent(reportId)}/reject`, moderationCorrectionDetailSchema, {
    body: { reason },
    accessToken,
  });
}

export interface ContributionAuditEventsParams {
  page?: number;
  size?: number;
  accessToken: string;
  signal?: AbortSignal;
}

export function getResourceSubmissionAuditEvents(
  submissionId: string,
  params: ContributionAuditEventsParams,
): Promise<ModerationAuditEventPage> {
  const { page = 0, size = 20, accessToken, signal } = params;
  return getJson(
    `/api/v1/moderation/resource-submissions/${encodeURIComponent(submissionId)}/audit-events`,
    moderationAuditEventPageSchema,
    { params: { page, size }, accessToken, signal },
  );
}

export function getCorrectionReportAuditEvents(
  reportId: string,
  params: ContributionAuditEventsParams,
): Promise<ModerationAuditEventPage> {
  const { page = 0, size = 20, accessToken, signal } = params;
  return getJson(
    `/api/v1/moderation/correction-reports/${encodeURIComponent(reportId)}/audit-events`,
    moderationAuditEventPageSchema,
    { params: { page, size }, accessToken, signal },
  );
}

export interface GlobalAuditEventsParams {
  contributionType?: ContributionType;
  decision?: ModerationDecision;
  page?: number;
  size?: number;
  accessToken: string;
  signal?: AbortSignal;
}

export function getGlobalAuditEvents(params: GlobalAuditEventsParams): Promise<ModerationAuditEventPage> {
  const { contributionType, decision, page = 0, size = 20, accessToken, signal } = params;
  return getJson("/api/v1/moderation/audit-events", moderationAuditEventPageSchema, {
    params: { contributionType, decision, page, size },
    accessToken,
    signal,
  });
}
