"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Trash2 } from "lucide-react";
import { toast } from "sonner";

import TrustBadge from "@/components/TrustBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CONTENT_LANGUAGES, fmt } from "@/i18n";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDistance, formatSalary, titleCase } from "@/lib/format";
import { candidateService } from "@/services/candidateService";
import type { Consent } from "@/types";

export default function CandidateDashboard() {
  const { isAuthenticated, role, loading, logout } = useAuth();
  const { t, locale } = useI18n();
  const router = useRouter();
  const enabled = isAuthenticated && role === "CANDIDATE";

  useEffect(() => {
    if (!loading && !enabled) router.push("/login?next=/candidate");
  }, [loading, enabled, router]);

  const dateFmt = (iso: string | null | undefined) =>
    iso ? new Date(iso).toLocaleDateString(locale === "hi" ? "hi-IN" : "en-IN", { dateStyle: "medium" }) : "";

  const profile = useQuery({
    queryKey: ["candidate", "profile"],
    queryFn: async () => {
      try {
        return await candidateService.getProfile();
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null;
        throw err;
      }
    },
    enabled,
  });
  const applications = useQuery({ queryKey: ["candidate", "applications"], queryFn: candidateService.applications, enabled });
  const saved = useQuery({ queryKey: ["candidate", "saved"], queryFn: candidateService.savedJobs, enabled });
  const recommended = useQuery({ queryKey: ["candidate", "recommended"], queryFn: candidateService.recommendations, enabled });

  if (loading || !enabled) return null;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <h1 className="text-2xl font-bold">{t.candidate.title}</h1>

      <Card>
        <CardHeader>
          <CardTitle>{t.candidate.profile}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {profile.isLoading && <p className="text-muted-foreground">{t.common.loading}</p>}
          {profile.data === null && <p className="text-muted-foreground">{t.candidate.noProfile}</p>}
          {profile.data && (
            <>
              <p className="font-medium">
                {profile.data.name || "—"}
                {profile.data.city ? ` · ${profile.data.city}` : ""}
              </p>
              <p className="text-sm text-muted-foreground">
                {fmt(t.candidate.experience, { n: profile.data.totalExperienceMonths ?? 0 })} ·{" "}
                {fmt(t.candidate.completeness, { n: profile.data.profileCompleteness ?? 0 })}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {profile.data.skills.map((s) => (
                  <Badge key={s} variant="secondary">
                    {s}
                  </Badge>
                ))}
              </div>
            </>
          )}
          <Button asChild variant="outline" size="sm">
            <Link href="/candidate/resume">{t.candidate.uploadResume}</Link>
          </Button>
        </CardContent>
      </Card>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t.candidate.applications}</CardTitle>
          </CardHeader>
          <CardContent>
            {applications.data?.length === 0 && (
              <p className="text-sm text-muted-foreground">{t.candidate.noApplications}</p>
            )}
            <ul className="divide-y">
              {applications.data?.map((a) => (
                <li key={a.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <div>
                    {a.jobSlug ? (
                      <Link href={`/jobs/${a.jobSlug}`} className="font-medium hover:text-brand">
                        {a.jobTitle}
                      </Link>
                    ) : (
                      <span className="font-medium">#{a.jobId}</span>
                    )}
                    <p className="text-xs text-muted-foreground">{fmt(t.candidate.appliedOn, { date: dateFmt(a.appliedAt) })}</p>
                  </div>
                  <Badge variant="outline">{titleCase(a.status)}</Badge>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t.candidate.saved}</CardTitle>
          </CardHeader>
          <CardContent>
            {saved.data?.length === 0 && <p className="text-sm text-muted-foreground">{t.candidate.noSaved}</p>}
            <ul className="divide-y">
              {saved.data?.map((j) => (
                <li key={j.id} className="py-2 text-sm">
                  <Link href={`/jobs/${j.slug}`} className="font-medium hover:text-brand">
                    {j.title}
                  </Link>
                  <p className="text-xs text-muted-foreground">
                    {j.companyName || t.job.localEmployer}
                    {j.city ? ` · ${j.city}` : ""}
                  </p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t.candidate.recommended}</CardTitle>
        </CardHeader>
        <CardContent>
          {recommended.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">{t.candidate.noRecommended}</p>
          )}
          <ul className="grid gap-3 sm:grid-cols-2">
            {recommended.data?.map((m) => (
              <li key={m.job.id} className="rounded-lg border p-3 text-sm">
                <Link href={`/jobs/${m.job.slug}`} className="font-semibold hover:text-brand">
                  {m.job.title}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {m.job.companyName || t.job.localEmployer}
                  {m.job.city ? ` · ${m.job.city}` : ""}
                  {formatDistance(m.distanceKm) ? ` · ${formatDistance(m.distanceKm)}` : ""}
                </p>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <TrustBadge level={m.job.trustLevel} />
                  <span className="text-xs">{formatSalary(m.job.salaryMin, m.job.salaryMax)}</span>
                </div>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <AlertSettings />
      <PrivacySection
        onDeleted={async () => {
          await logout();
          toast.success(t.candidate.deleted);
          router.push("/");
        }}
        dateFmt={dateFmt}
      />
    </div>
  );
}

function AlertSettings() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const prefs = useQuery({ queryKey: ["candidate", "prefs"], queryFn: candidateService.preferences });
  const [draft, setDraft] = useState<{ active: boolean; language: string; radiusKm: number } | null>(null);
  const current = draft ?? {
    active: prefs.data?.active ?? true,
    language: prefs.data?.language ?? "en",
    radiusKm: prefs.data?.radiusKm ?? 10,
  };
  const save = useMutation({
    mutationFn: () => candidateService.updatePreferences(current),
    onSuccess: (data) => {
      queryClient.setQueryData(["candidate", "prefs"], data);
      setDraft(null);
      toast.success(t.candidate.prefsSaved);
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : t.common.error),
  });

  if (!prefs.data) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.candidate.alerts}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 accent-[var(--brand)]"
            checked={current.active}
            onChange={(e) => setDraft({ ...current, active: e.target.checked })}
          />
          {t.candidate.alertsActive}
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>{t.candidate.alertLanguage}</Label>
            <Select value={current.language} onValueChange={(v) => setDraft({ ...current, language: v })}>
              <SelectTrigger className="w-full" aria-label={t.candidate.alertLanguage}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(CONTENT_LANGUAGES).map(([code, label]) => (
                  <SelectItem key={code} value={code} lang={code}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="alert-radius">{t.candidate.alertRadius}</Label>
            <Input
              id="alert-radius"
              type="number"
              min={1}
              max={100}
              value={current.radiusKm}
              onChange={(e) => setDraft({ ...current, radiusKm: Number(e.target.value) || 1 })}
            />
          </div>
        </div>
        <Button onClick={() => save.mutate()} disabled={save.isPending || draft === null}>
          {t.candidate.savePrefs}
        </Button>
      </CardContent>
    </Card>
  );
}

function PrivacySection({
  onDeleted,
  dateFmt,
}: {
  onDeleted: () => Promise<void>;
  dateFmt: (iso: string | null | undefined) => string;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const consents = useQuery({ queryKey: ["candidate", "consents"], queryFn: candidateService.consents });
  const [busy, setBusy] = useState(false);

  async function toggle(c: Consent, granted: boolean) {
    try {
      await candidateService.setConsent(c.purpose, granted);
      await queryClient.invalidateQueries({ queryKey: ["candidate", "consents"] });
      toast.success(t.candidate.consentSaved);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t.common.error);
    }
  }

  async function exportData() {
    setBusy(true);
    try {
      await candidateService.downloadMyData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t.common.error);
    } finally {
      setBusy(false);
    }
  }

  async function deleteAccount() {
    if (!window.confirm(t.candidate.deleteConfirm)) return;
    setBusy(true);
    try {
      await candidateService.deleteAccount();
      await onDeleted();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t.common.error);
      setBusy(false);
    }
  }

  const label: Record<Consent["purpose"], string> = {
    RESUME_PROCESSING: t.candidate.consentResume,
    WHATSAPP_ALERTS: t.candidate.consentAlerts,
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.candidate.privacy}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <p className="text-sm text-muted-foreground">{t.candidate.privacyIntro}</p>
        <ul className="space-y-3">
          {consents.data?.map((c) => (
            <li key={c.purpose}>
              <label className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  className="mt-1 size-4 accent-[var(--brand)]"
                  checked={c.granted !== false}
                  onChange={(e) => void toggle(c, e.target.checked)}
                />
                <span>
                  {label[c.purpose]}
                  <span className="block text-xs text-muted-foreground">
                    {c.recordedAt
                      ? fmt(t.candidate.consentRecorded, { date: dateFmt(c.recordedAt) })
                      : t.candidate.consentNotRecorded}
                  </span>
                </span>
              </label>
            </li>
          ))}
        </ul>
        <div className="flex flex-col gap-4 border-t pt-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <Button variant="outline" onClick={exportData} disabled={busy}>
              <Download className="size-4" aria-hidden />
              {t.candidate.export}
            </Button>
            <p className="max-w-xs text-xs text-muted-foreground">{t.candidate.exportHint}</p>
          </div>
          <div className="space-y-1">
            <Button variant="destructive" onClick={deleteAccount} disabled={busy}>
              <Trash2 className="size-4" aria-hidden />
              {t.candidate.delete}
            </Button>
            <p className="max-w-xs text-xs text-muted-foreground">{t.candidate.deleteHint}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
