import { useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NearbyMapView } from "./nearby-map-view";
import { MapSearchProvider, useMapSearch } from "@/lib/map/map-search-context";
import { getNearbyResources } from "@/lib/api/resources";
import { useAuth } from "@/lib/auth/auth-provider";
import type { NearbyResourcePageResponse } from "@/lib/validation/schemas";

jest.mock("next/navigation", () => ({
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/lib/api/resources");
jest.mock("@/lib/auth/auth-provider");
const mockGetNearbyResources = getNearbyResources as jest.MockedFunction<typeof getNearbyResources>;
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

// The Leaflet-backed map is exercised separately in nearby-map.test.tsx —
// here it's replaced with a stub that exposes the same coordination props
// as clickable test hooks, so this file can test the surrounding
// loading/empty/error/pagination/selection logic without touching Leaflet.
jest.mock("@/components/map/nearby-map", () => ({
  NearbyMap: ({
    results,
    onSelectResource,
    onPendingCentreChange,
  }: {
    results: { id: string; name: string }[];
    onSelectResource: (id: string) => void;
    onPendingCentreChange: (centre: { latitude: number; longitude: number }) => void;
  }) => (
    <div data-testid="nearby-map-stub">
      <span>{results.length} markers</span>
      {results.map((r) => (
        <button key={r.id} onClick={() => onSelectResource(r.id)}>
          marker:{r.name}
        </button>
      ))}
      <button onClick={() => onPendingCentreChange({ latitude: 45, longitude: -64 })}>simulate-move</button>
    </div>
  ),
}));

function ActivateMapView() {
  const { setView } = useMapSearch();
  useEffect(() => setView("map"), [setView]);
  return null;
}

function renderNearbyMapView(filters = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MapSearchProvider>
        <ActivateMapView />
        <NearbyMapView filters={filters} />
      </MapSearchProvider>
    </QueryClientProvider>,
  );
}

const resourceSummary = {
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
  latitude: 44.6488,
  longitude: -63.5752,
  distanceMeters: 850,
};

function page(content: typeof resourceSummary[]): NearbyResourcePageResponse {
  return { content, page: 0, size: 12, totalElements: content.length, totalPages: content.length > 0 ? 1 : 0 };
}

describe("NearbyMapView", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
    mockUseAuth.mockReturnValue({
      state: { status: "unauthenticated" },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn(),
    });
  });

  it("defaults to central Halifax and says so", async () => {
    mockGetNearbyResources.mockResolvedValue(page([]));
    renderNearbyMapView();

    expect(await screen.findByText("Showing resources near central Halifax.")).toBeInTheDocument();
    expect(mockGetNearbyResources).toHaveBeenCalledWith(
      expect.objectContaining({ latitude: 44.6488, longitude: -63.5752 }),
    );
  });

  it("shows a loading state, then results with a count and cards", async () => {
    mockGetNearbyResources.mockResolvedValue(page([resourceSummary]));
    renderNearbyMapView();

    expect(await screen.findByText("1 resource within 5 km")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Halifax Central Library" })).toBeInTheDocument();
    expect(screen.getByText("1 markers")).toBeInTheDocument();
  });

  it("shows an empty state with actionable suggestions when nothing is nearby", async () => {
    mockGetNearbyResources.mockResolvedValue(page([]));
    renderNearbyMapView();

    expect(await screen.findByText("No resources found nearby.")).toBeInTheDocument();
    expect(screen.getByText(/increasing the search radius/)).toBeInTheDocument();
  });

  it("shows a recoverable inline error when the nearby request fails", async () => {
    mockGetNearbyResources.mockRejectedValue(new Error("network down"));
    renderNearbyMapView();

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn.t be loaded/i);
  });

  it("selecting a list card selects the matching marker (shared selection state)", async () => {
    const user = userEvent.setup();
    mockGetNearbyResources.mockResolvedValue(page([resourceSummary]));
    renderNearbyMapView();

    await screen.findByRole("button", { name: "Halifax Central Library" });
    await user.click(screen.getByRole("button", { name: "Halifax Central Library" }));

    expect(await screen.findByText("Selected")).toBeInTheDocument();
  });

  it("selecting a marker selects the matching list card", async () => {
    const user = userEvent.setup();
    mockGetNearbyResources.mockResolvedValue(page([resourceSummary]));
    renderNearbyMapView();

    await screen.findByText("marker:Halifax Central Library");
    await user.click(screen.getByText("marker:Halifax Central Library"));

    expect(await screen.findByText("Selected")).toBeInTheDocument();
  });

  it("changing the selected marker does not trigger a new nearby request", async () => {
    const user = userEvent.setup();
    mockGetNearbyResources.mockResolvedValue(page([resourceSummary]));
    renderNearbyMapView();

    await screen.findByText("marker:Halifax Central Library");
    const callsBeforeSelection = mockGetNearbyResources.mock.calls.length;

    await user.click(screen.getByText("marker:Halifax Central Library"));

    await waitFor(() => expect(screen.getByText("Selected")).toBeInTheDocument());
    expect(mockGetNearbyResources.mock.calls.length).toBe(callsBeforeSelection);
  });

  it("a map move (Search this area) does not itself trigger a refetch until committed", async () => {
    const user = userEvent.setup();
    mockGetNearbyResources.mockResolvedValue(page([resourceSummary]));
    renderNearbyMapView();

    await screen.findByText("simulate-move");
    const callsBefore = mockGetNearbyResources.mock.calls.length;

    await user.click(screen.getByText("simulate-move"));

    // Recording a pending centre is a synchronous, local state update with
    // no query-key change — nothing async to await before asserting.
    expect(mockGetNearbyResources.mock.calls.length).toBe(callsBefore);
  });

  it("clears a stale selection once it no longer appears in a new result page", async () => {
    const user = userEvent.setup();
    mockGetNearbyResources.mockResolvedValueOnce(page([resourceSummary]));
    renderNearbyMapView();

    await screen.findByRole("button", { name: "Halifax Central Library" });
    await user.click(screen.getByRole("button", { name: "Halifax Central Library" }));
    await screen.findByText("Selected");

    // A different result page (e.g. after a radius/filter change) no longer contains it.
    mockGetNearbyResources.mockResolvedValueOnce(page([]));
    // Force a refetch by changing the radius via the exposed control.
    await user.selectOptions(screen.getByLabelText("Search radius"), "10");

    await waitFor(() => expect(screen.queryByText("Selected")).not.toBeInTheDocument());
  });
});
