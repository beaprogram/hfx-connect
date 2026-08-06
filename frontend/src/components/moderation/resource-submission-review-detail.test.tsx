import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResourceSubmissionReviewDetail } from "./resource-submission-review-detail";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  getResourceSubmissionForModeration,
  approveResourceSubmission,
  rejectResourceSubmission,
  getResourceSubmissionAuditEvents,
} from "@/lib/api/moderation";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/moderation");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetDetail = getResourceSubmissionForModeration as jest.MockedFunction<typeof getResourceSubmissionForModeration>;
const mockApprove = approveResourceSubmission as jest.MockedFunction<typeof approveResourceSubmission>;
const mockReject = rejectResourceSubmission as jest.MockedFunction<typeof rejectResourceSubmission>;
const mockAuditEvents = getResourceSubmissionAuditEvents as jest.MockedFunction<typeof getResourceSubmissionAuditEvents>;

const pendingSubmission = {
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
  reviewedByUserId: null,
  reviewedAt: null,
  reviewReason: null,
  resultingResource: null,
};

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ResourceSubmissionReviewDetail submissionId="sub-1" />
    </QueryClientProvider>,
  );
}

describe("ResourceSubmissionReviewDetail", () => {
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

  it("shows the submission's proposed fields and approve/reject forms while pending", async () => {
    mockGetDetail.mockResolvedValue(pendingSubmission);
    renderWithQueryClient();

    expect(await screen.findByRole("heading", { name: "Halifax Food Bank" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Approve" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Reject" })).toBeInTheDocument();
  });

  it("does not show approve/reject forms once already approved", async () => {
    mockGetDetail.mockResolvedValue({
      ...pendingSubmission,
      status: "APPROVED",
      reviewedAt: "2026-08-02T00:00:00Z",
      reviewReason: "Verified.",
      resultingResource: { id: "res-1", name: "Halifax Food Bank", slug: "halifax-food-bank" },
    });
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    expect(screen.queryByRole("heading", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Halifax Food Bank" })).toHaveAttribute("href", "/resources/halifax-food-bank");
  });

  it("approving submits a reason and shows the resulting approval", async () => {
    mockGetDetail.mockResolvedValue(pendingSubmission);
    mockApprove.mockResolvedValue({
      ...pendingSubmission,
      status: "APPROVED",
      reviewedAt: "2026-08-02T00:00:00Z",
      reviewReason: "Verified against the source.",
      resultingResource: { id: "res-1", name: "Halifax Food Bank", slug: "halifax-food-bank" },
    });
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    const approveSection = screen.getByRole("heading", { name: "Approve" }).closest("section")!;
    await user.type(approveSection.querySelector("textarea")!, "Verified against the source.");
    await user.click(within(approveSection).getByRole("button", { name: "Approve" }));

    await waitFor(() => expect(mockApprove).toHaveBeenCalledWith("sub-1", "Verified against the source.", "token-1"));
  });

  it("shows a specific message when self-review is blocked", async () => {
    mockGetDetail.mockResolvedValue(pendingSubmission);
    mockApprove.mockRejectedValue(new ApiRequestError("A moderator or admin cannot review their own contribution.", 403, "SELF_REVIEW_NOT_ALLOWED"));
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    const approveSection = screen.getByRole("heading", { name: "Approve" }).closest("section")!;
    await user.type(approveSection.querySelector("textarea")!, "Approving my own submission.");
    await user.click(within(approveSection).getByRole("button", { name: "Approve" }));

    expect(await within(approveSection).findByRole("alert")).toHaveTextContent(/cannot review their own/i);
  });

  it("rejecting submits a reason", async () => {
    mockGetDetail.mockResolvedValue(pendingSubmission);
    mockReject.mockResolvedValue({ ...pendingSubmission, status: "REJECTED", reviewedAt: "2026-08-02T00:00:00Z", reviewReason: "Not enough detail." });
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    const rejectSection = screen.getByRole("heading", { name: "Reject" }).closest("section")!;
    await user.type(rejectSection.querySelector("textarea")!, "Not enough detail.");
    await user.click(within(rejectSection).getByRole("button", { name: "Reject" }));

    await waitFor(() => expect(mockReject).toHaveBeenCalledWith("sub-1", "Not enough detail.", "token-1"));
  });

  it("requires a reason of at least 5 characters before submitting", async () => {
    mockGetDetail.mockResolvedValue(pendingSubmission);
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("heading", { name: "Halifax Food Bank" });
    const approveSection = screen.getByRole("heading", { name: "Approve" }).closest("section")!;
    await user.type(approveSection.querySelector("textarea")!, "Hi");
    await user.click(within(approveSection).getByRole("button", { name: "Approve" }));

    expect(await within(approveSection).findByRole("alert")).toHaveTextContent(/at least 5 characters/i);
    expect(mockApprove).not.toHaveBeenCalled();
  });
});
