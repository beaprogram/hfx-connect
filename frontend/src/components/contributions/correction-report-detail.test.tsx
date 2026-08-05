import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CorrectionReportDetail } from "./correction-report-detail";
import { useAuth } from "@/lib/auth/auth-provider";
import { getCorrectionReport, withdrawCorrectionReport } from "@/lib/api/correction-reports";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/correction-reports");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetCorrectionReport = getCorrectionReport as jest.MockedFunction<typeof getCorrectionReport>;
const mockWithdrawCorrectionReport = withdrawCorrectionReport as jest.MockedFunction<typeof withdrawCorrectionReport>;

const report = {
  id: "report-1",
  resource: { resourceId: "res-1", name: "Halifax Central Library", slug: "halifax-central-library" },
  issueType: "ADDRESS" as const,
  explanation: "The address is out of date.",
  proposedName: null,
  proposedDescription: null,
  proposedAddressLine1: "456 New St",
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
};

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CorrectionReportDetail reportId="report-1" />
    </QueryClientProvider>,
  );
}

describe("CorrectionReportDetail", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: { id: "user-1", email: "a@example.org", role: "USER", status: "ACTIVE", emailVerified: false, createdAt: "2026-07-15T00:00:00Z" },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
    });
  });

  it("shows the owned report's details, including the proposed field that was supplied", async () => {
    mockGetCorrectionReport.mockResolvedValue(report);
    renderWithQueryClient();

    expect(await screen.findByRole("heading", { name: "Halifax Central Library" })).toBeInTheDocument();
    expect(screen.getByText("Address")).toBeInTheDocument();
    expect(screen.getByText("The address is out of date.")).toBeInTheDocument();
    expect(screen.getByText("456 New St")).toBeInTheDocument();
  });

  it("links the resource name to its detail page when the resource still exists", async () => {
    mockGetCorrectionReport.mockResolvedValue(report);
    renderWithQueryClient();

    expect(await screen.findByRole("link", { name: "Halifax Central Library" })).toHaveAttribute(
      "href",
      "/resources/halifax-central-library",
    );
  });

  it("shows a plain name, not a link, when the target resource has been deleted", async () => {
    mockGetCorrectionReport.mockResolvedValue({ ...report, resource: { resourceId: null, name: "Halifax Central Library", slug: "halifax-central-library" } });
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    expect(screen.queryByRole("link", { name: "Halifax Central Library" })).not.toBeInTheDocument();
    expect(screen.getByText(/no longer exists/i)).toBeInTheDocument();
  });

  it("handles another user's item (404) the same as any other failure", async () => {
    mockGetCorrectionReport.mockRejectedValue(new Error("Not found"));
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't find that report/i);
  });

  it("shows a Withdraw button for a pending report and withdraws on click", async () => {
    mockGetCorrectionReport.mockResolvedValue(report);
    mockWithdrawCorrectionReport.mockResolvedValue({ ...report, status: "WITHDRAWN", withdrawnAt: "2026-08-02T00:00:00Z" });
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    await user.click(screen.getByRole("button", { name: "Withdraw report" }));

    await waitFor(() => expect(mockWithdrawCorrectionReport).toHaveBeenCalledWith("report-1", "token-1"));
  });
});
