"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createResourceSubmission,
  getResourceSubmissions,
  getResourceSubmission,
  withdrawResourceSubmission,
  type CreateResourceSubmissionInput,
} from "@/lib/api/resource-submissions";
import { resourceSubmissionKeys } from "@/lib/query/keys";
import { useAuth } from "@/lib/auth/auth-provider";

/** The current user's resource-submission list — enabled only while authenticated. */
export function useResourceSubmissionsQuery(page: number, size: number, sort?: string) {
  const { state, getValidAccessToken } = useAuth();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useQuery({
    enabled: userId !== null,
    queryKey: resourceSubmissionKeys.list(userId ?? "", { page, size, sort }),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return getResourceSubmissions({ page, size, sort, accessToken: token, signal });
    },
  });
}

/** One owned submission's detail — enabled only while authenticated and a real id is known. */
export function useResourceSubmissionQuery(submissionId: string | null) {
  const { state, getValidAccessToken } = useAuth();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useQuery({
    enabled: userId !== null && submissionId !== null,
    queryKey: resourceSubmissionKeys.detail(userId ?? "", submissionId ?? ""),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token || !submissionId) {
        throw new Error("Not authenticated.");
      }
      return getResourceSubmission(submissionId, token, signal);
    },
  });
}

/** Creates a new pending submission. Never claims publication — the backend always starts it PENDING_REVIEW. */
export function useCreateResourceSubmissionMutation() {
  const { state, getValidAccessToken } = useAuth();
  const queryClient = useQueryClient();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useMutation({
    mutationFn: async (input: CreateResourceSubmissionInput) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return createResourceSubmission(input, token);
    },
    onSuccess: () => {
      if (userId) {
        queryClient.invalidateQueries({ queryKey: [...resourceSubmissionKeys.all(userId), "list"] });
      }
    },
  });
}

/** Withdraws an owned, still-pending submission. */
export function useWithdrawResourceSubmissionMutation() {
  const { state, getValidAccessToken } = useAuth();
  const queryClient = useQueryClient();
  const userId = state.status === "authenticated" ? state.user.id : null;

  return useMutation({
    mutationFn: async (submissionId: string) => {
      const token = await getValidAccessToken();
      if (!token) {
        throw new Error("Not authenticated.");
      }
      return withdrawResourceSubmission(submissionId, token);
    },
    onSuccess: (response) => {
      if (userId) {
        queryClient.setQueryData(resourceSubmissionKeys.detail(userId, response.id), response);
        queryClient.invalidateQueries({ queryKey: [...resourceSubmissionKeys.all(userId), "list"] });
      }
    },
  });
}
