"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { CheckCircle2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { candidateService } from "@/services/candidateService";

type State = "idle" | "applied" | "already";

export default function ApplyButton({
  jobId,
  slug,
}: {
  jobId: number;
  slug: string;
}) {
  const { isAuthenticated, role } = useAuth();
  const router = useRouter();
  const [state, setState] = useState<State>("idle");
  const [busy, setBusy] = useState(false);

  async function apply() {
    if (!isAuthenticated || role !== "CANDIDATE") {
      router.push(`/login?next=/jobs/${slug}`);
      return;
    }
    setBusy(true);
    try {
      await candidateService.apply(jobId);
      setState("applied");
      toast.success("Application submitted!");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setState("already");
      } else if (err instanceof ApiError && err.status === 401) {
        router.push(`/login?next=/jobs/${slug}`);
      } else {
        toast.error(err instanceof ApiError ? err.message : "Could not apply");
      }
    } finally {
      setBusy(false);
    }
  }

  if (state === "applied" || state === "already") {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-brand-light px-4 py-2 text-sm font-medium text-brand-dark">
        <CheckCircle2 className="size-4" />
        {state === "applied"
          ? "Application submitted!"
          : "You have already applied to this job."}
      </div>
    );
  }

  return (
    <Button onClick={apply} disabled={busy} size="lg">
      {busy ? "Applying…" : "Apply now"}
    </Button>
  );
}
