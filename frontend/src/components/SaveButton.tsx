"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Star } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { candidateService } from "@/services/candidateService";
import { cn } from "@/lib/utils";

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
        await candidateService.unsaveJob(jobId);
        setSaved(false);
      } else {
        await candidateService.saveJob(jobId);
        setSaved(true);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401)
        router.push("/login?next=/jobs");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Button
      type="button"
      variant={saved ? "secondary" : "outline"}
      size="sm"
      onClick={toggle}
      disabled={busy}
      aria-pressed={saved}
      title={saved ? "Remove from saved" : "Save job"}
      className={cn(saved && "border-brand bg-brand-light text-brand-dark")}
    >
      <Star className={cn("size-4", saved && "fill-current")} />
      {saved ? "Saved" : "Save"}
    </Button>
  );
}
