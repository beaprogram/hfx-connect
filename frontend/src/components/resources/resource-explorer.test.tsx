import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ResourceExplorer } from "./resource-explorer";
import { MapSearchProvider } from "@/lib/map/map-search-context";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import { getNearbyResources } from "@/lib/api/resources";
import type { CategoryPageResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/api/categories");
jest.mock("@/lib/api/resources");
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/components/map/nearby-map", () => ({
  NearbyMap: () => <div data-testid="nearby-map-stub" />,
}));

const mockGetCategories = getCategories as jest.MockedFunction<typeof getCategories>;
const mockGetResources = getResources as jest.MockedFunction<typeof getResources>;
const mockGetNearbyResources = getNearbyResources as jest.MockedFunction<typeof getNearbyResources>;

const categoryPage: CategoryPageResponse = {
  content: [
    { id: 1, name: "Study Spaces", slug: "study-spaces", description: null, active: true, createdAt: "2026-07-15T00:00:00Z", updatedAt: "2026-07-15T00:00:00Z" },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

function renderExplorer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MapSearchProvider>
        <ResourceExplorer params={{ page: 0, sort: "name" }} />
      </MapSearchProvider>
    </QueryClientProvider>,
  );
}

describe("ResourceExplorer", () => {
  beforeEach(() => {
    mockGetCategories.mockResolvedValue(categoryPage);
    mockGetResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
    mockGetNearbyResources.mockResolvedValue({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
  });

  it("renders the plain list experience by default, without requesting nearby data", async () => {
    renderExplorer();

    expect(await screen.findByText("No active resources are published yet.")).toBeInTheDocument();
    expect(mockGetNearbyResources).not.toHaveBeenCalled();
  });

  it("public list browsing works with the map JavaScript stub entirely absent from the DOM", async () => {
    renderExplorer();
    await screen.findByText("No active resources are published yet.");
    expect(screen.queryByTestId("nearby-map-stub")).not.toBeInTheDocument();
  });

  it("switching to Map activates the nearby experience, defaulting to central Halifax", async () => {
    const user = userEvent.setup();
    renderExplorer();
    await screen.findByText("No active resources are published yet.");

    await user.click(screen.getByRole("button", { name: "Map" }));

    expect(await screen.findByText("Showing resources near central Halifax.")).toBeInTheDocument();
    expect(await screen.findByTestId("nearby-map-stub")).toBeInTheDocument();
  });

  it("the map never becomes the only way to reach a resource — List is always one click away", async () => {
    const user = userEvent.setup();
    renderExplorer();
    await screen.findByText("No active resources are published yet.");
    await user.click(screen.getByRole("button", { name: "Map" }));
    await screen.findByTestId("nearby-map-stub");

    await user.click(screen.getByRole("button", { name: "List" }));

    expect(await screen.findByText("No active resources are published yet.")).toBeInTheDocument();
  });

  it("hides the Sort control in Map mode (nearby search is always distance-ordered)", async () => {
    const user = userEvent.setup();
    renderExplorer();
    await screen.findByText("No active resources are published yet.");
    expect(screen.getByLabelText("Sort")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Map" }));
    await screen.findByTestId("nearby-map-stub");

    expect(screen.queryByLabelText("Sort")).not.toBeInTheDocument();
  });
});
