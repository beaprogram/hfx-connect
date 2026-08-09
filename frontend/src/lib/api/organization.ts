import { getJson, postJson, patchJson } from "./client";
import {
  organizationResponseSchema,
  publicOrganizationResponseSchema,
  ownershipClaimResponseSchema,
  ownershipClaimPageResponseSchema,
  ownedResourcePageResponseSchema,
  adminOrganizationQueuePageSchema,
  adminOrganizationDetailSchema,
  adminOwnershipClaimQueuePageSchema,
  adminOwnershipClaimDetailSchema,
  organizationAuditEventPageSchema,
  type OrganizationResponse,
  type PublicOrganizationResponse,
  type OwnershipClaimResponse,
  type OwnershipClaimPageResponse,
  type OwnedResourcePageResponse,
  type AdminOrganizationQueuePage,
  type AdminOrganizationDetail,
  type AdminOwnershipClaimQueuePage,
  type AdminOwnershipClaimDetail,
  type OrganizationAuditEventPage,
  type OwnershipClaimStatus,
  type OrganizationVerificationStatus,
} from "@/lib/validation/schemas";

/**
 * The organization/resource-ownership-claim API (Milestone 10A) — an
 * ORGANIZATION account's own profile and claims, plus the public verified-
 * organization lookup and the ADMIN-only verification/claim-review surface.
 * As with `lib/api/moderation.ts`, nothing here grants authority on its own
 * — every write is independently re-authorized and re-validated by the
 * backend regardless of what this client sends (ADR-017: backend
 * authorization is the security boundary, this client is not).
 */

export interface OrganizationProfileInput {
  name: string;
  description?: string;
  websiteUrl?: string;
  publicEmail?: string;
  phone?: string;
  addressLine1?: string;
  city?: string;
  province?: string;
  postalCode?: string;
}

export function createOrganizationProfile(input: OrganizationProfileInput, accessToken: string): Promise<OrganizationResponse> {
  return postJson("/api/v1/organizations/me", organizationResponseSchema, { body: input, accessToken });
}

export function getOrganizationProfile(accessToken: string, signal?: AbortSignal): Promise<OrganizationResponse> {
  return getJson("/api/v1/organizations/me", organizationResponseSchema, { accessToken, signal });
}

export function updateOrganizationProfile(input: OrganizationProfileInput, accessToken: string): Promise<OrganizationResponse> {
  return patchJson("/api/v1/organizations/me", organizationResponseSchema, { body: input, accessToken });
}

export function getPublicOrganization(slug: string, signal?: AbortSignal): Promise<PublicOrganizationResponse> {
  return getJson(`/api/v1/organizations/${encodeURIComponent(slug)}`, publicOrganizationResponseSchema, { signal });
}

export function claimResource(resourceId: string, accessToken: string): Promise<OwnershipClaimResponse> {
  return postJson(
    `/api/v1/organizations/me/resource-claims/${encodeURIComponent(resourceId)}`,
    ownershipClaimResponseSchema,
    { accessToken },
  );
}

export interface OrganizationClaimsParams {
  status?: OwnershipClaimStatus;
  page?: number;
  size?: number;
  accessToken: string;
  signal?: AbortSignal;
}

export function getOrganizationClaims(params: OrganizationClaimsParams): Promise<OwnershipClaimPageResponse> {
  const { status, page = 0, size = 20, accessToken, signal } = params;
  return getJson("/api/v1/organizations/me/resource-claims", ownershipClaimPageResponseSchema, {
    params: { status, page, size },
    accessToken,
    signal,
  });
}

export function withdrawOwnershipClaim(claimId: string, accessToken: string): Promise<OwnershipClaimResponse> {
  return postJson(
    `/api/v1/organizations/me/resource-claims/${encodeURIComponent(claimId)}/withdraw`,
    ownershipClaimResponseSchema,
    { accessToken },
  );
}

export interface OrganizationResourcesParams {
  page?: number;
  size?: number;
  accessToken: string;
  signal?: AbortSignal;
}

export function getOrganizationResources(params: OrganizationResourcesParams): Promise<OwnedResourcePageResponse> {
  const { page = 0, size = 20, accessToken, signal } = params;
  return getJson("/api/v1/organizations/me/resources", ownedResourcePageResponseSchema, {
    params: { page, size },
    accessToken,
    signal,
  });
}

