"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createOrganizationProfile,
  getOrganizationProfile,
  updateOrganizationProfile,
  getPublicOrganization,
  claimResource,
  getOrganizationClaims,
  withdrawOwnershipClaim,
  getOrganizationResources,
  getAdminOrganizationQueue,
  getAdminOrganization,
  verifyOrganization,
  rejectOrganization,
  suspendOrganization,
  getOrganizationAuditEvents,
  getAdminOwnershipClaimQueue,
  getAdminOwnershipClaim,
  approveOwnershipClaim,
  rejectOwnershipClaim,
  type OrganizationProfileInput,
} from "@/lib/api/organization";
import { organizationKeys, publicOrganizationKeys, adminOrganizationKeys, adminOwnershipClaimKeys } from "@/lib/query/keys";
import { useAuth } from "@/lib/auth/auth-provider";
import type { OwnershipClaimStatus, OrganizationVerificationStatus } from "@/lib/validation/schemas";

/** ORGANIZATION role only — a UX gate, not the security boundary; see ADR-017. */
function isOrganization(role: string | undefined): boolean {
  return role === "ORGANIZATION";
}

/** ADMIN only — organization verification/claim review is deliberately not extended to MODERATOR (ADR-017). */
function isAdmin(role: string | undefined): boolean {
  return role === "ADMIN";
}

function useOrganizationAuth() {
  const { state, getValidAccessToken } = useAuth();
  const userId = state.status === "authenticated" ? state.user.id : null;
  const authorized = state.status === "authenticated" && isOrganization(state.user.role);
  return { authorized, userId, getValidAccessToken };
}

function useAdminAuth() {
  const { state, getValidAccessToken } = useAuth();
  const authorized = state.status === "authenticated" && isAdmin(state.user.role);
  return { authorized, getValidAccessToken };
}

/* -------------------------------------------------------------------------- */
/* Organization self-service                                                  */
/* -------------------------------------------------------------------------- */

export function useOrganizationProfileQuery() {
  const { authorized, userId, getValidAccessToken } = useOrganizationAuth();
  return useQuery({
    enabled: authorized && userId !== null,
    queryKey: organizationKeys.profile(userId ?? ""),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return getOrganizationProfile(token, signal);
    },
    // A 404 here just means "no profile yet" — not worth retrying.
    retry: false,
  });
}

export function useCreateOrganizationProfileMutation() {
  const { userId, getValidAccessToken } = useOrganizationAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: OrganizationProfileInput) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return createOrganizationProfile(input, token);
    },
    onSuccess: (response) => {
      if (userId) {
        queryClient.setQueryData(organizationKeys.profile(userId), response);
      }
    },
  });
}

export function useUpdateOrganizationProfileMutation() {
  const { userId, getValidAccessToken } = useOrganizationAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: OrganizationProfileInput) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return updateOrganizationProfile(input, token);
    },
    onSuccess: (response) => {
      if (userId) {
        queryClient.setQueryData(organizationKeys.profile(userId), response);
      }
      queryClient.invalidateQueries({ queryKey: publicOrganizationKeys.detail(response.slug) });
    },
  });
}

export function usePublicOrganizationQuery(slug: string | null) {
  return useQuery({
    enabled: slug !== null,
    queryKey: publicOrganizationKeys.detail(slug ?? ""),
    queryFn: ({ signal }) => getPublicOrganization(slug ?? "", signal),
  });
}

/* -------------------------------------------------------------------------- */
/* Resource-ownership claims (organization side)                              */
/* -------------------------------------------------------------------------- */

export function useOrganizationClaimsQuery(status: OwnershipClaimStatus | undefined, page: number, size: number) {
  const { authorized, userId, getValidAccessToken } = useOrganizationAuth();
  return useQuery({
    enabled: authorized && userId !== null,
    queryKey: organizationKeys.claims(userId ?? "", { status, page, size }),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return getOrganizationClaims({ status, page, size, accessToken: token, signal });
    },
  });
}

export function useClaimResourceMutation() {
  const { userId, getValidAccessToken } = useOrganizationAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (resourceId: string) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return claimResource(resourceId, token);
    },
    onSuccess: () => {
      if (userId) {
        queryClient.invalidateQueries({ queryKey: [...organizationKeys.all(userId), "claims"] });
      }
    },
  });
}

export function useWithdrawOwnershipClaimMutation() {
  const { userId, getValidAccessToken } = useOrganizationAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (claimId: string) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return withdrawOwnershipClaim(claimId, token);
    },
    onSuccess: () => {
      if (userId) {
        queryClient.invalidateQueries({ queryKey: [...organizationKeys.all(userId), "claims"] });
      }
    },
  });
}

