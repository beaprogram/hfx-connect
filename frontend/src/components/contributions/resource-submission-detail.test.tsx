import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResourceSubmissionDetail } from "./resource-submission-detail";
import { useAuth } from "@/lib/auth/auth-provider";
import { getResourceSubmission, withdrawResourceSubmission } from "@/lib/api/resource-submissions";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/resource-submissions");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetResourceSubmission = getResourceSubmission as jest.MockedFunction<typeof getResourceSubmission>;
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
  submittedAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
  withdrawnAt: null,
};

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ResourceSubmissionDetail submissionId="sub-1" />
    </QueryClientProvider>,
  );
}

describe("ResourceSubmissionDetail", () => {
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

  it("shows the owned submission's details", async () => {
    mockGetResourceSubmission.mockResolvedValue(submission);
    renderWithQueryClient();

    expect(await screen.findByRole("heading", { name: "Halifax Food Bank" })).toBeInTheDocument();
    expect(screen.getByText("Food Assistance")).toBeInTheDocument();
    expect(screen.getByText("Pending review")).toBeInTheDocument();
  });

  it("omits optional fields that were not supplied", async () => {
    mockGetResourceSubmission.mockResolvedValue(submission);
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    expect(screen.queryByText("Phone")).not.toBeInTheDocument();
    expect(screen.queryByText("Website")).not.toBeInTheDocument();
  });

  it("handles another user's item (404) the same as any other failure, with no internal detail shown", async () => {
    mockGetResourceSubmission.mockRejectedValue(new Error("Not found"));
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't find that submission/i);
    expect(screen.queryByText(/RESOURCE_SUBMISSION_NOT_FOUND/)).not.toBeInTheDocument();
  });

  it("shows a Withdraw button for a pending submission and withdraws on click", async () => {
    mockGetResourceSubmission.mockResolvedValue(submission);
    mockWithdrawResourceSubmission.mockResolvedValue({ ...submission, status: "WITHDRAWN", withdrawnAt: "2026-08-02T00:00:00Z" });
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    await user.click(screen.getByRole("button", { name: "Withdraw submission" }));

    await waitFor(() => expect(mockWithdrawResourceSubmission).toHaveBeenCalledWith("sub-1", "token-1"));
  });

  it("does not show a Withdraw button for an already-withdrawn submission", async () => {
    mockGetResourceSubmission.mockResolvedValue({ ...submission, status: "WITHDRAWN", withdrawnAt: "2026-08-02T00:00:00Z" });
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    expect(screen.queryByRole("button", { name: "Withdraw submission" })).not.toBeInTheDocument();
  });
});
