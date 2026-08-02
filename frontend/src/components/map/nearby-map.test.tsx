import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { NearbyResourceSummaryResponse } from "@/lib/validation/schemas";
import type { MapCentre } from "@/lib/map/map-search-context";

/**
 * Leaflet manipulates real browser layout/canvas APIs jsdom doesn't
 * implement, so — per ADR-013's "Frontend Testing" — this file mocks
 * `react-leaflet`/`react-leaflet-cluster`/`leaflet` with lightweight
 * stand-ins and tests the coordination logic `NearbyMap` layers on top
 * (marker-per-result, no duplicates, click → selection, popup content,
 * moveend → pending centre) rather than any Leaflet-internal rendering.
 */
let moveEndHandler: (() => void) | undefined;
const fakeMap = {
  setView: jest.fn(),
  getZoom: jest.fn(() => 13),
  getCenter: jest.fn(() => ({ lat: 44.7, lng: -63.6 })),
  getBounds: jest.fn(() => ({ contains: jest.fn(() => true) })),
  panTo: jest.fn(),
};

jest.mock("react-leaflet", () => ({
  MapContainer: ({ children }: { children: React.ReactNode }) => <div data-testid="map-container">{children}</div>,
  TileLayer: () => <div data-testid="tile-layer" />,
  Marker: ({
    children,
    position,
    eventHandlers,
  }: {
    children: React.ReactNode;
    position: [number, number];
    eventHandlers?: { click?: () => void };
  }) => (
    <button
      type="button"
      data-testid="marker"
      data-lat={position[0]}
      data-lng={position[1]}
      onClick={eventHandlers?.click}
    >
      {children}
    </button>
  ),
  Popup: ({ children }: { children: React.ReactNode }) => <div data-testid="popup">{children}</div>,
  useMap: () => fakeMap,
  useMapEvents: (handlers: { moveend?: () => void }) => {
    moveEndHandler = handlers.moveend;
    return fakeMap;
  },
}));

jest.mock("react-leaflet-cluster", () => ({
  __esModule: true,
  default: ({ children }: { children: React.ReactNode }) => <div data-testid="cluster-group">{children}</div>,
}));

import { NearbyMap } from "./nearby-map";

const centre: MapCentre = { latitude: 44.6488, longitude: -63.5752 };

function buildResource(overrides: Partial<NearbyResourceSummaryResponse>): NearbyResourceSummaryResponse {
  return {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Halifax Central Library",
    slug: "halifax-central-library",
    city: "Halifax",
    province: "NS",
    costType: "FREE",
    verificationStatus: "VERIFIED",
    active: true,
    category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
    createdAt: "2026-07-15T00:00:00Z",
    hoursStatus: "OPEN",
    openNow: true,
    latitude: 44.6488,
    longitude: -63.5752,
    distanceMeters: 850,
    ...overrides,
  };
}

describe("NearbyMap", () => {
  beforeEach(() => {
    moveEndHandler = undefined;
    jest.clearAllMocks();
  });

  it("renders one marker per nearby result inside the cluster group", () => {
    const results = [
      buildResource({ id: "a", name: "Library" }),
      buildResource({ id: "b", name: "Food Bank", latitude: 44.66, longitude: -63.58 }),
    ];

    render(
      <NearbyMap
        centre={centre}
        results={results}
        selectedResourceId={null}
        onSelectResource={jest.fn()}
        onPendingCentreChange={jest.fn()}
      />,
    );

    const markers = screen.getAllByTestId("marker");
    expect(markers).toHaveLength(2);
  });

  it("renders no markers for an empty result set", () => {
    render(
      <NearbyMap centre={centre} results={[]} selectedResourceId={null} onSelectResource={jest.fn()} onPendingCentreChange={jest.fn()} />,
    );

    expect(screen.queryAllByTestId("marker")).toHaveLength(0);
  });

  it("renders exactly one marker per distinct result — no markers invented beyond the result set", () => {
    const results = [
      buildResource({ id: "a" }),
      buildResource({ id: "b", latitude: 44.66, longitude: -63.58 }),
      buildResource({ id: "c", latitude: 44.61, longitude: -63.6 }),
    ];
    render(
      <NearbyMap centre={centre} results={results} selectedResourceId={null} onSelectResource={jest.fn()} onPendingCentreChange={jest.fn()} />,
    );

    expect(screen.getAllByTestId("marker")).toHaveLength(results.length);
  });

  it("uses the backend-provided coordinates for each marker, not any recalculated value", () => {
    const results = [buildResource({ id: "a", latitude: 44.601, longitude: -63.611 })];
    render(
      <NearbyMap centre={centre} results={results} selectedResourceId={null} onSelectResource={jest.fn()} onPendingCentreChange={jest.fn()} />,
    );

    const marker = screen.getByTestId("marker");
    expect(marker).toHaveAttribute("data-lat", "44.601");
    expect(marker).toHaveAttribute("data-lng", "-63.611");
  });

  it("popup includes name, category, distance, and a details link — a safe summary, not the full record", () => {
    const results = [buildResource({ id: "a", name: "Halifax Central Library", distanceMeters: 1204.7 })];
    render(
      <NearbyMap centre={centre} results={results} selectedResourceId={null} onSelectResource={jest.fn()} onPendingCentreChange={jest.fn()} />,
    );

    expect(screen.getByText("Halifax Central Library")).toBeInTheDocument();
    expect(screen.getByText(/Study Spaces/)).toBeInTheDocument();
    expect(screen.getByText(/1\.2 km away/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View details" })).toHaveAttribute(
      "href",
      "/resources/halifax-central-library",
    );
  });

  it("clicking a marker calls onSelectResource with that resource's id", async () => {
    const user = userEvent.setup();
    const onSelectResource = jest.fn();
    const results = [buildResource({ id: "a" })];
    render(
      <NearbyMap centre={centre} results={results} selectedResourceId={null} onSelectResource={onSelectResource} onPendingCentreChange={jest.fn()} />,
    );

    await user.click(screen.getByTestId("marker"));

    expect(onSelectResource).toHaveBeenCalledWith("a");
  });

  it("moveend reports the map's new centre without refetching (no query — just the callback)", () => {
    const onPendingCentreChange = jest.fn();
    render(
      <NearbyMap centre={centre} results={[]} selectedResourceId={null} onSelectResource={jest.fn()} onPendingCentreChange={onPendingCentreChange} />,
    );

    expect(moveEndHandler).toBeDefined();
    moveEndHandler!();

    expect(onPendingCentreChange).toHaveBeenCalledWith({ latitude: 44.7, longitude: -63.6 });
  });
});
