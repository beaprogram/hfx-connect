"use client";

import { useCallback, useRef, useState } from "react";
import { GEOLOCATION_OPTIONS } from "@/lib/constants/map";

export type GeolocationStatus =
  | "idle"
  | "requesting"
  | "success"
  | "permission-denied"
  | "unavailable"
  | "timeout";

export interface GeolocationCoordinates {
  latitude: number;
  longitude: number;
}

interface UseGeolocationResult {
  status: GeolocationStatus;
  coordinates: GeolocationCoordinates | null;
  /** Requests a position. Safe to call again after any terminal state (denial/timeout/unavailable) — this is the explicit retry path. */
  requestLocation: () => void;
}

/**
 * Wraps `navigator.geolocation.getCurrentPosition` behind an explicit
 * `requestLocation()` trigger — nothing in this hook calls it automatically
 * (see ADR-013's "Browser Geolocation Flow": geolocation must never be
 * requested on page load). Coordinates are held only in this hook's own
 * React state (component memory), never written to localStorage,
 * sessionStorage, or the URL — see ADR-013's "Location Privacy" section.
 */
export function useGeolocation(): UseGeolocationResult {
  const [status, setStatus] = useState<GeolocationStatus>("idle");
  const [coordinates, setCoordinates] = useState<GeolocationCoordinates | null>(null);
  // Guards against a stale async callback (a slow getCurrentPosition call
  // that resolves after a newer request already started) overwriting a more
  // recent result.
  const requestIdRef = useRef(0);

  const requestLocation = useCallback(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setStatus("unavailable");
      return;
    }

    const requestId = ++requestIdRef.current;
    setStatus("requesting");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (requestIdRef.current !== requestId) return;
        setCoordinates({ latitude: position.coords.latitude, longitude: position.coords.longitude });
        setStatus("success");
      },
      (error) => {
        if (requestIdRef.current !== requestId) return;
        if (error.code === error.PERMISSION_DENIED) {
          setStatus("permission-denied");
        } else if (error.code === error.TIMEOUT) {
          setStatus("timeout");
        } else {
          setStatus("unavailable");
        }
      },
      GEOLOCATION_OPTIONS,
    );
  }, []);

  return { status, coordinates, requestLocation };
}
