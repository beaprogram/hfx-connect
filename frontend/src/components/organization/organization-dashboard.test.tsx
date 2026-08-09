import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { OrganizationDashboard } from "./organization-dashboard";
import { useAuth } from "@/lib/auth/auth-provider";
import { getOrganizationProfile, createOrganizationProfile, getOrganizationResources, getOrganizationClaims } from "@/lib/api/organization";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/organization");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetProfile = getOrganizationProfile as jest.MockedFunction<typeof getOrganizationProfile>;
const mockCreate = createOrganizationProfile as jest.MockedFunction<typeof createOrganizationProfile>;
const mockGetResources = getOrganizationResources as jest.MockedFunction<typeof getOrganizationResources>;
const mockGetClaims = getOrganizationClaims as jest.MockedFunction<typeof getOrganizationClaims>;

const verifiedProfile = {
  id: "org-1",
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
  verificationStatus: "VERIFIED" as const,
  verifiedAt: "2026-08-01T00:00:00Z",
  verificationReason: "Confirmed.",
  createdAt: "2026-07-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

function renderDashboard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationDashboard />
    </QueryClientProvider>,
  );
}

describe("OrganizationDashboard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: { id: "1", email: "org@example.org", role: "ORGANIZATION", status: "ACTIVE", emailVerified: true, createdAt: "2026-07-15T00:00:00Z" },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
    });
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    mockGetClaims.mockResolvedValue({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
  });

  it("shows a create-profile form when the account has no profile yet (404)", async () => {
    mockGetProfile.mockRejectedValue(new ApiRequestError("The current account has no organization profile yet.", 404, "ORGANIZATION_NOT_FOUND"));

    renderDashboard();

    expect(await screen.findByRole("heading", { name: /create your organization profile/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /create profile/i })).toBeInTheDocument();
  });

  it("creating a profile never lets the caller set verification status — only name/contact fields are submitted", async () => {
    mockGetProfile.mockRejectedValue(new ApiRequestError("none", 404, "ORGANIZATION_NOT_FOUND"));
    mockCreate.mockResolvedValue({ ...verifiedProfile, verificationStatus: "PENDING_VERIFICATION", verifiedAt: null, verificationReason: null });
    const user = userEvent.setup();
    renderDashboard();

    const nameInput = await screen.findByLabelText(/organization name/i);
    await user.type(nameInput, "Halifax Newcomer Services");
    await user.click(screen.getByRole("button", { name: /create profile/i }));

    await waitFor(() => expect(mockCreate).toHaveBeenCalled());
    const submittedInput = mockCreate.mock.calls[0]![0];
    expect(submittedInput).toEqual({ name: "Halifax Newcomer Services" });
    expect(submittedInput).not.toHaveProperty("verificationStatus");
    expect(submittedInput).not.toHaveProperty("ownerUserId");
  });

  it("shows the verified status and never claims verification before the backend confirms it", async () => {
    mockGetProfile.mockResolvedValue(verifiedProfile);

    renderDashboard();

    expect(await screen.findByText("Halifax Newcomer Services")).toBeInTheDocument();
    expect(screen.getByText(/your organization is verified/i)).toBeInTheDocument();
  });

  it("shows a pending-verification explanation, not a premature verified claim", async () => {
    mockGetProfile.mockResolvedValue({ ...verifiedProfile, verificationStatus: "PENDING_VERIFICATION", verifiedAt: null, verificationReason: null });

    renderDashboard();

    expect(await screen.findByText(/must be verified before you can claim/i)).toBeInTheDocument();
    expect(screen.queryByText(/your organization is verified/i)).not.toBeInTheDocument();
  });

  it("shows the rejection reason when rejected", async () => {
    mockGetProfile.mockResolvedValue({ ...verifiedProfile, verificationStatus: "REJECTED", verifiedAt: null, verificationReason: "Could not confirm registration." });

    renderDashboard();

    expect(await screen.findByText(/was not verified/i)).toBeInTheDocument();
    expect(screen.getByText(/could not confirm registration/i)).toBeInTheDocument();
  });
});
