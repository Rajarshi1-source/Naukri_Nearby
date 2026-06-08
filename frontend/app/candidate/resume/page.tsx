"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { CandidateProfile, ResumeUploadResponse } from "@/lib/types";

type Phase = "idle" | "uploading" | "parsing" | "done" | "failed";

export default function ResumeUploadPage() {
  const { isAuthenticated, role, loading } = useAuth();
  const router = useRouter();
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!loading && (!isAuthenticated || role !== "CANDIDATE")) {
      router.push("/login?next=/candidate/resume");
    }
  }, [loading, isAuthenticated, role, router]);

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  async function onFile(file: File) {
    setError(null);
    setProfile(null);
    setPhase("uploading");
    try {
      const form = new FormData();
      form.append("file", file);
      const res = await api<ResumeUploadResponse>("/api/resumes/upload", {
        method: "POST",
        body: form,
      });
      setPhase("parsing");
      startPolling(res.resumeId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Upload failed");
      setPhase("failed");
    }
  }

  function startPolling(resumeId: string) {
    if (pollRef.current) clearInterval(pollRef.current);
    let attempts = 0;
    pollRef.current = setInterval(async () => {
      attempts += 1;
      try {
        const status = await api<{ resumeId: string; status: string }>(
          `/api/resumes/${resumeId}/parse-status`,
        );
        if (status.status === "COMPLETED") {
          stopPolling();
          await loadProfile();
          setPhase("done");
        } else if (status.status === "FAILED") {
          stopPolling();
          setError("We couldn't read that resume. You can edit your profile manually.");
          setPhase("failed");
        } else if (attempts > 30) {
          stopPolling();
          setError("Parsing is taking longer than expected. Check back shortly.");
          setPhase("failed");
        }
      } catch {
        stopPolling();
        setError("Lost connection while parsing.");
        setPhase("failed");
      }
    }, 2000);
  }

  function stopPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  async function loadProfile() {
    try {
      setProfile(await api<CandidateProfile>("/api/candidate/profile"));
    } catch {
      setProfile(null);
    }
  }

  if (loading || !isAuthenticated) return null;

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-bold text-slate-900">Upload your resume</h1>
      <p className="mt-1 text-sm text-slate-500">
        PDF or a photo (JPG/PNG). Our AI extracts your skills and experience — handles Hindi and
        English.
      </p>

      <label
        className={`mt-6 flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-10 text-center ${
          phase === "uploading" || phase === "parsing"
            ? "border-slate-200 bg-slate-50"
            : "border-slate-300 bg-white hover:border-brand"
        }`}
      >
        <input
          type="file"
          accept=".pdf,image/png,image/jpeg"
          className="hidden"
          disabled={phase === "uploading" || phase === "parsing"}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void onFile(file);
          }}
        />
        <span className="text-slate-700">
          {phase === "uploading"
            ? "Uploading…"
            : phase === "parsing"
              ? "Reading your resume…"
              : "Tap to choose a file or take a photo"}
        </span>
        <span className="mt-1 text-xs text-slate-400">Max ~10 MB</span>
      </label>

      {error && (
        <div className="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      {phase === "done" && profile && (
        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-green-700">Profile updated</h2>
          <dl className="mt-3 space-y-2 text-sm">
            <Row label="Name" value={profile.name} />
            <Row label="City" value={[profile.city, profile.state].filter(Boolean).join(", ")} />
            <Row
              label="Experience"
              value={
                profile.totalExperienceMonths != null
                  ? `${Math.round((profile.totalExperienceMonths / 12) * 10) / 10} years`
                  : null
              }
            />
          </dl>
          {profile.skills.length > 0 && (
            <div className="mt-3">
              <p className="text-sm font-medium text-slate-700">Skills</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {profile.skills.map((s) => (
                  <span
                    key={s}
                    className="rounded-md bg-brand-light px-2 py-1 text-xs text-brand-dark"
                  >
                    {s}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex gap-2">
      <dt className="w-28 shrink-0 text-slate-500">{label}</dt>
      <dd className="text-slate-800">{value || "—"}</dd>
    </div>
  );
}
