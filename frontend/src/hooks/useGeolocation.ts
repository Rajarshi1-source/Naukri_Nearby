"use client";

import { useEffect, useState } from "react";

/** Resolves the browser's location once on mount; `located` stays false if denied/unavailable. */
export function useGeolocation() {
  const [coords, setCoords] = useState<[number, number] | null>(null);
  const [located, setLocated] = useState(false);

  useEffect(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords([pos.coords.latitude, pos.coords.longitude]);
        setLocated(true);
      },
      () => setLocated(false),
      { timeout: 8000 },
    );
  }, []);

  return { coords, located };
}
