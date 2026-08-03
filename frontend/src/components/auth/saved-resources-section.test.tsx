import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SavedResourcesSection } from "./saved-resources-section";
import { useAuth } from "@/lib/auth/auth-provider";
import { getSavedResources } from "@/lib/api/saved-resources";
import { removeSavedResource } from "@/lib/api/saved-resources";
import type { SavedResourcePageResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/saved-resources");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetSavedResources = getSavedResources as jest.MockedFunction<typeof getSavedResources>;
const mockRemoveSavedResource = removeSavedResource as jest.MockedFunction<typeof removeSavedResource>;

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SavedResourcesSection />
    </QueryClientProvider>,
  );
}

const savedResource = {
  savedAt: "2026-08-01T00:00:00Z",
  resource: {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Halifax Central Library",
    slug: "halifax-central-library",
    city: "Halifax",
    province: "NS",
    costType: "FREE" as const,
    verificationStatus: "VERIFIED" as const,
    active: true,
    category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
    createdAt: "2026-07-15T00:00:00Z",
    hoursStatus: "OPEN" as const,
    openNow: true,
  },
};

function page(content: typeof savedResource[], totalPages = content.length > 0 ? 1 : 0): SavedResourcePageResponse {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages };
}

describe("SavedResourcesSection", () => {
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

  it("has a Saved Resources heading", async () => {
    mockGetSavedResources.mockResolvedValue(page([]));
    renderWithQueryClient();

    expect(screen.getByRole("heading", { name: "Saved Resources" })).toBeInTheDocument();
  });

  it("shows a loading state before data arrives", () => {
    mockGetSavedResources.mockReturnValue(new Promise(() => {}));
    renderWithQueryClient();

    expect(screen.getByRole("status")).toHaveTextContent(/loading your saved resources/i);
  });

  it("shows an empty state with a link to browse resources", async () => {
    mockGetSavedResources.mockResolvedValue(page([]));
    renderWithQueryClient();

    expect(await screen.findByText("You have not saved any resources yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse resources" })).toHaveAttribute("href", "/resources");
  });

  it("shows saved resource cards with name, category, cost/verification/hours, saved date, and a detail link", async () => {
    mockGetSavedResources.mockResolvedValue(page([savedResource]));
    renderWithQueryClient();

    expect(await screen.findByRole("link", { name: "Halifax Central Library" })).toHaveAttribute(
      "href",
      "/resources/halifax-central-library",
    );
    expect(screen.getByText(/Study Spaces/)).toBeInTheDocument();
    expect(screen.getByText("Free")).toBeInTheDocument();
    expect(screen.getByText(/Verified/)).toBeInTheDocument();
    expect(screen.getByText("Open now")).toBeInTheDocument();
    expect(screen.getByText(/Saved Jul 31, 2026/)).toBeInTheDocument();
  });

  it("shows a recoverable error state with a retry action", async () => {
    mockGetSavedResources.mockRejectedValue(new Error("network down"));
    renderWithQueryClient();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });

  it("removes a saved resource when Remove is clicked", async () => {
    mockGetSavedResources.mockResolvedValue(page([savedResource]));
    mockRemoveSavedResource.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Central Library" });
    await user.click(screen.getByRole("button", { name: "Remove Halifax Central Library from saved resources" }));

    await waitFor(() => expect(mockRemoveSavedResource).toHaveBeenCalledWith(savedResource.resource.id, "token-1"));
  });

  it("shows pagination controls when there is more than one page", async () => {
    mockGetSavedResources.mockResolvedValue(page([savedResource], 2));
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Central Library" });
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next page" })).toBeEnabled();
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
  });

  it("does not show pagination controls for a single page", async () => {
    mockGetSavedResources.mockResolvedValue(page([savedResource], 1));
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Central Library" });
    expect(screen.queryByRole("navigation", { name: "Saved-resource pages" })).not.toBeInTheDocument();
  });

  it("requests the next page when Next page is clicked", async () => {
    mockGetSavedResources.mockResolvedValue(page([savedResource], 2));
    const user = userEvent.setup();
    renderWithQueryClient();

    await screen.findByRole("link", { name: "Halifax Central Library" });
    await user.click(screen.getByRole("button", { name: "Next page" }));

    await waitFor(() =>
      expect(mockGetSavedResources).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })),
    );
  });
});
