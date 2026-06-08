"use client";

import L from "leaflet";
import { Circle, MapContainer, Marker, Popup, TileLayer } from "react-leaflet";

import "leaflet/dist/leaflet.css";

// Leaflet's default marker icons reference local asset paths that bundlers can't resolve; point them
// at the CDN copies so the marker renders correctly.
const markerIcon = L.icon({
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

interface Props {
  center: [number, number];
  radiusKm: number;
}

/**
 * Shows the searcher's location and the active search radius. The backend SearchResponse does not
 * return per-job coordinates, so we render the radius rather than fake per-job markers.
 */
export default function JobMap({ center, radiusKm }: Props) {
  return (
    <MapContainer center={center} zoom={12} scrollWheelZoom className="h-full w-full">
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Marker position={center} icon={markerIcon}>
        <Popup>You are here</Popup>
      </Marker>
      <Circle
        center={center}
        radius={radiusKm * 1000}
        pathOptions={{ color: "#0d7a5f", fillColor: "#0d7a5f", fillOpacity: 0.08 }}
      />
    </MapContainer>
  );
}
