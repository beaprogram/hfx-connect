import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  useResourceSubmissionsQuery,
  useResourceSubmissionQuery,
  useCreateResourceSubmissionMutation,
  useWithdrawResourceSubmissionMutation,
} from "./use-resource-submissions";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  getResourceSubmissions,
  getResourceSubmission,
  createResourceSubmission,
  withdrawResourceSubmission,
} from "@/lib/api/resource-submissions";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/resource-submissions");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetResourceSubmissions = getResourceSubmissions as jest.MockedFunction<typeof getResourceSubmissions>;
const mockGetResourceSubmission = getResourceSubmission as jest.MockedFunction<typeof getResourceSubmission>;
const mockCreateResourceSubmission = createResourceSubmission as jest.MockedFunction<typeof createResourceSubmission>;
const mockWithdrawResourceSubmission = withdrawResourceSubmission as jest.MockedFunction<typeof withdrawResourceSubmission>;

const submission = {
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
  costType: "FREE" as const,
  eligibilityInformation: null,
  accessibilityInformation: null,
  status: "PENDING_REVIEW" as const,
  submittedAt: "2026-08-04T00:00:00Z",
  updatedAt: "2026-08-04T00:00:00Z",
  withdrawnAt: null,
};

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

function mockAuthenticated(userId = "user-1") {
  mockUseAuth.mockReturnValue({
    state: {
      status: "authenticated",
      user: { id: userId, email: "a@example.org", role: "USER", status: "ACTIVE", emailVerified: false, createdAt: "2026-07-15T00:00:00Z" },
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

describe("useResourceSubmissionsQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not request the list while signed out", () => {
    mockUnauthenticated();
    renderHook(() => useResourceSubmissionsQuery(0, 20), { wrapper });
    expect(mockGetResourceSubmissions).not.toHaveBeenCalled();
  });

  it("requests the current user's submissions once authenticated", async () => {
    mockAuthenticated();
    mockGetResourceSubmissions.mockResolvedValue({ content: [submission], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    const { result } = renderHook(() => useResourceSubmissionsQuery(0, 20), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(1);
  });
});

describe("useResourceSubmissionQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not request while no id is supplied", () => {
    mockAuthenticated();
    renderHook(() => useResourceSubmissionQuery(null), { wrapper });
    expect(mockGetResourceSubmission).not.toHaveBeenCalled();
  });

  it("requests one submission by id once authenticated", async () => {
    mockAuthenticated();
    mockGetResourceSubmission.mockResolvedValue(submission);

    const { result } = renderHook(() => useResourceSubmissionQuery("sub-1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGetResourceSubmission).toHaveBeenCalledWith("sub-1", "token-1", expect.anything());
  });
});

describe("useCreateResourceSubmissionMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAuthenticated();
  });

  it("calls the create API with the resolved access token", async () => {
    mockCreateResourceSubmission.mockResolvedValue(submission);
    const { result } = renderHook(() => useCreateResourceSubmissionMutation(), { wrapper });

    result.current.mutate({
      categoryId: 1,
      name: "Halifax Food Bank",
      shortDescription: "Free groceries.",
      addressLine1: "123 Main St",
      city: "Halifax",
      province: "NS",
      postalCode: "B3H 4R2",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockCreateResourceSubmission).toHaveBeenCalledWith(expect.objectContaining({ name: "Halifax Food Bank" }), "token-1");
  });
});

describe("useWithdrawResourceSubmissionMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAuthenticated();
  });

  it("calls the withdraw API with the resolved access token", async () => {
    mockWithdrawResourceSubmission.mockResolvedValue({ ...submission, status: "WITHDRAWN", withdrawnAt: "2026-08-04T01:00:00Z" });
    const { result } = renderHook(() => useWithdrawResourceSubmissionMutation(), { wrapper });

    result.current.mutate("sub-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockWithdrawResourceSubmission).toHaveBeenCalledWith("sub-1", "token-1");
  });
});
