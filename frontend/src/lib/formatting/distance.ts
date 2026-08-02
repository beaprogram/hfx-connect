/**
 * Formats a straight-line distance in metres for display — never route
 * distance or travel time (Milestone 7A's `distanceMeters` is geographic,
 * "as the crow flies" — see ADR-012). Metres under 1 km, kilometres (one
 * decimal place) above, always labelled "away" so it reads as approximate
 * rather than a precise measurement.
 */
export function formatDistanceAway(distanceMeters: number): string {
  if (distanceMeters < 1000) {
    return `${Math.round(distanceMeters)} m away`;
  }
  return `${(distanceMeters / 1000).toFixed(1)} km away`;
}
