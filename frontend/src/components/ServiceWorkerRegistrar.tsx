"use client";

import { useEffect } from "react";

import { API_URL } from "@/lib/api";

/**
 * Registers the hand-written service worker (public/sw.js) in production builds only, so `next dev`
 * never serves stale cached code. The API origin is passed in the script URL because it is a
 * build-time value the worker cannot read itself.
 */
export default function ServiceWorkerRegistrar() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    const url = `/sw.js?api=${encodeURIComponent(API_URL)}`;
    navigator.serviceWorker.register(url, { scope: "/" }).catch(() => {
      // Offline support is an enhancement; the app works without it.
    });
  }, []);
  return null;
}
