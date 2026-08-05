import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  useCorrectionReportsQuery,
  useCorrectionReportQuery,
  useCreateCorrectionReportMutation,
  useWithdrawCorrectionReportMutation,
} from "./use-correction-reports";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  getCorrectionReports,
  getCorrectionReport,
  createCorrectionReport,
  withdrawCorrectionReport,
} from "@/lib/api/correction-reports";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/correction-reports");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetCorrectionReports = getCorrectionReports as jest.MockedFunction<typeof getCorrectionReports>;
const mockGetCorrectionReport = getCorrectionReport as jest.MockedFunction<typeof getCorrectionReport>;
const mockCreateCorrectionReport = createCorrectionReport as jest.MockedFunction<typeof createCorrectionReport>;
const mockWithdrawCorrectionReport = withdrawCorrectionReport as jest.MockedFunction<typeof withdrawCorrectionReport>;

const report = {
  id: "report-1",
  resource: { resourceId: "res-1", name: "Halifax Central Library", slug: "halifax-central-library" },
  issueType: "ADDRESS" as const,
  explanation: "The address is out of date.",
  proposedName: null,
  proposedDescription: null,
  proposedAddressLine1: null,
  proposedAddressLine2: null,
  proposedCity: null,
  proposedProvince: null,
  proposedPostalCode: null,
  proposedPhone: null,
  proposedEmail: null,
  proposedWebsiteUrl: null,
  proposedCostType: null,
  proposedCostDetails: null,
  proposedEligibility: null,
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

describe("useCorrectionReportsQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not request the list while signed out", () => {
    mockUnauthenticated();
    renderHook(() => useCorrectionReportsQuery(0, 20), { wrapper });
    expect(mockGetCorrectionReports).not.toHaveBeenCalled();
  });

  it("requests the current user's reports once authenticated", async () => {
    mockAuthenticated();
    mockGetCorrectionReports.mockResolvedValue({ content: [report], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    const { result } = renderHook(() => useCorrectionReportsQuery(0, 20), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(1);
  });
});

describe("useCorrectionReportQuery", () => {
  beforeEach(() => jest.clearAllMocks());

  it("does not request while no id is supplied", () => {
    mockAuthenticated();
    renderHook(() => useCorrectionReportQuery(null), { wrapper });
    expect(mockGetCorrectionReport).not.toHaveBeenCalled();
  });
});

describe("useCreateCorrectionReportMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAuthenticated();
  });

  it("calls the create API with the target resource id and resolved access token", async () => {
    mockCreateCorrectionReport.mockResolvedValue(report);
    const { result } = renderHook(() => useCreateCorrectionReportMutation(), { wrapper });

    result.current.mutate({ resourceId: "res-1", input: { issueType: "ADDRESS", explanation: "Wrong address." } });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockCreateCorrectionReport).toHaveBeenCalledWith(
      "res-1",
      expect.objectContaining({ issueType: "ADDRESS" }),
      "token-1",
    );
  });
});

describe("useWithdrawCorrectionReportMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAuthenticated();
  });

  it("calls the withdraw API with the resolved access token", async () => {
    mockWithdrawCorrectionReport.mockResolvedValue({ ...report, status: "WITHDRAWN", withdrawnAt: "2026-08-04T01:00:00Z" });
    const { result } = renderHook(() => useWithdrawCorrectionReportMutation(), { wrapper });

    result.current.mutate("report-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockWithdrawCorrectionReport).toHaveBeenCalledWith("report-1", "token-1");
  });
});
