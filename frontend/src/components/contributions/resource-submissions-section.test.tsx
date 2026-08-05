import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResourceSubmissionsSection } from "./resource-submissions-section";
import { useAuth } from "@/lib/auth/auth-provider";
import { getResourceSubmissions } from "@/lib/api/resource-submissions";
import type { ResourceSubmissionPageResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/resource-submissions");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetResourceSubmissions = getResourceSubmissions as jest.MockedFunction<typeof getResourceSubmissions>;

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ResourceSubmissionsSection />
    </QueryClientProvider>,
  );
}

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
  costType: "UNKNOWN" as const,
  eligibilityInformation: null,
  accessibilityInformation: null,
  status: "PENDING_REVIEW" as const,
  submittedAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
  withdrawnAt: null,
};

function page(content: typeof submission[], totalPages = content.length > 0 ? 1 : 0): ResourceSubmissionPageResponse {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages };
}

describe("ResourceSubmissionsSection", () => {
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

  it("has a My Resource Submissions heading", async () => {
    mockGetResourceSubmissions.mockResolvedValue(page([]));
    renderWithQueryClient();

    expect(screen.getByRole("heading", { name: "My Resource Submissions" })).toBeInTheDocument();
  });

  it("shows a loading state before data arrives", () => {
    mockGetResourceSubmissions.mockReturnValue(new Promise(() => {}));
    renderWithQueryClient();

    expect(screen.getByRole("status")).toHaveTextContent(/loading your submissions/i);
  });

  it("shows an empty state with a link to propose a resource", async () => {
    mockGetResourceSubmissions.mockResolvedValue(page([]));
    renderWithQueryClient();

    expect(await screen.findByText(/haven't proposed any resources yet/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Propose a resource" })).toHaveAttribute("href", "/submit-resource");
  });

  it("shows submission cards with name, category, status, submitted date, and a detail link", async () => {
    mockGetResourceSubmissions.mockResolvedValue(page([submission]));
    renderWithQueryClient();

    expect(await screen.findByRole("link", { name: "Halifax Food Bank" })).toHaveAttribute(
      "href",
      "/dashboard/submissions/sub-1",
    );
    expect(screen.getByText("Food Assistance")).toBeInTheDocument();
    expect(screen.getByText("Pending review")).toBeInTheDocument();
    expect(screen.getByText(/Submitted Jul 31, 2026/)).toBeInTheDocument();
  });

  it("shows a recoverable error state with a retry action", async () => {
    mockGetResourceSubmissions.mockRejectedValue(new Error("network down"));
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });

  it("shows pagination controls when there is more than one page", async () => {
    mockGetResourceSubmissions.mockResolvedValue(page([submission], 2));
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Food Bank" });
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next page" })).toBeEnabled();
  });

  it("requests the next page when Next page is clicked", async () => {
    mockGetResourceSubmissions.mockResolvedValue(page([submission], 2));
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Food Bank" });
    await user.click(screen.getByRole("button", { name: "Next page" }));

    await waitFor(() => expect(mockGetResourceSubmissions).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));
  });

  it("does not fabricate a review-time estimate or count", async () => {
    mockGetResourceSubmissions.mockResolvedValue(page([submission]));
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Food Bank" });
    expect(screen.queryByText(/estimated|review time|typically takes/i)).not.toBeInTheDocument();
  });
});
