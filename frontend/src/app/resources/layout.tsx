import { Suspense, type ReactNode } from "react";
import { MapSearchProvider } from "@/lib/map/map-search-context";

/**
 * Wraps both `/resources` (the list/map explorer) and `/resources/[slug]`
 * (resource detail) so map/geolocation state — search centre, radius,
 * geolocation status, selected resource — survives navigating to a
 * resource's detail page and back, and survives filter-driven navigations
 * to a new `/resources?...` URL. A layout persists across sibling-route
 * navigations within it; a page does not. See ADR-013's "State
 * Architecture" section.
 *
 * The `Suspense` boundary is required because `MapSearchProvider` reads
 * `useSearchParams()` (to mirror `view`/`radiusKm` — see
 * map-search-context.tsx); Next.js requires a Suspense boundary around any
 * Client Component using that hook. `/resources` is already fully dynamic
 * (`page.tsx` reads `searchParams` itself), so this never produces a
 * visible fallback in practice.
 */
export default function ResourcesLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense>
      <MapSearchProvider>{children}</MapSearchProvider>
    </Suspense>
  );
}
