import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UseMyLocationControl } from "./use-my-location-control";
import { MapSearchProvider } from "@/lib/map/map-search-context";

jest.mock("next/navigation", () => ({
  usePathname: () => "/resources",
  useSearchParams: () => new URLSearchParams(),
}));

function renderControl() {
  return render(
    <MapSearchProvider>
      <UseMyLocationControl />
    </MapSearchProvider>,
  );
}

function mockGeolocation(): { getCurrentPosition: jest.Mock } {
  const getCurrentPosition = jest.fn();
  Object.defineProperty(global.navigator, "geolocation", { value: { getCurrentPosition }, configurable: true });
  return { getCurrentPosition };
}

describe("UseMyLocationControl", () => {
  beforeEach(() => {
    // jsdom's `window.location` persists across tests in a file; the
    // provider reads it on mount, so each test needs a clean starting URL.
    window.history.replaceState(null, "", "/resources");
  });

  afterEach(() => {
    // @ts-expect-error -- test-only cleanup.
    delete global.navigator.geolocation;
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it("does not request geolocation on mount", () => {
    const { getCurrentPosition } = mockGeolocation();
    renderControl();
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });

  it("requests geolocation only after the button is activated", async () => {
    const user = userEvent.setup();
    const { getCurrentPosition } = mockGeolocation();
    renderControl();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
  });

  it("shows a calm fallback message on permission denial and keeps the app usable (retry available)", async () => {
    const user = userEvent.setup();
    mockGeolocation().getCurrentPosition.mockImplementation((_s: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    renderControl();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    expect(
      await screen.findByText(/location permission was not granted/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });

  it("shows a recoverable message on timeout", async () => {
    const user = userEvent.setup();
    mockGeolocation().getCurrentPosition.mockImplementation((_s: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 3, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    renderControl();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    expect(await screen.findByText(/took too long/i)).toBeInTheDocument();
  });

  it("shows a recoverable message when the position is unavailable", async () => {
    const user = userEvent.setup();
    mockGeolocation().getCurrentPosition.mockImplementation((_s: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 2, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    renderControl();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    expect(await screen.findByText(/could not be determined/i)).toBeInTheDocument();
  });

  it("retry after a denial requests geolocation again", async () => {
    const user = userEvent.setup();
    const { getCurrentPosition } = mockGeolocation();
    getCurrentPosition.mockImplementation((_s: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    renderControl();

    await user.click(screen.getByRole("button", { name: "Use my location" }));
    await screen.findByRole("button", { name: "Try again" });
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(getCurrentPosition).toHaveBeenCalledTimes(2);
  });

  it("announces success and does not store coordinates in localStorage or sessionStorage", async () => {
    const user = userEvent.setup();
    mockGeolocation().getCurrentPosition.mockImplementation((success: PositionCallback) => {
      success({ coords: { latitude: 44.6488, longitude: -63.5752 } } as GeolocationPosition);
    });
    renderControl();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    await waitFor(() => expect(screen.getByText(/location found/i)).toBeInTheDocument());
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
