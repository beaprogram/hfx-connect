import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  useOrganizationProfileQuery,
  useClaimResourceMutation,
  useAdminOrganizationQueueQuery,
  useVerifyOrganizationMutation,
  useAdminOwnershipClaimQueueQuery,
  useApproveOwnershipClaimMutation,
} from "./use-organization";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  getOrganizationProfile,
  claimResource,
  getAdminOrganizationQueue,
  verifyOrganization,
  getAdminOwnershipClaimQueue,
  approveOwnershipClaim,
} from "@/lib/api/organization";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/organization");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetProfile = getOrganizationProfile as jest.MockedFunction<typeof getOrganizationProfile>;
const mockClaimResource = claimResource as jest.MockedFunction<typeof claimResource>;
const mockGetAdminOrgQueue = getAdminOrganizationQueue as jest.MockedFunction<typeof getAdminOrganizationQueue>;
const mockVerify = verifyOrganization as jest.MockedFunction<typeof verifyOrganization>;
const mockGetAdminClaimQueue = getAdminOwnershipClaimQueue as jest.MockedFunction<typeof getAdminOwnershipClaimQueue>;
const mockApprove = approveOwnershipClaim as jest.MockedFunction<typeof approveOwnershipClaim>;

function mockRole(role: "USER" | "ORGANIZATION" | "MODERATOR" | "ADMIN") {
  mockUseAuth.mockReturnValue({
    state: {
      status: "authenticated",
      user: { id: "1", email: "a@example.org", role, status: "ACTIVE", emailVerified: true, createdAt: "2026-07-15T00:00:00Z" },
      accessToken: "token-1",
      expiresAt: Date.now() + 900_000,
    },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
  });
}

function mockUnauthenticated() {
  mockUseAuth.mockReturnValue({
    state: { status: "unauthenticated" },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn().mockResolvedValue(null),
  });
}

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

const organizationResponse = {
  id: "org-1",
  name: "Halifax Newcomer Services",
  slug: "halifax-newcomer-services",
  description: null,
  websiteUrl: null,
  publicEmail: null,
  phone: null,
  addressLine1: null,
  city: null,
  province: null,
  postalCode: null,
  verificationStatus: "PENDING_VERIFICATION" as const,
  verifiedAt: null,
  verificationReason: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

describe("useOrganizationProfileQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not query while signed out", () => {
    mockUnauthenticated();
    renderHook(() => useOrganizationProfileQuery(), { wrapper });
    expect(mockGetProfile).not.toHaveBeenCalled();
  });

  it.each(["USER", "MODERATOR", "ADMIN"] as const)("does not query for a %s account", (role) => {
    mockRole(role);
    renderHook(() => useOrganizationProfileQuery(), { wrapper });
    expect(mockGetProfile).not.toHaveBeenCalled();
  });

  it("queries for an ORGANIZATION account", async () => {
    mockRole("ORGANIZATION");
    mockGetProfile.mockResolvedValue(organizationResponse);

    const { result } = renderHook(() => useOrganizationProfileQuery(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGetProfile).toHaveBeenCalledWith("token-1", expect.anything());
  });
});

describe("useClaimResourceMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRole("ORGANIZATION");
  });

  it("calls claimResource with the resolved access token, never a client-supplied organization id", async () => {
    mockClaimResource.mockResolvedValue({
      id: "claim-1",
      resource: null,
      status: "PENDING_REVIEW",
      requestedAt: "2026-08-01T00:00:00Z",
      reviewedAt: null,
      reviewReason: null,
    });
    const { result } = renderHook(() => useClaimResourceMutation(), { wrapper });

    result.current.mutate("res-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockClaimResource).toHaveBeenCalledWith("res-1", "token-1");
  });
});

describe("useAdminOrganizationQueueQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not query for an ORGANIZATION account", () => {
    mockRole("ORGANIZATION");
    renderHook(() => useAdminOrganizationQueueQuery({ page: 0, size: 20 }), { wrapper });
    expect(mockGetAdminOrgQueue).not.toHaveBeenCalled();
  });

  it("does not query for a MODERATOR account — org verification is ADMIN-only", () => {
    mockRole("MODERATOR");
    renderHook(() => useAdminOrganizationQueueQuery({ page: 0, size: 20 }), { wrapper });
    expect(mockGetAdminOrgQueue).not.toHaveBeenCalled();
  });

  it("queries for an ADMIN account", async () => {
    mockRole("ADMIN");
    mockGetAdminOrgQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    const { result } = renderHook(() => useAdminOrganizationQueueQuery({ page: 0, size: 20 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useVerifyOrganizationMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRole("ADMIN");
  });

  it("calls verifyOrganization with the resolved access token", async () => {
    mockVerify.mockResolvedValue({ ...organizationResponse, ownerUserId: "owner-1", verifiedByUserId: "1", verificationStatus: "VERIFIED" });
    const { result } = renderHook(() => useVerifyOrganizationMutation(), { wrapper });

    result.current.mutate({ organizationId: "org-1", reason: "Confirmed." });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockVerify).toHaveBeenCalledWith("org-1", "Confirmed.", "token-1");
  });
});

describe("useAdminOwnershipClaimQueueQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not query for an ORGANIZATION account", () => {
    mockRole("ORGANIZATION");
    renderHook(() => useAdminOwnershipClaimQueueQuery({ page: 0, size: 20 }), { wrapper });
    expect(mockGetAdminClaimQueue).not.toHaveBeenCalled();
  });

  it("queries for an ADMIN account", async () => {
    mockRole("ADMIN");
    mockGetAdminClaimQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    const { result } = renderHook(() => useAdminOwnershipClaimQueueQuery({ page: 0, size: 20 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useApproveOwnershipClaimMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRole("ADMIN");
  });

  it("calls approveOwnershipClaim with the resolved access token", async () => {
    mockApprove.mockResolvedValue({
      id: "claim-1",
      organizationId: "org-1",
      organizationName: "Halifax Newcomer Services",
      organizationSlug: "halifax-newcomer-services",
      organizationVerificationStatus: "VERIFIED",
      resource: null,
      currentResourceOrganizationId: "org-1",
      status: "APPROVED",
      requestedAt: "2026-08-01T00:00:00Z",
      reviewedByUserId: "1",
      reviewedAt: "2026-08-02T00:00:00Z",
      reviewReason: "Confirmed.",
    });
    const { result } = renderHook(() => useApproveOwnershipClaimMutation(), { wrapper });

    result.current.mutate({ claimId: "claim-1", reason: "Confirmed." });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApprove).toHaveBeenCalledWith("claim-1", "Confirmed.", "token-1");
  });
});
