import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResourceListView } from "./resource-list-view";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import { useAuth } from "@/lib/auth/auth-provider";
import type { CategoryPageResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/api/categories");
jest.mock("@/lib/api/resources");
jest.mock("@/lib/auth/auth-provider");
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn() }),
}));

const mockGetCategories = getCategories as jest.MockedFunction<typeof getCategories>;
const mockGetResources = getResources as jest.MockedFunction<typeof getResources>;
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

const categoryPage: CategoryPageResponse = {
  content: [{ id: 1, name: "Study Spaces", slug: "study-spaces", description: null, active: true, createdAt: "2026-07-15T00:00:00Z", updatedAt: "2026-07-15T00:00:00Z" }],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

const resourceSummary = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  city: "Halifax",
  province: "NS",
  costType: "FREE" as const,
  verificationStatus: "UNVERIFIED" as const,
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  hoursStatus: "UNKNOWN" as const,
  openNow: null,
};

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("ResourceListView", () => {
  beforeEach(() => {
    mockGetCategories.mockResolvedValue(categoryPage);
    mockUseAuth.mockReturnValue({
      state: { status: "unauthenticated" },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn(),
    });
  });

  it("shows a result count and cards once resources load", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name" }} />);

    await waitFor(() => expect(screen.getByText("1 resource found")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Halifax Central Library" })).toBeInTheDocument();
  });

  it("shows a generic empty state when no resources exist and no filter is applied", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name" }} />);

    await waitFor(() => expect(screen.getByText("No active resources are published yet.")).toBeInTheDocument());
  });

  it("names the selected category in the empty state and offers a reset link", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", categoryId: 1 }} />);

    await waitFor(() => expect(screen.getByText("No active resources in Study Spaces yet.")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Reset filters" })).toHaveAttribute("href", "/resources");
  });

  it("shows an inline error when the resource request fails", async () => {
    mockGetResources.mockRejectedValue(new Error("network down"));

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name" }} />);

    await waitFor(() =>
      expect(screen.getByText(/Resources couldn't be loaded right now/i)).toBeInTheDocument(),
    );
  });

  it("shows a fallback note when the URL's categoryId doesn't match any known category", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", categoryId: 999 }} />);

    await waitFor(() =>
      expect(screen.getByText(/requested category couldn't be found/i)).toBeInTheDocument(),
    );
  });

  it("renders the category filter options from the backend, not hard-coded", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name" }} />);

    await waitFor(() => expect(screen.getByRole("option", { name: "Study Spaces" })).toBeInTheDocument());
  });

  it("labels the category and sort controls so they're identifiable without relying on placeholder text", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name" }} />);

    await waitFor(() => expect(screen.getByLabelText("Category")).toBeInTheDocument());
    expect(screen.getByLabelText("Sort")).toBeInTheDocument();
  });

  it("passes q through to getResources and shows a keyword-aware result summary", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", q: "library" }} />);

    await waitFor(() => expect(screen.getByText('1 resource matching "library"')).toBeInTheDocument());
    expect(mockGetResources).toHaveBeenCalledWith(expect.objectContaining({ q: "library" }));
  });

  it("includes the category name in the result summary when both q and categoryId are set", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", q: "library", categoryId: 1 }} />);

    await waitFor(() =>
      expect(screen.getByText('1 resource matching "library" in Study Spaces')).toBeInTheDocument(),
    );
  });

  it("shows a keyword-specific no-results message distinct from the generic empty state", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", q: "no-such-resource" }} />);

    await waitFor(() =>
      expect(screen.getByText('No active resources matched "no-such-resource".')).toBeInTheDocument(),
    );
    expect(screen.getByText(/try a different or shorter search term/i)).toBeInTheDocument();
  });

  it("shows a combined keyword+category no-results message", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", q: "no-match", categoryId: 1 }} />);

    await waitFor(() =>
      expect(screen.getByText('No active resources matched "no-match" in Study Spaces.')).toBeInTheDocument(),
    );
  });

  it("shows the search phrase and a reset-filters link in the filter summary line", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", q: "library" }} />);

    await waitFor(() => expect(screen.getByText(/searching for/i)).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Reset filters" })).toHaveAttribute("href", "/resources");
  });

  it("never claims relevance ranking in the result summary", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", q: "library" }} />);

    await waitFor(() => expect(screen.getByText('1 resource matching "library"')).toBeInTheDocument());
    expect(screen.queryByText(/most relevant/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/relevance/i)).not.toBeInTheDocument();
  });

  // ---- Cost/verification/openNow filters (Milestone 6B — see ADR-011) ----

  it("passes costType, verificationStatus, and openNow through to getResources", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(
      <ResourceListView
        params={{ page: 0, sort: "name", costType: "FREE", verificationStatus: "VERIFIED", openNow: true }}
      />,
    );

    await waitFor(() =>
      expect(mockGetResources).toHaveBeenCalledWith(
        expect.objectContaining({ costType: "FREE", verificationStatus: "VERIFIED", openNow: true }),
      ),
    );
  });

  it("shows an active-filter summary and a reset link when only cost/verification/openNow filters are set", async () => {
    mockGetResources.mockResolvedValue({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", costType: "FREE", openNow: true }} />);

    await waitFor(() => expect(screen.getByText("Free, Open now")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Reset filters" })).toHaveAttribute("href", "/resources");
  });

  it("shows a filters-aware no-results message when cost/verification/openNow filters yield nothing", async () => {
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });

    renderWithQueryClient(<ResourceListView params={{ page: 0, sort: "name", costType: "PAID" }} />);

    await waitFor(() =>
      expect(screen.getByText("No active resources match the selected filters.")).toBeInTheDocument(),
    );
    expect(screen.getByText(/Try removing one or more filters/i)).toBeInTheDocument();
  });
});
