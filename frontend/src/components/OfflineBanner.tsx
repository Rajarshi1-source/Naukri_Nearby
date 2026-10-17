"use client";

import { useSyncExternalStore } from "react";
import { WifiOff } from "lucide-react";

import { useT } from "@/i18n/I18nProvider";

function subscribe(onChange: () => void) {
  window.addEventListener("online", onChange);
  window.addEventListener("offline", onChange);
  return () => {
    window.removeEventListener("online", onChange);
    window.removeEventListener("offline", onChange);
  };
}

/** Tells the user they are looking at saved data while offline (ADR 0009). */
export default function OfflineBanner() {
  const t = useT();
  const online = useSyncExternalStore(
    subscribe,
    () => navigator.onLine,
    () => true,
  );
  if (online) return null;
  return (
    <div
      role="status"
      className="sticky top-14 z-[999] flex items-center justify-center gap-2 bg-warning/15 px-4 py-2 text-center text-sm text-foreground"
    >
      <WifiOff className="size-4 shrink-0" aria-hidden />
      {t.offline.banner}
    </div>
  );
}
