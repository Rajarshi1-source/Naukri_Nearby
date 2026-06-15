"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/lib/auth";
import { useResumeUpload } from "@/hooks/useResumeUpload";
import { cn } from "@/lib/utils";

export default function ResumeUploadPage() {
  const { isAuthenticated, role, loading } = useAuth();
  const router = useRouter();
  const { state, upload } = useResumeUpload();

  useEffect(() => {
    if (!loading && (!isAuthenticated || role !== "CANDIDATE")) {
      router.push("/login?next=/candidate/resume");
    }
  }, [loading, isAuthenticated, role, router]);

  if (loading || !isAuthenticated) return null;

  const busy = state.phase === "uploading" || state.phase === "parsing";
  const profile = state.phase === "done" ? state.profile : null;
  const errorMessage = state.phase === "failed" ? state.message : null;

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-bold text-foreground">Upload your resume</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        PDF or a photo (JPG/PNG). Our AI extracts your skills and experience —
        handles Hindi and English.
      </p>

      <label
        className={cn(
          "mt-6 flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-10 text-center transition-colors",
          busy
            ? "border-border bg-muted"
            : "border-input bg-card hover:border-brand",
        )}
      >
        <input
          type="file"
          accept=".pdf,image/png,image/jpeg"
          className="hidden"
          disabled={busy}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void upload(file);
          }}
        />
        <span className="text-foreground/80">
          {state.phase === "uploading"
            ? "Uploading…"
            : state.phase === "parsing"
              ? "Reading your resume…"
              : "Tap to choose a file or take a photo"}
        </span>
        <span className="mt-1 text-xs text-muted-foreground">Max ~10 MB</span>
      </label>

      {errorMessage && (
        <div className="mt-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {errorMessage}
        </div>
      )}

      {profile && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle className="text-brand">Profile updated</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-2 text-sm">
              <Row label="Name" value={profile.name} />
              <Row
                label="City"
                value={[profile.city, profile.state]
                  .filter(Boolean)
                  .join(", ")}
              />
              <Row
                label="Experience"
                value={
                  profile.totalExperienceMonths != null
                    ? `${
                        Math.round(
                          (profile.totalExperienceMonths / 12) * 10,
                        ) / 10
                      } years`
                    : null
                }
              />
            </dl>
            {profile.skills.length > 0 && (
              <div className="mt-3">
                <p className="text-sm font-medium text-foreground/80">Skills</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {profile.skills.map((s) => (
                    <Badge
                      key={s}
                      className="bg-brand-light text-brand-dark"
                    >
                      {s}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex gap-2">
      <dt className="w-28 shrink-0 text-muted-foreground">{label}</dt>
      <dd className="text-foreground/90">{value || "—"}</dd>
    </div>
  );
}
