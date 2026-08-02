import { act, renderHook } from "@testing-library/react";
import { MapSearchProvider, useMapSearch } from "./map-search-context";
import { HALIFAX_DEFAULT_CENTER } from "@/lib/constants/map";
import { usePathname } from "next/navigation";

jest.mock("next/navigation", () => ({
  usePathname: jest.fn(() => "/resources"),
  useSearchParams: () => new URLSearchParams(),
}));

const mockUsePathname = usePathname as jest.Mock;

function wrapper({ children }: { children: React.ReactNode }) {
  return <MapSearchProvider>{children}</MapSearchProvider>;
}

describe("useMapSearch", () => {
  // jsdom's `window.location` persists across tests within a file; the
  // provider's URL-restoration effect reads it on every mount, so each
  // test needs a clean starting URL to stay isolated from the last.
  beforeEach(() => {
    window.history.replaceState(null, "", "/resources");
    mockUsePathname.mockReturnValue("/resources");
  });

  it("throws when used outside a MapSearchProvider", () => {
    // Swallow the expected React error-boundary console noise for this one case.
    const consoleError = jest.spyOn(console, "error").mockImplementation(() => {});
    expect(() => renderHook(() => useMapSearch())).toThrow(/MapSearchProvider/);
    consoleError.mockRestore();
  });

  it("starts in list view with no search centre", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });
    expect(result.current.view).toBe("list");
    expect(result.current.centre).toBeNull();
  });

  it("defaults to the Halifax centre the first time map view is activated", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });

    act(() => result.current.setView("map"));

    expect(result.current.view).toBe("map");
    expect(result.current.centre).toEqual(HALIFAX_DEFAULT_CENTER);
    expect(result.current.centreSource).toBe("default");
  });

  it("does not overwrite an already-chosen centre when map view is activated again", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });

    act(() => result.current.setView("map"));
    act(() => result.current.applyGeolocatedCentre({ latitude: 1, longitude: 2 }));
    act(() => result.current.setView("list"));
    act(() => result.current.setView("map"));

    expect(result.current.centre).toEqual({ latitude: 1, longitude: 2 });
    expect(result.current.centreSource).toBe("geolocation");
  });

  it("applyGeolocatedCentre sets the centre, marks the source, clears any pending centre, and resets the page", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });
    act(() => result.current.setView("map"));
    act(() => result.current.setNearbyPage(2));
    act(() => result.current.setPendingCentre({ latitude: 9, longitude: 9 }));

    act(() => result.current.applyGeolocatedCentre({ latitude: 44.65, longitude: -63.6 }));

    expect(result.current.centre).toEqual({ latitude: 44.65, longitude: -63.6 });
    expect(result.current.centreSource).toBe("geolocation");
    expect(result.current.pendingCentre).toBeNull();
    expect(result.current.nearbyPage).toBe(0);
  });

  it("hasPendingSearch is false until the map has moved past the threshold", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });
    act(() => result.current.setView("map"));

    // A tiny move (a few metres) stays below the threshold.
    act(() => result.current.setPendingCentre({ latitude: HALIFAX_DEFAULT_CENTER.latitude + 0.00001, longitude: HALIFAX_DEFAULT_CENTER.longitude }));
    expect(result.current.hasPendingSearch).toBe(false);

    // A real move (roughly a few hundred metres) exceeds it.
    act(() => result.current.setPendingCentre({ latitude: HALIFAX_DEFAULT_CENTER.latitude + 0.01, longitude: HALIFAX_DEFAULT_CENTER.longitude }));
    expect(result.current.hasPendingSearch).toBe(true);
  });

  it("searchThisArea commits the pending centre, marks the source manual, resets the page, and clears pending", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });
    act(() => result.current.setView("map"));
    act(() => result.current.setNearbyPage(3));
    act(() => result.current.setPendingCentre({ latitude: 45, longitude: -64 }));

    act(() => result.current.searchThisArea());

    expect(result.current.centre).toEqual({ latitude: 45, longitude: -64 });
    expect(result.current.centreSource).toBe("manual");
    expect(result.current.pendingCentre).toBeNull();
    expect(result.current.nearbyPage).toBe(0);
  });

  it("searchThisArea does nothing when there is no pending centre", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });
    act(() => result.current.setView("map"));
    const centreBefore = result.current.centre;

    act(() => result.current.searchThisArea());

    expect(result.current.centre).toEqual(centreBefore);
  });

  it("setRadiusKm updates the radius and resets the page", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });
    act(() => result.current.setNearbyPage(2));

    act(() => result.current.setRadiusKm(25));

    expect(result.current.radiusKm).toBe(25);
    expect(result.current.nearbyPage).toBe(0);
  });

  it("never writes centre/selection state to localStorage or sessionStorage", () => {
    const { result } = renderHook(() => useMapSearch(), { wrapper });

    act(() => result.current.setView("map"));
    act(() => result.current.applyGeolocatedCentre({ latitude: 44.6488, longitude: -63.5752 }));
    act(() => result.current.setSelectedResourceId("11111111-1111-1111-1111-111111111111"));

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it("mirrors view/radiusKm to the URL once active, and removes them back in list view", () => {
    window.history.replaceState(null, "", "/resources");
    const { result } = renderHook(() => useMapSearch(), { wrapper });

    act(() => result.current.setView("map"));
    expect(new URLSearchParams(window.location.search).get("view")).toBe("map");
    expect(new URLSearchParams(window.location.search).get("radiusKm")).toBe("5");

    act(() => result.current.setView("list"));
    expect(new URLSearchParams(window.location.search).has("view")).toBe(false);
    expect(new URLSearchParams(window.location.search).has("radiusKm")).toBe(false);
  });

  it("does not leak view/radiusKm onto a resource-detail page's URL after navigating away from /resources", () => {
    window.history.replaceState(null, "", "/resources");
    const { result, rerender } = renderHook(() => useMapSearch(), { wrapper });

    act(() => result.current.setView("map"));
    expect(new URLSearchParams(window.location.search).get("view")).toBe("map");

    // Simulate navigating to a resource-detail page under the same layout —
    // the pathname changes, but this provider instance stays mounted.
    window.history.replaceState(null, "", "/resources/halifax-central-library");
    mockUsePathname.mockReturnValue("/resources/halifax-central-library");
    rerender();

    expect(window.location.search).toBe("");
  });

  it("never puts latitude/longitude in the URL", () => {
    window.history.replaceState(null, "", "/resources");
    const { result } = renderHook(() => useMapSearch(), { wrapper });

    act(() => result.current.setView("map"));
    act(() => result.current.applyGeolocatedCentre({ latitude: 44.6488, longitude: -63.5752 }));

    expect(window.location.search).not.toContain("lat");
    expect(window.location.search).not.toContain("63.5752");
  });
});
