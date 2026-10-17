"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { CheckCircle2, CloudOff } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useT } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { enqueueApply, isNetworkError, isOfflineQueueSupported } from "@/lib/offlineQueue";
import { candidateService } from "@/services/candidateService";

type State = "idle" | "applied" | "already" | "queued";

/**
 * One-tap apply. Offline (or when the request fails for lack of network), the application is queued in
 * IndexedDB and sent by OfflineSync on reconnect (ADR 0009). The server is idempotent, so a replay of a
 * request that did get through is harmless.
 */
export default function ApplyButton({
  jobId,
  slug,
}: {
  jobId: number;
  slug: string;
}) {
  const t = useT();
  const { isAuthenticated, role } = useAuth();
  const router = useRouter();
  const [state, setState] = useState<State>("idle");
  const [busy, setBusy] = useState(false);

  async function queue() {
    await enqueueApply({ jobId, queuedAt: Date.now() });
    setState("queued");
    toast.info(t.apply.queued);
  }

  async function apply() {
    if (!isAuthenticated || role !== "CANDIDATE") {
      router.push(`/login?next=/jobs/${slug}`);
      return;
    }
    setBusy(true);
    try {
      if (!navigator.onLine && isOfflineQueueSupported()) {
        await queue();
        return;
      }
      await candidateService.apply(jobId);
      setState("applied");
      toast.success(t.apply.applied);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setState("already");
      } else if (err instanceof ApiError && err.status === 401) {
        router.push(`/login?next=/jobs/${slug}`);
      } else if (isNetworkError(err) && isOfflineQueueSupported()) {
        await queue();
      } else {
        toast.error(err instanceof ApiError ? err.message : t.apply.failed);
      }
    } finally {
      setBusy(false);
    }
  }

  if (state === "queued") {
    return (
      <div role="status" className="flex items-center gap-2 rounded-lg bg-warning/15 px-4 py-2 text-sm font-medium">
        <CloudOff className="size-4" aria-hidden />
        {t.apply.queuedBadge}
      </div>
    );
  }

  if (state === "applied" || state === "already") {
    return (
      <div role="status" className="flex items-center gap-2 rounded-lg bg-brand-light px-4 py-2 text-sm font-medium text-brand-dark">
        <CheckCircle2 className="size-4" aria-hidden />
        {state === "applied" ? t.apply.applied : t.apply.already}
      </div>
    );
  }

  return (
    <Button onClick={apply} disabled={busy} size="lg">
      {busy ? t.apply.applying : t.apply.apply}
    </Button>
  );
}
