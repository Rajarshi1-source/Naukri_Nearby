"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function SaveButton({
  jobId,
  initiallySaved = false,
}: {
  jobId: number;
  initiallySaved?: boolean;
}) {
  const { isAuthenticated, role } = useAuth();
  const router = useRouter();
  const [saved, setSaved] = useState(initiallySaved);
  const [busy, setBusy] = useState(false);

  async function toggle() {
    if (!isAuthenticated || role !== "CANDIDATE") {
      router.push(`/login?next=/jobs`);
      return;
    }
    setBusy(true);
    try {
      if (saved) {
        await api<void>(`/api/candidate/saved-jobs/${jobId}`, { method: "DELETE" });
        setSaved(false);
      } else {
        await api<void>(`/api/candidate/saved-jobs/${jobId}`, { method: "POST" });
        setSaved(true);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) router.push("/login?next=/jobs");
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      onClick={toggle}
      disabled={busy}
      aria-pressed={saved}
      title={saved ? "Remove from saved" : "Save job"}
      className={`rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60 ${
        saved
          ? "border-brand bg-brand-light text-brand-dark"
          : "border-slate-300 text-slate-600 hover:bg-slate-50"
      }`}
    >
      {saved ? "★ Saved" : "☆ Save"}
    </button>
  );
}
