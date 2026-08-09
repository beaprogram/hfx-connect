import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ClaimResourceControl } from "./claim-resource-control";
import { useAuth } from "@/lib/auth/auth-provider";
import { getOrganizationProfile, claimResource } from "@/lib/api/organization";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/organization");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetProfile = getOrganizationProfile as jest.MockedFunction<typeof getOrganizationProfile>;
const mockClaim = claimResource as jest.MockedFunction<typeof claimResource>;

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

function mockRole(role: "USER" | "ORGANIZATION" | "MODERATOR" | "ADMIN" | null) {
  if (role === null) {
    mockUseAuth.mockReturnValue({
      state: { status: "unauthenticated" },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue(null),
    });
    return;
  }
  mockUseAuth.mockReturnValue({
    state: {
      status: "authenticated",
      user: { id: "1", email: "a@example.org", role, status: "ACTIVE", emailVerified: true, createdAt: "2026-07-15T00:00:00Z" },
      accessToken: "token-1",
      expiresAt: Date.now() + 900_000,
    },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
  });
}

function renderControl() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ClaimResourceControl resourceId="res-1" />
    </QueryClientProvider>,
  );
}

describe("ClaimResourceControl", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders nothing when signed out", () => {
    mockRole(null);
    const { container } = renderControl();
    expect(container).toBeEmptyDOMElement();
  });

  it.each(["USER", "MODERATOR", "ADMIN"] as const)("renders nothing for a %s account", (role) => {
    mockRole(role);
    const { container } = renderControl();
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing for an ORGANIZATION account whose profile is still pending verification", async () => {
    mockRole("ORGANIZATION");
    mockGetProfile.mockResolvedValue({ ...verifiedProfile, verificationStatus: "PENDING_VERIFICATION", verifiedAt: null });

    const { container } = renderControl();

    await waitFor(() => expect(mockGetProfile).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole("button", { name: /claim this listing/i })).not.toBeInTheDocument();
  });

  it("shows the claim button for a VERIFIED organization account", async () => {
    mockRole("ORGANIZATION");
    mockGetProfile.mockResolvedValue(verifiedProfile);

    renderControl();

    expect(await screen.findByRole("button", { name: /claim this listing/i })).toBeInTheDocument();
  });

  it("submitting a claim shows the pending-review confirmation and never sends a client-supplied organization id", async () => {
    mockRole("ORGANIZATION");
    mockGetProfile.mockResolvedValue(verifiedProfile);
    mockClaim.mockResolvedValue({
      id: "claim-1",
      resource: null,
      status: "PENDING_REVIEW",
      requestedAt: "2026-08-01T00:00:00Z",
      reviewedAt: null,
      reviewReason: null,
    });
    const user = userEvent.setup();
    renderControl();

    const button = await screen.findByRole("button", { name: /claim this listing/i });
    await user.click(button);

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/pending admin review/i));
    expect(mockClaim).toHaveBeenCalledWith("res-1", "token-1");
    expect(screen.getByRole("link", { name: /view your claims/i })).toHaveAttribute("href", "/organization/claims");
  });

  it("shows the backend's error message on failure (e.g. RESOURCE_ALREADY_OWNED race)", async () => {
    mockRole("ORGANIZATION");
    mockGetProfile.mockResolvedValue(verifiedProfile);
    mockClaim.mockRejectedValue(new ApiRequestError("This resource is already owned by an organization.", 409, "RESOURCE_ALREADY_OWNED"));
    const user = userEvent.setup();
    renderControl();

    const button = await screen.findByRole("button", { name: /claim this listing/i });
    await user.click(button);

    expect(await screen.findByRole("alert")).toHaveTextContent(/already owned/i);
    expect(screen.getByRole("button", { name: /claim this listing/i })).toBeInTheDocument();
  });
});