export interface AdminOrganizationQueueParams {
  verificationStatus?: OrganizationVerificationStatus;
  page?: number;
  size?: number;
  sort?: string;
  accessToken: string;
  signal?: AbortSignal;
}

export function getAdminOrganizationQueue(params: AdminOrganizationQueueParams): Promise<AdminOrganizationQueuePage> {
  const { verificationStatus, page = 0, size = 20, sort, accessToken, signal } = params;
  return getJson("/api/v1/admin/organizations", adminOrganizationQueuePageSchema, {
    params: { verificationStatus, page, size, sort },
    accessToken,
    signal,
  });
}

export function getAdminOrganization(organizationId: string, accessToken: string, signal?: AbortSignal): Promise<AdminOrganizationDetail> {
  return getJson(`/api/v1/admin/organizations/${encodeURIComponent(organizationId)}`, adminOrganizationDetailSchema, {
    accessToken,
    signal,
  });
}

export function verifyOrganization(organizationId: string, reason: string, accessToken: string): Promise<AdminOrganizationDetail> {
  return postJson(`/api/v1/admin/organizations/${encodeURIComponent(organizationId)}/verify`, adminOrganizationDetailSchema, {
    body: { reason },
    accessToken,
  });
}

export function rejectOrganization(organizationId: string, reason: string, accessToken: string): Promise<AdminOrganizationDetail> {
  return postJson(`/api/v1/admin/organizations/${encodeURIComponent(organizationId)}/reject`, adminOrganizationDetailSchema, {
    body: { reason },
    accessToken,
  });
}

export function suspendOrganization(organizationId: string, reason: string, accessToken: string): Promise<AdminOrganizationDetail> {
  return postJson(`/api/v1/admin/organizations/${encodeURIComponent(organizationId)}/suspend`, adminOrganizationDetailSchema, {
    body: { reason },
    accessToken,
  });
}

export function getOrganizationAuditEvents(
  organizationId: string,
  params: { page?: number; size?: number; accessToken: string; signal?: AbortSignal },
): Promise<OrganizationAuditEventPage> {
  const { page = 0, size = 20, accessToken, signal } = params;
  return getJson(`/api/v1/admin/organizations/${encodeURIComponent(organizationId)}/audit-events`, organizationAuditEventPageSchema, {
    params: { page, size },
    accessToken,
    signal,
  });
}

export interface AdminOwnershipClaimQueueParams {
  status?: OwnershipClaimStatus;
  organizationId?: string;
  page?: number;
  size?: number;
  accessToken: string;
  signal?: AbortSignal;
}

export function getAdminOwnershipClaimQueue(params: AdminOwnershipClaimQueueParams): Promise<AdminOwnershipClaimQueuePage> {
  const { status, organizationId, page = 0, size = 20, accessToken, signal } = params;
  return getJson("/api/v1/admin/resource-ownership-claims", adminOwnershipClaimQueuePageSchema, {
    params: { status, organizationId, page, size },
    accessToken,
    signal,
  });
}

export function getAdminOwnershipClaim(claimId: string, accessToken: string, signal?: AbortSignal): Promise<AdminOwnershipClaimDetail> {
  return getJson(`/api/v1/admin/resource-ownership-claims/${encodeURIComponent(claimId)}`, adminOwnershipClaimDetailSchema, {
    accessToken,
    signal,
  });
}

export function approveOwnershipClaim(claimId: string, reason: string, accessToken: string): Promise<AdminOwnershipClaimDetail> {
  return postJson(`/api/v1/admin/resource-ownership-claims/${encodeURIComponent(claimId)}/approve`, adminOwnershipClaimDetailSchema, {
    body: { reason },
    accessToken,
  });
}

export function rejectOwnershipClaim(claimId: string, reason: string, accessToken: string): Promise<AdminOwnershipClaimDetail> {
  return postJson(`/api/v1/admin/resource-ownership-claims/${encodeURIComponent(claimId)}/reject`, adminOwnershipClaimDetailSchema, {
    body: { reason },
    accessToken,
  });
}
