import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResourceSubmissionQueue } from "./resource-submission-queue";
import { useAuth } from "@/lib/auth/auth-provider";
import { getResourceSubmissionQueue } from "@/lib/api/moderation";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/moderation");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetQueue = getResourceSubmissionQueue as jest.MockedFunction<typeof getResourceSubmissionQueue>;

const item = {
  id: "sub-1",
  category: { id: 1, name: "Food Assistance", slug: "food-assistance" },
  name: "Halifax Food Bank",
  shortDescription: "Free groceries.",
  status: "PENDING_REVIEW" as const,
  submittedAt: "2026-08-01T00:00:00Z",
};

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ResourceSubmissionQueue />
    </QueryClientProvider>,
  );
}

describe("ResourceSubmissionQueue", () => {
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
  });

  it("requests the queue defaulting to PENDING_REVIEW", async () => {
    mockGetQueue.mockResolvedValue({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    renderWithQueryClient();

    await waitFor(() => expect(mockGetQueue).toHaveBeenCalledWith(expect.objectContaining({ status: "PENDING_REVIEW" })));
    expect(await screen.findByRole("heading", { name: "Halifax Food Bank" })).toBeInTheDocument();
  });

  it("shows a loading state before the queue resolves", () => {
    mockGetQueue.mockReturnValue(new Promise(() => {}));
    renderWithQueryClient();

    expect(screen.getByRole("status")).toHaveTextContent(/loading the queue/i);
  });

  it("shows an empty state when the queue has no items", async () => {
    mockGetQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    renderWithQueryClient();

    expect(await screen.findByText(/nothing here right now/i)).toBeInTheDocument();
  });

  it("shows an error state with a retry action", async () => {
    mockGetQueue.mockRejectedValueOnce(new Error("network"));
    const user = userEvent.setup();
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't load the moderation queue/i);

    mockGetQueue.mockResolvedValueOnce({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    await user.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByRole("heading", { name: "Halifax Food Bank" })).toBeInTheDocument();
  });

  it("changing the status filter re-queries with the new status", async () => {
    mockGetQueue.mockResolvedValue({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    await user.selectOptions(screen.getByLabelText("Status"), "APPROVED");

    await waitFor(() => expect(mockGetQueue).toHaveBeenCalledWith(expect.objectContaining({ status: "APPROVED" })));
  });

  it("never displays submitter identity", async () => {
    mockGetQueue.mockResolvedValue({ content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    expect(screen.queryByText(/submittedBy|submitter/i)).not.toBeInTheDocument();
  });
});
