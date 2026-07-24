/**
 * Joins whichever of city/province a resource actually has, for the compact
 * one-line summary used on cards. Never renders a bare comma or dash when a
 * field is missing.
 */
export function formatCityProvince(city?: string | null, province?: string | null): string | null {
  const parts = [city, province].filter((part): part is string => Boolean(part && part.trim().length > 0));
  return parts.length > 0 ? parts.join(", ") : null;
}