export function useOrganizationResourcesQuery(page: number, size: number) {
  const { authorized, userId, getValidAccessToken } = useOrganizationAuth();
  return useQuery({
    enabled: authorized && userId !== null,
    queryKey: organizationKeys.resources(userId ?? "", { page, size }),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return getOrganizationResources({ page, size, accessToken: token, signal });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Admin: organization verification                                           */
/* -------------------------------------------------------------------------- */

export function useAdminOrganizationQueueQuery(
  params: { verificationStatus?: OrganizationVerificationStatus; page: number; size: number; sort?: string },
) {
  const { authorized, getValidAccessToken } = useAdminAuth();
  return useQuery({
    enabled: authorized,
    queryKey: adminOrganizationKeys.queue(params),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return getAdminOrganizationQueue({ ...params, accessToken: token, signal });
    },
  });
}

export function useAdminOrganizationDetailQuery(organizationId: string | null) {
  const { authorized, getValidAccessToken } = useAdminAuth();
  return useQuery({
    enabled: authorized && organizationId !== null,
    queryKey: adminOrganizationKeys.detail(organizationId ?? ""),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token || !organizationId) throw new Error("Not authenticated.");
      return getAdminOrganization(organizationId, token, signal);
    },
  });
}

function useAdminOrganizationDecisionMutation(action: (id: string, reason: string, token: string) => ReturnType<typeof verifyOrganization>) {
  const { getValidAccessToken } = useAdminAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ organizationId, reason }: { organizationId: string; reason: string }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return action(organizationId, reason, token);
    },
    onSuccess: (response, { organizationId }) => {
      queryClient.setQueryData(adminOrganizationKeys.detail(organizationId), response);
      queryClient.invalidateQueries({ queryKey: ["admin-organizations"] });
      queryClient.invalidateQueries({ queryKey: ["organization"] });
      queryClient.invalidateQueries({ queryKey: publicOrganizationKeys.detail(response.slug) });
      // Suspension/verification changes what a resource's public detail shows.
      queryClient.invalidateQueries({ queryKey: ["resources"] });
    },
  });
}

export function useVerifyOrganizationMutation() {
  return useAdminOrganizationDecisionMutation(verifyOrganization);
}

export function useRejectOrganizationMutation() {
  return useAdminOrganizationDecisionMutation(rejectOrganization);
}

export function useSuspendOrganizationMutation() {
  return useAdminOrganizationDecisionMutation(suspendOrganization);
}

export function useOrganizationAuditEventsQuery(organizationId: string | null, page: number, size: number) {
  const { authorized, getValidAccessToken } = useAdminAuth();
  return useQuery({
    enabled: authorized && organizationId !== null,
    queryKey: adminOrganizationKeys.audit(organizationId ?? "", { page, size }),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token || !organizationId) throw new Error("Not authenticated.");
      return getOrganizationAuditEvents(organizationId, { page, size, accessToken: token, signal });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Admin: resource-ownership-claim review                                     */
/* -------------------------------------------------------------------------- */

export function useAdminOwnershipClaimQueueQuery(
  params: { status?: OwnershipClaimStatus; organizationId?: string; page: number; size: number },
) {
  const { authorized, getValidAccessToken } = useAdminAuth();
  return useQuery({
    enabled: authorized,
    queryKey: adminOwnershipClaimKeys.queue(params),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return getAdminOwnershipClaimQueue({ ...params, accessToken: token, signal });
    },
  });
}

export function useAdminOwnershipClaimDetailQuery(claimId: string | null) {
  const { authorized, getValidAccessToken } = useAdminAuth();
  return useQuery({
    enabled: authorized && claimId !== null,
    queryKey: adminOwnershipClaimKeys.detail(claimId ?? ""),
    queryFn: async ({ signal }) => {
      const token = await getValidAccessToken();
      if (!token || !claimId) throw new Error("Not authenticated.");
      return getAdminOwnershipClaim(claimId, token, signal);
    },
  });
}

function useAdminOwnershipClaimDecisionMutation(
  action: (id: string, reason: string, token: string) => ReturnType<typeof approveOwnershipClaim>,
) {
  const { getValidAccessToken } = useAdminAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ claimId, reason }: { claimId: string; reason: string }) => {
      const token = await getValidAccessToken();
      if (!token) throw new Error("Not authenticated.");
      return action(claimId, reason, token);
    },
    onSuccess: (response, { claimId }) => {
      queryClient.setQueryData(adminOwnershipClaimKeys.detail(claimId), response);
      queryClient.invalidateQueries({ queryKey: ["admin-ownership-claims"] });
      // Approval assigns resources.organizationId — the claiming
      // organization's own claims/resources, and the affected public
      // resource's detail/list/nearby responses, all now show different
      // ownership attribution.
      queryClient.invalidateQueries({ queryKey: ["organization"] });
      queryClient.invalidateQueries({ queryKey: ["resources"] });
    },
  });
}

export function useApproveOwnershipClaimMutation() {
  return useAdminOwnershipClaimDecisionMutation(approveOwnershipClaim);
}

export function useRejectOwnershipClaimMutation() {
  return useAdminOwnershipClaimDecisionMutation(rejectOwnershipClaim);
}
