"use client";

import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { Flag } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useT } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { trustService } from "@/services/trustService";
import { REPORT_REASONS, type ReportReason } from "@/types";

/** Lets any signed-in user report a job (ADR 0007). Enough reports send it back to review. */
export default function ReportJobButton({ jobId, slug }: { jobId: number; slug: string }) {
  const t = useT();
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>("ASKS_FOR_PAYMENT");
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const detailsId = useId();

  if (done) {
    return <p className="text-sm text-muted-foreground">{t.report.thanks}</p>;
  }

  if (!open) {
    return (
      <Button
        variant="ghost"
        size="sm"
        className="text-muted-foreground"
        onClick={() => {
          if (!isAuthenticated) {
            toast.info(t.report.loginFirst);
            router.push(`/login?next=/jobs/${slug}`);
            return;
          }
          setOpen(true);
        }}
      >
        <Flag className="size-4" aria-hidden />
        {t.report.button}
      </Button>
    );
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await trustService.report(jobId, reason, details.trim() || undefined);
      setDone(true);
      toast.success(t.report.thanks);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setDone(true);
        toast.info(t.report.already);
      } else {
        toast.error(err instanceof ApiError ? err.message : t.common.error);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="w-full space-y-3 rounded-lg border p-4" aria-label={t.report.title}>
      <h2 className="font-semibold">{t.report.title}</h2>
      <div className="space-y-1.5">
        <Label>{t.report.reason}</Label>
        <Select value={reason} onValueChange={(v) => setReason(v as ReportReason)}>
          <SelectTrigger className="w-full" aria-label={t.report.reason}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {REPORT_REASONS.map((r) => (
              <SelectItem key={r} value={r}>
                {t.report[r]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor={detailsId}>{t.report.details}</Label>
        <textarea
          id={detailsId}
          value={details}
          maxLength={1000}
          onChange={(e) => setDetails(e.target.value)}
          rows={3}
          className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
        />
      </div>
      <div className="flex gap-2">
        <Button type="submit" variant="destructive" disabled={busy}>
          {t.report.submit}
        </Button>
        <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
          {t.report.cancel}
        </Button>
      </div>
    </form>
  );
}
