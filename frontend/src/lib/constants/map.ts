/**
 * A reasonable central-Halifax coordinate (Grand Parade / downtown core),
 * used only as the map's starting viewport before a user has granted
 * geolocation or manually moved the map — never presented as the user's own
 * location. See ADR-013.
 */
export const HALIFAX_DEFAULT_CENTER = { latitude: 44.6488, longitude: -63.5752 } as const;

export const HALIFAX_DEFAULT_ZOOM = 13;

/**
 * The real backend-supported radius range (Milestone 7A — see ADR-012):
 * `radiusKm` must be greater than 0 and at most `MAX_RADIUS_KM`. This list is
 * the single source of truth for what the radius selector is allowed to send.
 */
export const RADIUS_OPTIONS_KM = [1, 2, 5, 10, 25, 50] as const;

export type RadiusKm = (typeof RADIUS_OPTIONS_KM)[number];

export const DEFAULT_RADIUS_KM: RadiusKm = 5;

export const MAX_RADIUS_KM = 50;

export function isRadiusKm(value: number): value is RadiusKm {
  return RADIUS_OPTIONS_KM.includes(value as RadiusKm);
}

/**
 * `getCurrentPosition` options (see ADR-013's "Browser Geolocation Flow"):
 * `enableHighAccuracy: false` is deliberate — this product only needs a
 * neighbourhood-scale search origin, not GPS-grade precision, and high
 * accuracy costs more battery/time for no user-visible benefit here.
 * `timeout: 10_000`ms gives a slow GPS fix a fair chance without leaving the
 * user waiting indefinitely. `maximumAge: 300_000`ms (5 minutes) allows a
 * recently cached position to be reused instead of forcing a fresh fix on
 * every request.
 */
export const GEOLOCATION_OPTIONS: PositionOptions = {
  enableHighAccuracy: false,
  timeout: 10_000,
  maximumAge: 300_000,
};

/**
 * Minimum distance (metres, straight-line) the map centre must move before
 * "Search this area" appears — filters out sub-pixel/rounding jitter from a
 * programmatic recentre (e.g. "Use my location") registering as a spurious
 * pending move.
 */
export const SEARCH_THIS_AREA_THRESHOLD_METERS = 50;

/** Fixed page size for nearby-search results (map/list panel), mirroring RESOURCE_LIST_PAGE_SIZE. */
export const NEARBY_PAGE_SIZE = 12;
