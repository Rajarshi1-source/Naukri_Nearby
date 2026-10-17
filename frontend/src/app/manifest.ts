import type { MetadataRoute } from "next";

/** Installable PWA manifest (ADR 0009). Served at /manifest.webmanifest. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "NaukriNearby — Hyperlocal jobs for Bharat",
    short_name: "NaukriNearby",
    description: "Find jobs near you, in your language. Works on slow networks.",
    start_url: "/jobs",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#fcfcfc",
    theme_color: "#2f7d6d",
    lang: "en-IN",
    categories: ["business", "productivity"],
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
