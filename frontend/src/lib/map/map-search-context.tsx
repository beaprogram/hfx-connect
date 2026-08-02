"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import {
  DEFAULT_RADIUS_KM,
  HALIFAX_DEFAULT_CENTER,
  isRadiusKm,
  SEARCH_THIS_AREA_THRESHOLD_METERS,
  type RadiusKm,
} from "@/lib/constants/map";

export type ExplorerView = "list" | "map";

export type MapCentreSource = "default" | "geolocation" | "manual";

export interface MapCentre {
  latitude: number;
  longitude: number;
}

interface MapSearchState {
  view: ExplorerView;
  radiusKm: RadiusKm;
  centre: MapCentre | null;
  centreSource: MapCentreSource;
  pendingCentre: MapCentre | null;
  selectedResourceId: string | null;
  nearbyPage: number;
}

interface MapSearchContextValue extends MapSearchState {
  setView: (view: ExplorerView) => void;
  setRadiusKm: (radiusKm: RadiusKm) => void;
  applyGeolocatedCentre: (centre: MapCentre) => void;
  setManualCentre: (centre: MapCentre) => void;
  setPendingCentre: (centre: MapCentre | null) => void;
  searchThisArea: () => void;
  hasPendingSearch: boolean;
  setSelectedResourceId: (id: string | null) => void;
  setNearbyPage: (page: number) => void;
  resetNearbyPage: () => void;
}

const MapSearchContext = createContext<MapSearchContextValue | null>(null);

/** Haversine distance in metres — used only to decide whether a map move is meaningful enough to surface "Search this area", not for any authoritative search result. */
function distanceMeters(a: MapCentre, b: MapCentre): number {
  const R = 6_371_000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

/**
 * Session-scoped map/nearby-search state, provided once at
 * `app/resources/layout.tsx` (above both the list route and the
 * resource-detail route) so it survives filter-driven navigations to
 * `/resources?...` and round trips to `/resources/[slug]` and back — see
 * ADR-013's "State Architecture" section. Centre/pending-centre/selection
 * are deliberately never written to the URL, localStorage, or
 * sessionStorage (ADR-013's "Location Privacy"); `view`/`radiusKm` are
 * best-effort mirrored to the URL (read once on mount, written on change)
 * purely for shareable/bookmarkable presentation, not as the state's
 * source of truth.
 */
export function MapSearchProvider({ children }: { children: ReactNode }) {
  const [view, setViewState] = useState<ExplorerView>("list");
  const [radiusKm, setRadiusKmState] = useState<RadiusKm>(DEFAULT_RADIUS_KM);
  const [centre, setCentre] = useState<MapCentre | null>(null);
  const [centreSource, setCentreSource] = useState<MapCentreSource>("default");
  const [pendingCentre, setPendingCentre] = useState<MapCentre | null>(null);
  const [selectedResourceId, setSelectedResourceId] = useState<string | null>(null);
  const [nearbyPage, setNearbyPage] = useState(0);
  const restoredFromUrl = useRef(false);

  // Best-effort initial restoration from the URL (e.g. a bookmarked/shared
  // `?view=map&radiusKm=10` link). Deliberately reads `window.location`
  // inside an effect (after mount) rather than during render: this
  // provider is server-rendered, and reading the real URL during render
  // would make the client's first paint diverge from the server's,
  // producing a hydration mismatch. Setting state here is the intentional
  // fix-up-after-mount half of that trade-off, not a mirrored-state
  // anti-pattern — see ADR-013's "URL Restoration Timing".
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (restoredFromUrl.current) return;
    restoredFromUrl.current = true;
    const params = new URLSearchParams(window.location.search);
    const rawView = params.get("view");
    if (rawView === "map" || rawView === "list") {
      setViewState(rawView);
    }
    const rawRadius = params.get("radiusKm");
    const parsedRadius = rawRadius !== null ? Number.parseFloat(rawRadius) : NaN;
    if (Number.isFinite(parsedRadius) && isRadiusKm(parsedRadius)) {
      setRadiusKmState(parsedRadius);
    }
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  // Mirrors view/radiusKm to the URL (shallow — no server round trip) so the
  // current presentation is shareable/restorable. Never includes centre.
  // Re-runs on `pathname` too: a filter change navigates via `router.push`
  // to a fresh `/resources?...` URL (existing Milestone 6A/6B behavior,
  // unaware of view/radiusKm), which would otherwise silently drop them
  // from the address bar without this state itself having changed. Scoped
  // to exactly the `/resources` list/map route — this provider also wraps
  // `/resources/[slug]` (see app/resources/layout.tsx), and a detail page's
  // own URL must never pick up `?view=map&radiusKm=…` clutter.
  const pathname = usePathname();
  const searchParamsString = useSearchParams().toString();
  useEffect(() => {
    if (!restoredFromUrl.current) return;
    if (pathname !== "/resources") return;
    const url = new URL(window.location.href);
    if (view === "map") {
      url.searchParams.set("view", "map");
      url.searchParams.set("radiusKm", String(radiusKm));
    } else {
      url.searchParams.delete("view");
      url.searchParams.delete("radiusKm");
    }
    const nextSearch = url.search;
    if (nextSearch !== window.location.search) {
      window.history.replaceState(window.history.state, "", `${url.pathname}${nextSearch}`);
    }
  }, [view, radiusKm, pathname, searchParamsString]);

  const setView = useCallback((next: ExplorerView) => {
    setViewState(next);
    if (next === "map") {
      setCentre((current) => {
        if (current) return current;
        setCentreSource("default");
        return HALIFAX_DEFAULT_CENTER;
      });
    }
  }, []);

  const setRadiusKm = useCallback((next: RadiusKm) => {
    setRadiusKmState(next);
    setNearbyPage(0);
  }, []);

  const applyGeolocatedCentre = useCallback((next: MapCentre) => {
    setCentre(next);
    setCentreSource("geolocation");
    setPendingCentre(null);
    setNearbyPage(0);
  }, []);

  const setManualCentre = useCallback((next: MapCentre) => {
    setCentre(next);
    setCentreSource("manual");
    setPendingCentre(null);
  }, []);

  const searchThisArea = useCallback(() => {
    setPendingCentre((pending) => {
      if (!pending) return pending;
      setCentre(pending);
      setCentreSource("manual");
      setNearbyPage(0);
      return null;
    });
  }, []);

  const resetNearbyPage = useCallback(() => setNearbyPage(0), []);

  const hasPendingSearch = useMemo(() => {
    if (!pendingCentre || !centre) return false;
    return distanceMeters(centre, pendingCentre) >= SEARCH_THIS_AREA_THRESHOLD_METERS;
  }, [centre, pendingCentre]);

  const value: MapSearchContextValue = {
    view,
    radiusKm,
    centre,
    centreSource,
    pendingCentre,
    selectedResourceId,
    nearbyPage,
    setView,
    setRadiusKm,
    applyGeolocatedCentre,
    setManualCentre,
    setPendingCentre,
    searchThisArea,
    hasPendingSearch,
    setSelectedResourceId,
    setNearbyPage,
    resetNearbyPage,
  };

  return <MapSearchContext.Provider value={value}>{children}</MapSearchContext.Provider>;
}

export function useMapSearch(): MapSearchContextValue {
  const context = useContext(MapSearchContext);
  if (!context) {
    throw new Error("useMapSearch must be used within a MapSearchProvider (see app/resources/layout.tsx).");
  }
  return context;
}
