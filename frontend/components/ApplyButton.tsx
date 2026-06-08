"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

type State = "idle" | "applied" | "already";

export default function ApplyButton({ jobId, slug }: { jobId: number; slug: string }) {
  const { isAuthenticated, role } = useAuth();
  const router = useRouter();
  const [state, setState] = useState<State>("idle");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function apply() {
    if (!isAuthenticated || role !== "CANDIDATE") {
      router.push(`/login?next=/jobs/${slug}`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api<unknown>("/api/applications", {
        method: "POST",
        body: { jobId },
      });
      setState("applied");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setState("already");
      } else if (err instanceof ApiError && err.status === 401) {
        router.push(`/login?next=/jobs/${slug}`);
      } else {
        setError(err instanceof ApiError ? err.message : "Could not apply");
      }
    } finally {
      setBusy(false);
    }
  }

  if (state === "applied" || state === "already") {
    return (
      <div className="rounded-lg bg-green-50 px-4 py-2 text-sm font-medium text-green-700">
        {state === "applied" ? "Application submitted!" : "You have already applied to this job."}
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <button
        onClick={apply}
        disabled={busy}
        className="rounded-lg bg-brand px-6 py-2.5 font-semibold text-white hover:bg-brand-dark disabled:opacity-60"
      >
        {busy ? "Applying…" : "Apply now"}
      </button>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}
