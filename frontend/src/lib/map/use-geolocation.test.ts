import { act, renderHook } from "@testing-library/react";
import { useGeolocation } from "./use-geolocation";

function mockGeolocation(): {
  getCurrentPosition: jest.Mock;
} {
  const getCurrentPosition = jest.fn();
  Object.defineProperty(global.navigator, "geolocation", {
    value: { getCurrentPosition },
    configurable: true,
  });
  return { getCurrentPosition };
}

describe("useGeolocation", () => {
  afterEach(() => {
    // @ts-expect-error -- test-only cleanup of a property defined via defineProperty above.
    delete global.navigator.geolocation;
  });

  it("starts idle and never calls getCurrentPosition on its own", () => {
    const { getCurrentPosition } = mockGeolocation();
    const { result } = renderHook(() => useGeolocation());

    expect(result.current.status).toBe("idle");
    expect(result.current.coordinates).toBeNull();
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });

  it("requests a position only when requestLocation is called, and reports 'requesting' immediately", () => {
    const { getCurrentPosition } = mockGeolocation();
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(result.current.status).toBe("requesting");
  });

  it("stores coordinates and reports success on a successful fix", () => {
    const { getCurrentPosition } = mockGeolocation();
    getCurrentPosition.mockImplementation((success: PositionCallback) => {
      success({ coords: { latitude: 44.6488, longitude: -63.5752 } } as GeolocationPosition);
    });
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    expect(result.current.status).toBe("success");
    expect(result.current.coordinates).toEqual({ latitude: 44.6488, longitude: -63.5752 });
  });

  it("reports permission-denied on PERMISSION_DENIED", () => {
    const { getCurrentPosition } = mockGeolocation();
    getCurrentPosition.mockImplementation((_success: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    expect(result.current.status).toBe("permission-denied");
    expect(result.current.coordinates).toBeNull();
  });

  it("reports timeout on TIMEOUT", () => {
    const { getCurrentPosition } = mockGeolocation();
    getCurrentPosition.mockImplementation((_success: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 3, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    expect(result.current.status).toBe("timeout");
  });

  it("reports unavailable on POSITION_UNAVAILABLE", () => {
    const { getCurrentPosition } = mockGeolocation();
    getCurrentPosition.mockImplementation((_success: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 2, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    expect(result.current.status).toBe("unavailable");
  });

  it("reports unavailable when navigator.geolocation doesn't exist at all", () => {
    // @ts-expect-error -- deliberately absent for this case.
    delete global.navigator.geolocation;
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    expect(result.current.status).toBe("unavailable");
  });

  it("allows retry after a denial — a second requestLocation call requests again", () => {
    const { getCurrentPosition } = mockGeolocation();
    getCurrentPosition.mockImplementation((_success: PositionCallback, error: PositionErrorCallback) => {
      error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
    });
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());
    expect(result.current.status).toBe("permission-denied");

    act(() => result.current.requestLocation());
    expect(getCurrentPosition).toHaveBeenCalledTimes(2);
  });

  it("passes enableHighAccuracy: false, a timeout, and a maximumAge to getCurrentPosition", () => {
    const { getCurrentPosition } = mockGeolocation();
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.requestLocation());

    const options = getCurrentPosition.mock.calls[0]![2];
    expect(options).toMatchObject({ enableHighAccuracy: false });
    expect(options.timeout).toBeGreaterThan(0);
    expect(options.maximumAge).toBeGreaterThan(0);
  });
});
