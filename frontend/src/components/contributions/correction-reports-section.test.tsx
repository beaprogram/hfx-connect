import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CorrectionReportsSection } from "./correction-reports-section";
import { useAuth } from "@/lib/auth/auth-provider";
import { getCorrectionReports } from "@/lib/api/correction-reports";
import type { CorrectionReportPageResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/correction-reports");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetCorrectionReports = getCorrectionReports as jest.MockedFunction<typeof getCorrectionReports>;

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CorrectionReportsSection />
    </QueryClientProvider>,
  );
}

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
  submittedAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
  withdrawnAt: null,
};

function page(content: typeof report[], totalPages = content.length > 0 ? 1 : 0): CorrectionReportPageResponse {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages };
}

describe("CorrectionReportsSection", () => {
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

  it("has a My Correction Reports heading", async () => {
    mockGetCorrectionReports.mockResolvedValue(page([]));
    renderWithQueryClient();

    expect(screen.getByRole("heading", { name: "My Correction Reports" })).toBeInTheDocument();
  });

  it("shows a loading state before data arrives", () => {
    mockGetCorrectionReports.mockReturnValue(new Promise(() => {}));
    renderWithQueryClient();

    expect(screen.getByRole("status")).toHaveTextContent(/loading your correction reports/i);
  });

  it("shows an empty state with a link to browse resources", async () => {
    mockGetCorrectionReports.mockResolvedValue(page([]));
    renderWithQueryClient();

    expect(await screen.findByText(/haven't reported any issues yet/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse resources to report information" })).toHaveAttribute("href", "/resources");
  });

  it("shows report cards with resource name, issue type, status, submitted date, and a detail link", async () => {
    mockGetCorrectionReports.mockResolvedValue(page([report]));
    renderWithQueryClient();

    expect(await screen.findByRole("link", { name: "Halifax Central Library" })).toHaveAttribute(
      "href",
      "/dashboard/correction-reports/report-1",
    );
    expect(screen.getByText("Address")).toBeInTheDocument();
    expect(screen.getByText("Pending review")).toBeInTheDocument();
  });

  it("shows a recoverable error state with a retry action", async () => {
    mockGetCorrectionReports.mockRejectedValue(new Error("network down"));
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });

  it("requests the next page when Next page is clicked", async () => {
    mockGetCorrectionReports.mockResolvedValue(page([report], 2));
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Central Library" });
    await user.click(screen.getByRole("button", { name: "Next page" }));

    await waitFor(() => expect(mockGetCorrectionReports).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));
  });
});
