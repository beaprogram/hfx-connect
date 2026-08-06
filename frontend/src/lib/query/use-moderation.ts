"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getResourceSubmissionQueue,
  getResourceSubmissionForModeration,
  approveResourceSubmission,
  rejectResourceSubmission,
  getCorrectionReportQueue,
  getCorrectionReportForModeration,
  approveCorrectionReport,
  rejectCorrectionReport,
  getResourceSubmissionAuditEvents,
  getCorrectionReportAuditEvents,
  getGlobalAuditEvents,
  type ApproveCorrectionReportInput,
} from "@/lib/api/moderation";
import { moderationKeys } from "@/lib/query/keys";
import { useAuth } from "@/lib/auth/auth-provider";
import type { ContributionType, ModerationDecision } from "@/lib/validation/schemas";

/** MODERATOR/ADMIN only — see ADR-016. This is a UX gate; the backend is the real authorization boundary. */
function isModerator(role: string | undefined): boolean {
  return role === "MODERATOR" || role === "ADMIN";
}

function useModeratorAuth() {
  const { state, getValidAccessToken } = useAuth();
  const authorized = state.status === "authenticated" && isModerator(state.user.role);
  return { authorized, getValidAccessToken };
}

export function useResourceSubmissionQueueQuery(
	params: { status?: string; categoryId?: number; page: number; size: number; sort?: string },
) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized,
		queryKey: moderationKeys.submissionQueue(params),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return getResourceSubmissionQueue({ ...params, accessToken: token, signal });
		},
	});
}

export function useResourceSubmissionModerationDetailQuery(submissionId: string | null) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized && submissionId !== null,
		queryKey: moderationKeys.submissionDetail(submissionId ?? ""),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token || !submissionId) throw new Error("Not authenticated.");
			return getResourceSubmissionForModeration(submissionId, token, signal);
		},
	});
}

export function useApproveResourceSubmissionMutation() {
	const { getValidAccessToken } = useModeratorAuth();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async ({ submissionId, reason }: { submissionId: string; reason: string }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return approveResourceSubmission(submissionId, reason, token);
		},
		onSuccess: (response, { submissionId }) => {
			queryClient.setQueryData(moderationKeys.submissionDetail(submissionId), response);
			queryClient.invalidateQueries({ queryKey: ["moderation", "resource-submissions"] });
			queryClient.invalidateQueries({ queryKey: ["resources"] });
			queryClient.invalidateQueries({ queryKey: ["resource-submissions"] });
		},
	});
}

export function useRejectResourceSubmissionMutation() {
	const { getValidAccessToken } = useModeratorAuth();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async ({ submissionId, reason }: { submissionId: string; reason: string }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return rejectResourceSubmission(submissionId, reason, token);
		},
		onSuccess: (response, { submissionId }) => {
			queryClient.setQueryData(moderationKeys.submissionDetail(submissionId), response);
			queryClient.invalidateQueries({ queryKey: ["moderation", "resource-submissions"] });
			queryClient.invalidateQueries({ queryKey: ["resource-submissions"] });
		},
	});
}

export function useCorrectionReportQueueQuery(
	params: { status?: string; issueType?: string; page: number; size: number; sort?: string },
) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized,
		queryKey: moderationKeys.correctionQueue(params),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return getCorrectionReportQueue({ ...params, accessToken: token, signal });
		},
	});
}

export function useCorrectionReportModerationDetailQuery(reportId: string | null) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized && reportId !== null,
		queryKey: moderationKeys.correctionDetail(reportId ?? ""),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token || !reportId) throw new Error("Not authenticated.");
			return getCorrectionReportForModeration(reportId, token, signal);
		},
	});
}

export function useApproveCorrectionReportMutation() {
	const { getValidAccessToken } = useModeratorAuth();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async ({ reportId, input }: { reportId: string; input: ApproveCorrectionReportInput }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return approveCorrectionReport(reportId, input, token);
		},
		onSuccess: (response, { reportId }) => {
			queryClient.setQueryData(moderationKeys.correctionDetail(reportId), response);
			queryClient.invalidateQueries({ queryKey: ["moderation", "correction-reports"] });
			queryClient.invalidateQueries({ queryKey: ["resources"] });
			queryClient.invalidateQueries({ queryKey: ["correction-reports"] });
			queryClient.invalidateQueries({ queryKey: ["saved-resources"] });
		},
	});
}

export function useRejectCorrectionReportMutation() {
	const { getValidAccessToken } = useModeratorAuth();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async ({ reportId, reason }: { reportId: string; reason: string }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return rejectCorrectionReport(reportId, reason, token);
		},
		onSuccess: (response, { reportId }) => {
			queryClient.setQueryData(moderationKeys.correctionDetail(reportId), response);
			queryClient.invalidateQueries({ queryKey: ["moderation", "correction-reports"] });
			queryClient.invalidateQueries({ queryKey: ["correction-reports"] });
		},
	});
}

export function useResourceSubmissionAuditEventsQuery(submissionId: string | null, page: number, size: number) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized && submissionId !== null,
		queryKey: moderationKeys.submissionAudit(submissionId ?? "", { page, size }),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token || !submissionId) throw new Error("Not authenticated.");
			return getResourceSubmissionAuditEvents(submissionId, { page, size, accessToken: token, signal });
		},
	});
}

export function useCorrectionReportAuditEventsQuery(reportId: string | null, page: number, size: number) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized && reportId !== null,
		queryKey: moderationKeys.correctionAudit(reportId ?? "", { page, size }),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token || !reportId) throw new Error("Not authenticated.");
			return getCorrectionReportAuditEvents(reportId, { page, size, accessToken: token, signal });
		},
	});
}

export function useGlobalAuditEventsQuery(
	params: { contributionType?: ContributionType; decision?: ModerationDecision; page: number; size: number },
) {
	const { authorized, getValidAccessToken } = useModeratorAuth();
	return useQuery({
		enabled: authorized,
		queryKey: moderationKeys.globalAudit(params),
		queryFn: async ({ signal }) => {
			const token = await getValidAccessToken();
			if (!token) throw new Error("Not authenticated.");
			return getGlobalAuditEvents({ ...params, accessToken: token, signal });
		},
	});
}
