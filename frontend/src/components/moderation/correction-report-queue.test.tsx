import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CorrectionReportQueue } from "./correction-report-queue";
import { useAuth } from "@/lib/auth/auth-provider";
import { getCorrectionReportQueue } from "@/lib/api/moderation";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/moderation");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetQueue = getCorrectionReportQueue as jest.MockedFunction<typeof getCorrectionReportQueue>;

const item = {
  id: "report-1",
  resource: { resourceId: "res-1", name: "Halifax Central Library", slug: "halifax-central-library" },
  issueType: "ADDRESS" as const,
  explanationPreview: "The address is out of date.",
  status: "PENDING_REVIEW" as const,
  submittedAt: "2026-08-01T00:00:00Z",
};

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CorrectionReportQueue />
    </QueryClientProvider>,
  );
}

describe("CorrectionReportQueue", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: { id: "mod-1", email: "mod@example.org", role: "ADMIN", status: "ACTIVE", emailVerified: true, createdAt: "2026-07-15T00:00:00Z" },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
    });
  });

  it("requests the queue defaulting to PENDING_REVIEW", async () => {
    mockGetQueue.mockResolvedValue({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    renderWithQueryClient();

    await waitFor(() => expect(mockGetQueue).toHaveBeenCalledWith(expect.objectContaining({ status: "PENDING_REVIEW" })));
    expect(await screen.findByRole("heading", { name: "Halifax Central Library" })).toBeInTheDocument();
  });

  it("shows an empty state when the queue has no items", async () => {
    mockGetQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    renderWithQueryClient();

    expect(await screen.findByText(/nothing here right now/i)).toBeInTheDocument();
  });

  it("changing the issue-type filter re-queries with the new filter", async () => {
    mockGetQueue.mockResolvedValue({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    await user.selectOptions(screen.getByLabelText("Issue type"), "RESOURCE_CLOSED");

    await waitFor(() => expect(mockGetQueue).toHaveBeenCalledWith(expect.objectContaining({ issueType: "RESOURCE_CLOSED" })));
  });

  it("shows an error state with a retry action", async () => {
    mockGetQueue.mockRejectedValueOnce(new Error("network"));
    const user = userEvent.setup();
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't load the moderation queue/i);

    mockGetQueue.mockResolvedValueOnce({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    await user.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByRole("heading", { name: "Halifax Central Library" })).toBeInTheDocument();
  });

  it("never displays reporter identity", async () => {
    mockGetQueue.mockResolvedValue({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Central Library" });
    expect(screen.queryByText(/reportedBy|reporter/i)).not.toBeInTheDocument();
  });
});
