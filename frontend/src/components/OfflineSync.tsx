"use client";

import { useEffect, useRef } from "react";
import { toast } from "sonner";

import { fmt } from "@/i18n";
import { useT } from "@/i18n/I18nProvider";
import { ApiError, tokens } from "@/lib/api";
import {
  isNetworkError,
  isOfflineQueueSupported,
  queuedApplies,
  removeQueued,
} from "@/lib/offlineQueue";
import { candidateService } from "@/services/candidateService";

/**
 * Replays applications queued while offline (ADR 0009) on start and whenever the browser comes back
 * online. A 409 means the application already exists, which is the goal, so it is removed too.
 */
export default function OfflineSync() {
  const t = useT();
  const running = useRef(false);

  useEffect(() => {
    if (!isOfflineQueueSupported()) return;

    async function flush() {
      if (running.current || !navigator.onLine || !tokens.access) return;
      running.current = true;
      let sent = 0;
      try {
        for (const item of await queuedApplies()) {
          try {
            await candidateService.apply(item.jobId, item.coverNote);
            await removeQueued(item.jobId);
            sent++;
          } catch (err) {
            if (err instanceof ApiError && (err.status === 409 || err.status === 404 || err.status === 400)) {
              await removeQueued(item.jobId); // already applied, or the job is gone — nothing to retry
            } else if (isNetworkError(err)) {
              break; // still offline; try again on the next online event
            }
          }
        }
      } finally {
        running.current = false;
      }
      if (sent > 0) toast.success(fmt(t.apply.synced, { n: sent }));
    }

    void flush();
    window.addEventListener("online", flush);
    return () => window.removeEventListener("online", flush);
  }, [t]);

  return null;
}
