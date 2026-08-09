import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AdminOrganizationDetail } from "./admin-organization-detail";
import { useAuth } from "@/lib/auth/auth-provider";
import { getAdminOrganization, verifyOrganization, getOrganizationAuditEvents } from "@/lib/api/organization";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/organization");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetDetail = getAdminOrganization as jest.MockedFunction<typeof getAdminOrganization>;
const mockVerify = verifyOrganization as jest.MockedFunction<typeof verifyOrganization>;
const mockAuditEvents = getOrganizationAuditEvents as jest.MockedFunction<typeof getOrganizationAuditEvents>;

const pendingOrganization = {
  id: "org-1",
  ownerUserId: "owner-1",
  name: "Halifax Newcomer Services",
  slug: "halifax-newcomer-services",
  description: null,
  websiteUrl: null,
  publicEmail: null,
  phone: null,
  addressLine1: null,
  city: null,
  province: null,
  postalCode: null,
  verificationStatus: "PENDING_VERIFICATION" as const,
  verifiedByUserId: null,
  verifiedAt: null,
  verificationReason: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

function renderDetail() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminOrganizationDetail organizationId="org-1" />
    </QueryClientProvider>,
  );
}

describe("AdminOrganizationDetail", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: { id: "admin-1", email: "admin@example.org", role: "ADMIN", status: "ACTIVE", emailVerified: true, createdAt: "2026-07-15T00:00:00Z" },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
    });
    mockAuditEvents.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it("shows verify/reject forms while pending", async () => {
    mockGetDetail.mockResolvedValue(pendingOrganization);
    renderDetail();

    expect(await screen.findByRole("heading", { name: "Halifax Newcomer Services" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Verify" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Reject" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Suspend" })).not.toBeInTheDocument();
  });

  it("shows only a suspend form once verified", async () => {
    mockGetDetail.mockResolvedValue({
      ...pendingOrganization,
      verificationStatus: "VERIFIED",
      verifiedByUserId: "admin-2",
      verifiedAt: "2026-08-02T00:00:00Z",
      verificationReason: "Confirmed.",
    });
    renderDetail();

    await screen.findByRole("heading", { name: "Halifax Newcomer Services" });
    expect(screen.getByRole("heading", { name: "Suspend" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Verify" })).not.toBeInTheDocument();
  });

  it("verifying submits a reason", async () => {
    mockGetDetail.mockResolvedValue(pendingOrganization);
    mockVerify.mockResolvedValue({ ...pendingOrganization, verificationStatus: "VERIFIED", verifiedByUserId: "admin-1", verifiedAt: "2026-08-02T00:00:00Z" });
    const user = userEvent.setup();
    renderDetail();

    await screen.findByRole("heading", { name: "Halifax Newcomer Services" });
    const verifySection = screen.getByRole("heading", { name: "Verify" }).closest("section")!;
    await user.type(verifySection.querySelector("textarea")!, "Confirmed against public registry.");
    await user.click(within(verifySection).getByRole("button", { name: "Verify" }));

    await waitFor(() => expect(mockVerify).toHaveBeenCalledWith("org-1", "Confirmed against public registry.", "token-1"));
  });

  it("shows a specific message when self-review is blocked", async () => {
    mockGetDetail.mockResolvedValue(pendingOrganization);
    mockVerify.mockRejectedValue(new ApiRequestError("You cannot review your own contribution.", 403, "SELF_REVIEW_NOT_ALLOWED"));
    const user = userEvent.setup();
    renderDetail();

    await screen.findByRole("heading", { name: "Halifax Newcomer Services" });
    const verifySection = screen.getByRole("heading", { name: "Verify" }).closest("section")!;
    await user.type(verifySection.querySelector("textarea")!, "Approving my own organization.");
    await user.click(within(verifySection).getByRole("button", { name: "Verify" }));

    expect(await within(verifySection).findByRole("alert")).toHaveTextContent(/cannot review your own/i);
  });
});
