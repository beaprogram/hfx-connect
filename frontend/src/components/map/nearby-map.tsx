"use client";

import "leaflet/dist/leaflet.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";

import L from "leaflet";
import type { StaticImageData } from "next/image";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import { useEffect, useRef } from "react";
import { MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from "react-leaflet";
import MarkerClusterGroup from "react-leaflet-cluster";
import Link from "next/link";
import { CostBadge, HoursStatusBadge } from "@/components/resources/status-badges";
import { formatCityProvince } from "@/lib/formatting/location";
import { formatDistanceAway } from "@/lib/formatting/distance";
import { HALIFAX_DEFAULT_ZOOM } from "@/lib/constants/map";
import type { MapCentre } from "@/lib/map/map-search-context";
import type { NearbyResourceSummaryResponse } from "@/lib/validation/schemas";

/**
 * Next.js's static-image-import loader returns a `StaticImageData` object
 * (`{ src, height, width }`), not a raw URL string — Leaflet's own default
 * icon setup expects a plain string. This resolves either shape to a URL,
 * so the fix works the same whether the bundler returns a string or an
 * object. See ADR-013's "Leaflet Default-Marker Assets Under a Bundler".
 */
function resolveAssetUrl(mod: string | StaticImageData): string {
  return typeof mod === "string" ? mod : mod.src;
}

// Leaflet's default icon otherwise resolves its image URLs relative to the
// page, not the bundled asset — broken under any bundler, not specific to
// Next.js. Replacing the default icon once, at module load, is the
// standard fix (see ADR-013).
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: resolveAssetUrl(markerIcon2x),
  iconUrl: resolveAssetUrl(markerIcon),
  shadowUrl: resolveAssetUrl(markerShadow),
});

const TILE_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors';

interface NearbyMapProps {
  centre: MapCentre;
  results: NearbyResourceSummaryResponse[];
  selectedResourceId: string | null;
  onSelectResource: (id: string) => void;
  onPendingCentreChange: (centre: MapCentre) => void;
}

export function NearbyMap({ centre, results, selectedResourceId, onSelectResource, onPendingCentreChange }: NearbyMapProps) {
  return (
    <MapContainer
      center={[centre.latitude, centre.longitude]}
      zoom={HALIFAX_DEFAULT_ZOOM}
      style={{ height: "100%", width: "100%" }}
      className="rounded-lg"
      aria-label="Map of nearby resources"
    >
      <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
      <RecentreOnCentreChange centre={centre} />
      <MoveTracker onPendingCentreChange={onPendingCentreChange} />
      <MarkerClusterGroup chunkedLoading>
        {results.map((resource) => (
          <ResourceMarker
            key={resource.id}
            resource={resource}
            selected={resource.id === selectedResourceId}
            onSelect={onSelectResource}
          />
        ))}
      </MarkerClusterGroup>
    </MapContainer>
  );
}

/** Recentres the map when `centre` changes for a reason other than the initial mount (e.g. "Use my location" or "Search this area") — `MapContainer`'s own `center` prop already handles the first render. */
function RecentreOnCentreChange({ centre }: { centre: MapCentre }) {
  const map = useMap();
  const isFirstRender = useRef(true);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    map.setView([centre.latitude, centre.longitude], map.getZoom());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [centre.latitude, centre.longitude]);

  return null;
}

/**
 * Records the map's current centre after the user stops panning/zooming
 * (`moveend` fires once, after movement settles — never mid-drag), so
 * "Search this area" can appear without flooding the API on every frame of
 * a drag. See ADR-013's "Search This Area".
 */
function MoveTracker({ onPendingCentreChange }: { onPendingCentreChange: (centre: MapCentre) => void }) {
  const map = useMapEvents({
    moveend: () => {
      const c = map.getCenter();
      onPendingCentreChange({ latitude: c.lat, longitude: c.lng });
    },
  });
  return null;
}

function ResourceMarker({
  resource,
  selected,
  onSelect,
}: {
  resource: NearbyResourceSummaryResponse;
  selected: boolean;
  onSelect: (id: string) => void;
}) {
  const map = useMap();
  const markerRef = useRef<L.Marker>(null);
  const location = formatCityProvince(resource.city, resource.province);

  useEffect(() => {
    if (!selected) return;
    const marker = markerRef.current;
    if (!marker) return;
    marker.openPopup();
    const latlng = marker.getLatLng();
    if (!map.getBounds().contains(latlng)) {
      map.panTo(latlng);
    }
  }, [selected, map]);

  return (
    <Marker
      ref={markerRef}
      position={[resource.latitude, resource.longitude]}
      eventHandlers={{ click: () => onSelect(resource.id) }}
    >
      <Popup>
        <div className="flex min-w-[12rem] flex-col gap-1.5">
          <p className="font-semibold text-slate-900">{resource.name}</p>
          <p className="text-xs text-slate-600">
            {resource.category.name}
            {location ? ` · ${location}` : ""}
          </p>
          <p className="text-xs text-slate-600">{formatDistanceAway(resource.distanceMeters)}</p>
          <div className="flex flex-wrap gap-1">
            <CostBadge costType={resource.costType} />
            <HoursStatusBadge status={resource.hoursStatus} />
          </div>
          <Link
            href={`/resources/${resource.slug}`}
            className="mt-1 text-sm font-medium text-blue-700 underline underline-offset-2"
          >
            View details
          </Link>
        </div>
      </Popup>
    </Marker>
  );
}
