"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import TrustBadge from "@/components/TrustBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { fmt } from "@/i18n";
import { useT } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatSalary, titleCase } from "@/lib/format";
import { riskReason } from "@/lib/risk";
import { trustService } from "@/services/trustService";
import type { ModerationItem } from "@/types";

/**
 * Admin review queue (ADR 0007). The API enforces ROLE_ADMIN; this page only hides itself from
 * other roles. Approving re-indexes the job through the outbox and releases its candidate alerts.
 */
export default function ModerationPage() {
  const { isAuthenticated, role, loading } = useAuth();
  const t = useT();
  const router = useRouter();
  const isAdmin = isAuthenticated && role === "ADMIN";

  useEffect(() => {
    if (!loading && !isAuthenticated) router.push("/login?next=/admin/moderation");
  }, [loading, isAuthenticated, router]);

  const queue = useQuery({ queryKey: ["admin", "queue"], queryFn: trustService.queue, enabled: isAdmin });

  if (loading || !isAuthenticated) return null;
  if (!isAdmin) {
    return <p className="mx-auto max-w-3xl text-muted-foreground">{t.admin.forbidden}</p>;
  }

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <div>
        <h1 className="text-2xl font-bold">{t.admin.title}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t.admin.intro}</p>
      </div>
      {queue.isLoading && <p className="text-muted-foreground">{t.common.loading}</p>}
      {queue.data?.length === 0 && (
        <p className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">{t.admin.empty}</p>
      )}
      <ul className="space-y-4">
        {queue.data?.map((item) => (
          <QueueRow key={item.job.id} item={item} />
        ))}
      </ul>
    </div>
  );
}

function QueueRow({ item }: { item: ModerationItem }) {
  const t = useT();
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const { job } = item;
  const decide = useMutation({
    mutationFn: (decision: "APPROVED" | "REJECTED") => trustService.decide(job.id, decision, note.trim() || undefined),
    onSuccess: async () => {
      toast.success(t.admin.decided);
      await queryClient.invalidateQueries({ queryKey: ["admin", "queue"] });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : t.common.error),
  });

  return (
    <li>
      <Card>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <h2 className="text-lg font-semibold">{job.title}</h2>
              <p className="text-sm text-muted-foreground">
                {job.companyName} · {job.city} · {formatSalary(job.salaryMin, job.salaryMax, job.salaryType)}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <TrustBadge level={job.trustLevel} />
              <Badge variant="outline" className="border-warning">
                {fmt(t.admin.score, { n: job.riskScore ?? 0 })}
              </Badge>
              {item.reportCount > 0 && <Badge variant="destructive">{fmt(t.admin.reports, { n: item.reportCount })}</Badge>}
              {job.category && <Badge variant="secondary">{titleCase(job.category)}</Badge>}
            </div>
          </div>
          {job.riskReasons && job.riskReasons.length > 0 && (
            <ul className="list-inside list-disc text-sm">
              {job.riskReasons.map((r) => (
                <li key={r}>{riskReason(t, r)}</li>
              ))}
            </ul>
          )}
          {item.moderationNote && <p className="text-xs text-muted-foreground">{item.moderationNote}</p>}
          {job.description && (
            <p className="whitespace-pre-line rounded-md bg-muted/50 p-3 text-sm">{job.description}</p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <Input
              aria-label={t.admin.note}
              placeholder={t.admin.note}
              value={note}
              maxLength={1000}
              onChange={(e) => setNote(e.target.value)}
              className="max-w-sm"
            />
            <Button onClick={() => decide.mutate("APPROVED")} disabled={decide.isPending}>
              {t.admin.approve}
            </Button>
            <Button variant="destructive" onClick={() => decide.mutate("REJECTED")} disabled={decide.isPending}>
              {t.admin.reject}
            </Button>
          </div>
        </CardContent>
      </Card>
    </li>
  );
}
