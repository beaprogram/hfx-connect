"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createCorrectionReport,
  getCorrectionReports,
  getCorrectionReport,
  withdrawCorrectionReport,
  type CreateCorrectionReportInput,
} from "@/lib/api/correction-reports";
import { correctionReportKeys } from "@/lib/query/keys";
import { useAuth } from "@/lib/auth/auth-provider";

/** The current user's correction-report list — enabled only while authenticated. */
export function useCorrectionReportsQuery(page: number, size: number, sort?: string) {
  const { state, getValidAccessToken } = useAuth();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useQuery({
    enabled: userId !== null,
    queryKey: correctionReportKeys.list(userId ?? "", { page, size, sort }),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return getCorrectionReports({ page, size, sort, accessToken: token, signal });
    },
  });
}

/** One owned report's detail — enabled only while authenticated and a real id is known. */
export function useCorrectionReportQuery(reportId: string | null) {
  const { state, getValidAccessToken } = useAuth();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useQuery({
    enabled: userId !== null && reportId !== null,
    queryKey: correctionReportKeys.detail(userId ?? "", reportId ?? ""),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token || !reportId) {
        throw new Error("Not authenticated.");
      }
      return getCorrectionReport(reportId, token, signal);
    },
  });
}

/** Creates a new pending correction report against an active resource. Never claims to modify the resource itself. */
export function useCreateCorrectionReportMutation() {
  const { state, getValidAccessToken } = useAuth();
  const queryClient = useQueryClient();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useMutation({
    mutationFn: async ({ resourceId, input }: { resourceId: string; input: CreateCorrectionReportInput }) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return createCorrectionReport(resourceId, input, token);
    },
    onSuccess: () => {
      if (userId) {
        queryClient.invalidateQueries({ queryKey: [...correctionReportKeys.all(userId), "list"] });
      }
    },
  });
}

/** Withdraws an owned, still-pending correction report. */
export function useWithdrawCorrectionReportMutation() {
  const { state, getValidAccessToken } = useAuth();
  const queryClient = useQueryClient();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useMutation({
    mutationFn: async (reportId: string) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return withdrawCorrectionReport(reportId, token);
    },
    onSuccess: (response) => {
      if (userId) {
        queryClient.setQueryData(correctionReportKeys.detail(userId, response.id), response);
        queryClient.invalidateQueries({ queryKey: [...correctionReportKeys.all(userId), "list"] });
      }
    },
  });
}
