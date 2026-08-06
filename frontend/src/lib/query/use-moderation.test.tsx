import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  useResourceSubmissionQueueQuery,
  useApproveResourceSubmissionMutation,
  useCorrectionReportQueueQuery,
} from "./use-moderation";
import { useAuth } from "@/lib/auth/auth-provider";
import { getResourceSubmissionQueue, approveResourceSubmission, getCorrectionReportQueue } from "@/lib/api/moderation";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/moderation");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetSubmissionQueue = getResourceSubmissionQueue as jest.MockedFunction<typeof getResourceSubmissionQueue>;
const mockApprove = approveResourceSubmission as jest.MockedFunction<typeof approveResourceSubmission>;
const mockGetCorrectionQueue = getCorrectionReportQueue as jest.MockedFunction<typeof getCorrectionReportQueue>;

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

describe("useResourceSubmissionQueueQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not query while signed out", () => {
    mockUnauthenticated();
    renderHook(() => useResourceSubmissionQueueQuery({ page: 0, size: 20 }), { wrapper });
    expect(mockGetSubmissionQueue).not.toHaveBeenCalled();
  });

  it.each(["USER", "ORGANIZATION"] as const)("does not query for a %s account", (role) => {
    mockRole(role);
    renderHook(() => useResourceSubmissionQueueQuery({ page: 0, size: 20 }), { wrapper });
    expect(mockGetSubmissionQueue).not.toHaveBeenCalled();
  });

  it.each(["MODERATOR", "ADMIN"] as const)("queries for a %s account", async (role) => {
    mockRole(role);
    mockGetSubmissionQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    const { result } = renderHook(() => useResourceSubmissionQueueQuery({ page: 0, size: 20 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGetSubmissionQueue).toHaveBeenCalled();
  });
});

describe("useCorrectionReportQueueQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not query for a USER account", () => {
    mockRole("USER");
    renderHook(() => useCorrectionReportQueueQuery({ page: 0, size: 20 }), { wrapper });
    expect(mockGetCorrectionQueue).not.toHaveBeenCalled();
  });

  it("queries for an ADMIN account", async () => {
    mockRole("ADMIN");
    mockGetCorrectionQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    const { result } = renderHook(() => useCorrectionReportQueueQuery({ page: 0, size: 20 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useApproveResourceSubmissionMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRole("MODERATOR");
  });

  it("calls the approve API with the resolved access token, never a client-supplied actor", async () => {
    mockApprove.mockResolvedValue({
      id: "sub-1",
      category: { id: 1, name: "Food Assistance", slug: "food-assistance" },
      name: "Halifax Food Bank",
      shortDescription: "Free groceries.",
      fullDescription: null,
      addressLine1: "123 Main St",
      addressLine2: null,
      city: "Halifax",
      province: "NS",
      postalCode: "B3H 4R2",
      phone: null,
      email: null,
      websiteUrl: null,
      costType: "FREE",
      eligibilityInformation: null,
      accessibilityInformation: null,
      status: "APPROVED",
      submittedAt: "2026-08-01T00:00:00Z",
      updatedAt: "2026-08-02T00:00:00Z",
      withdrawnAt: null,
      reviewedByUserId: "mod-1",
      reviewedAt: "2026-08-02T00:00:00Z",
      reviewReason: "Verified.",
      resultingResource: { id: "res-1", name: "Halifax Food Bank", slug: "halifax-food-bank" },
    });
    const { result } = renderHook(() => useApproveResourceSubmissionMutation(), { wrapper });

    result.current.mutate({ submissionId: "sub-1", reason: "Verified." });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApprove).toHaveBeenCalledWith("sub-1", "Verified.", "token-1");
  });
});
