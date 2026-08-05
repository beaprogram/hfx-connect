import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CorrectionReportForm } from "./correction-report-form";
import { createCorrectionReport } from "@/lib/api/correction-reports";
import { useAuth } from "@/lib/auth/auth-provider";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/api/correction-reports");
jest.mock("@/lib/auth/auth-provider");

const mockCreateCorrectionReport = createCorrectionReport as jest.MockedFunction<typeof createCorrectionReport>;
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

const reportResponse = {
  id: "report-1",
  resource: { resourceId: "res-1", name: "Halifax Central Library", slug: "halifax-central-library" },
  issueType: "ADDRESS" as const,
  explanation: "Wrong address.",
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

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CorrectionReportForm resourceId="res-1" resourceName="Halifax Central Library" />
    </QueryClientProvider>,
  );
}

describe("CorrectionReportForm", () => {
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

  it("shows the target resource name", () => {
    renderWithQueryClient();
    expect(screen.getByText("Halifax Central Library")).toBeInTheDocument();
  });

  it("does not claim to modify the resource directly", () => {
    renderWithQueryClient();
    expect(screen.getByText(/does not change this listing immediately/i)).toBeInTheDocument();
  });

  it("submits an explanation-only report (no proposed fields required)", async () => {
    mockCreateCorrectionReport.mockResolvedValue(reportResponse);
    const user = userEvent.setup();
    renderWithQueryClient();

    await user.selectOptions(screen.getByLabelText(/what's wrong/i), "RESOURCE_CLOSED");
    await user.type(screen.getByLabelText("Explanation"), "This location has permanently closed.");
    await user.click(screen.getByRole("button", { name: "Submit report" }));

    await waitFor(() => expect(mockCreateCorrectionReport).toHaveBeenCalledWith(
      "res-1",
      expect.objectContaining({ issueType: "RESOURCE_CLOSED", explanation: "This location has permanently closed." }),
      "token-1",
    ));
    expect(await screen.findByText(/sent for review/i)).toBeInTheDocument();
    expect(screen.queryByText(/has been updated/i)).not.toBeInTheDocument();
  });

  it("submits proposed corrected fields when provided", async () => {
    mockCreateCorrectionReport.mockResolvedValue(reportResponse);
    const user = userEvent.setup();
    renderWithQueryClient();

    await user.selectOptions(screen.getByLabelText(/what's wrong/i), "ADDRESS");
    await user.type(screen.getByLabelText("Explanation"), "The address moved.");
    await user.type(screen.getByLabelText("Corrected address"), "456 New St");
    await user.click(screen.getByRole("button", { name: "Submit report" }));

    await waitFor(() => expect(mockCreateCorrectionReport).toHaveBeenCalledWith(
      "res-1",
      expect.objectContaining({ proposedAddressLine1: "456 New St" }),
      "token-1",
    ));
  });

  it("maps server field-validation errors accessibly", async () => {
    mockCreateCorrectionReport.mockRejectedValue(
      new ApiRequestError("Invalid.", 400, "VALIDATION_ERROR", { explanation: "Explanation is required." }),
    );
    const user = userEvent.setup();
    renderWithQueryClient();

    await user.selectOptions(screen.getByLabelText(/what's wrong/i), "OTHER");
    await user.type(screen.getByLabelText("Explanation"), "x");
    await user.click(screen.getByRole("button", { name: "Submit report" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/fix the highlighted fields/i);
  });

  it("shows a conflict message for a duplicate pending report", async () => {
    mockCreateCorrectionReport.mockRejectedValue(new ApiRequestError("Conflict.", 409, "CORRECTION_REPORT_CONFLICT"));
    const user = userEvent.setup();
    renderWithQueryClient();

    await user.selectOptions(screen.getByLabelText(/what's wrong/i), "OTHER");
    await user.type(screen.getByLabelText("Explanation"), "Duplicate issue.");
    await user.click(screen.getByRole("button", { name: "Submit report" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/already have a pending report/i);
  });

  it("prevents a duplicate submit while a request is in flight", async () => {
    let resolveCreate: () => void = () => {};
    mockCreateCorrectionReport.mockImplementation(
      () => new Promise((resolve) => (resolveCreate = () => resolve(reportResponse))),
    );
    const user = userEvent.setup();
    renderWithQueryClient();

    await user.selectOptions(screen.getByLabelText(/what's wrong/i), "OTHER");
    await user.type(screen.getByLabelText("Explanation"), "Something.");
    await user.click(screen.getByRole("button", { name: "Submit report" }));

    expect(screen.getByRole("button", { name: "Submitting…" })).toBeDisabled();
    resolveCreate();
    await waitFor(() => expect(mockCreateCorrectionReport).toHaveBeenCalledTimes(1));
  });
});
