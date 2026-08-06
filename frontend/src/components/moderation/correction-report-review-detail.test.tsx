import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CorrectionReportReviewDetail } from "./correction-report-review-detail";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  getCorrectionReportForModeration,
  approveCorrectionReport,
  rejectCorrectionReport,
  getCorrectionReportAuditEvents,
} from "@/lib/api/moderation";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/moderation");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetDetail = getCorrectionReportForModeration as jest.MockedFunction<typeof getCorrectionReportForModeration>;
const mockApprove = approveCorrectionReport as jest.MockedFunction<typeof approveCorrectionReport>;
const mockReject = rejectCorrectionReport as jest.MockedFunction<typeof rejectCorrectionReport>;
const mockAuditEvents = getCorrectionReportAuditEvents as jest.MockedFunction<typeof getCorrectionReportAuditEvents>;

function baseReport(overrides: Partial<Awaited<ReturnType<typeof getCorrectionReportForModeration>>> = {}) {
  return {
    id: "report-1",
    resource: { resourceId: "res-1", name: "Halifax Central Library", slug: "halifax-central-library" },
    currentResource: {
      id: "res-1",
      name: "Halifax Central Library",
      description: "A library.",
      addressLine1: "5440 Spring Garden Rd",
      addressLine2: null,
      city: "Halifax",
      province: "NS",
      postalCode: "B3J 1E9",
      phone: null,
      email: null,
      websiteUrl: null,
      costType: "FREE" as const,
      costDetails: null,
      eligibility: null,
      active: true,
    },
    issueType: "ADDRESS" as const,
    explanation: "The address on file is stale.",
    proposedName: null,
    proposedDescription: null,
    proposedAddressLine1: "1 New Ave",
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
    submittedAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    withdrawnAt: null,
    reviewedByUserId: null,
    reviewedAt: null,
    reviewReason: null,
    appliedToResourceAt: null,
    ...overrides,
  };
}

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CorrectionReportReviewDetail reportId="report-1" />
    </QueryClientProvider>,
  );
}

describe("CorrectionReportReviewDetail", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: { id: "mod-1", email: "mod@example.org", role: "MODERATOR", status: "ACTIVE", emailVerified: true, createdAt: "2026-07-15T00:00:00Z" },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
    });
    mockAuditEvents.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it("shows current-vs-proposed values and approve/reject forms while pending", async () => {
    mockGetDetail.mockResolvedValue(baseReport());
    renderWithQueryClient();

    expect(await screen.findByRole("heading", { name: "Halifax Central Library" })).toBeInTheDocument();
    expect(screen.getByText("1 New Ave")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Approve" })).toBeInTheDocument();
  });

  it("does not show the deactivate checkbox for a non-RESOURCE_CLOSED report", async () => {
    mockGetDetail.mockResolvedValue(baseReport({ issueType: "ADDRESS" }));
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    expect(screen.queryByLabelText(/deactivate this resource/i)).not.toBeInTheDocument();
  });

  it("shows the deactivate checkbox for a RESOURCE_CLOSED report", async () => {
    mockGetDetail.mockResolvedValue(baseReport({ issueType: "RESOURCE_CLOSED", proposedAddressLine1: null }));
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    expect(screen.getByLabelText(/deactivate this resource/i)).toBeInTheDocument();
  });

  it("disables the apply-changes checkbox for OPERATING_HOURS", async () => {
    mockGetDetail.mockResolvedValue(baseReport({ issueType: "OPERATING_HOURS", proposedAddressLine1: null }));
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    expect(screen.getByLabelText(/apply the supported proposed changes/i)).toBeDisabled();
  });

  it("approving with applyProposedChanges checked sends applyProposedChanges: true", async () => {
    mockGetDetail.mockResolvedValue(baseReport());
    mockApprove.mockResolvedValue(baseReport({ status: "APPROVED", appliedToResourceAt: "2026-08-02T00:00:00Z", reviewReason: "Confirmed." }));
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    const approveSection = screen.getByRole("heading", { name: "Approve" }).closest("section")!;
    await user.type(within(approveSection).getByLabelText("Reason"), "Confirmed the new address.");
    await user.click(within(approveSection).getByLabelText(/apply the supported proposed changes/i));
    await user.click(within(approveSection).getByRole("button", { name: "Approve" }));

    await waitFor(() =>
      expect(mockApprove).toHaveBeenCalledWith(
        "report-1",
        { reason: "Confirmed the new address.", applyProposedChanges: true, deactivateResource: false },
        "token-1",
      ),
    );
  });

  it("rejecting never shows or sends deactivateResource/applyProposedChanges flags", async () => {
    mockGetDetail.mockResolvedValue(baseReport());
    mockReject.mockResolvedValue(baseReport({ status: "REJECTED", reviewReason: "Not verifiable." }));
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    const rejectSection = screen.getByRole("heading", { name: "Reject" }).closest("section")!;
    await user.type(within(rejectSection).getByLabelText("Reason"), "Not verifiable.");
    await user.click(within(rejectSection).getByRole("button", { name: "Reject" }));

    await waitFor(() => expect(mockReject).toHaveBeenCalledWith("report-1", "Not verifiable.", "token-1"));
  });

  it("does not show approve/reject forms once already approved", async () => {
    mockGetDetail.mockResolvedValue(
      baseReport({ status: "APPROVED", appliedToResourceAt: "2026-08-02T00:00:00Z", reviewReason: "Confirmed." }),
    );
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    expect(screen.queryByRole("heading", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.getByText(/reviewed and applied/i)).toBeInTheDocument();
  });
});
