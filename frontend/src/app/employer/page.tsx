"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, LocateFixed } from "lucide-react";
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
import { fmt, type Dictionary } from "@/i18n";
import { useT } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatSalary, titleCase } from "@/lib/format";
import { riskReason } from "@/lib/risk";
import { employerService, type NewJobInput } from "@/services/employerService";
import { JOB_CATEGORIES, type JobResponse } from "@/types";

/** Quick picks for employers without GPS (common Tier-2/3 cities). */
const CITY_PRESETS: { city: string; state: string; lat: number; lng: number }[] = [
  { city: "Lucknow", state: "Uttar Pradesh", lat: 26.8467, lng: 80.9462 },
  { city: "Kanpur", state: "Uttar Pradesh", lat: 26.4499, lng: 80.3319 },
  { city: "Indore", state: "Madhya Pradesh", lat: 22.7196, lng: 75.8577 },
  { city: "Patna", state: "Bihar", lat: 25.5941, lng: 85.1376 },
  { city: "Jaipur", state: "Rajasthan", lat: 26.9124, lng: 75.7873 },
  { city: "Nagpur", state: "Maharashtra", lat: 21.1458, lng: 79.0882 },
];

const APPLICATION_NEXT: Record<string, string[]> = {
  APPLIED: ["VIEWED", "SHORTLISTED", "REJECTED"],
  VIEWED: ["SHORTLISTED", "REJECTED"],
  SHORTLISTED: ["HIRED", "REJECTED"],
};

export default function EmployerDashboard() {
  const { isAuthenticated, role, loading } = useAuth();
  const t = useT();
  const router = useRouter();
  const enabled = isAuthenticated && role === "EMPLOYER";

  useEffect(() => {
    if (!loading && !enabled) router.push("/login?next=/employer");
  }, [loading, enabled, router]);

  if (loading || !enabled) return null;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <h1 className="text-2xl font-bold">{t.employer.title}</h1>
      <ProfileCard />
      <PostJobCard />
      <MyJobs />
    </div>
  );
}

function ProfileCard() {
  const t = useT();
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ["employer", "profile"], queryFn: employerService.profile });
  const [draft, setDraft] = useState<{ companyName: string; companyType: string; gstNumber: string; city: string; state: string } | null>(null);
  const ids = { name: useId(), type: useId(), gst: useId(), city: useId(), state: useId() };

  const current = draft ?? {
    companyName: profile.data?.companyName ?? "",
    companyType: profile.data?.companyType ?? "",
    gstNumber: profile.data?.gstNumber ?? "",
    city: profile.data?.city ?? "",
    state: profile.data?.state ?? "",
  };

  const save = useMutation({
    mutationFn: () =>
      employerService.updateProfile({
        companyName: current.companyName,
        companyType: current.companyType || undefined,
        gstNumber: current.gstNumber || undefined,
        city: current.city || undefined,
        state: current.state || undefined,
      }),
    onSuccess: async (data) => {
      queryClient.setQueryData(["employer", "profile"], data);
      await queryClient.invalidateQueries({ queryKey: ["employer", "jobs"] });
      setDraft(null);
      toast.success(t.employer.profileSaved);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : t.common.error),
  });

  if (!profile.data) return null;
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <CardTitle>{t.employer.profile}</CardTitle>
        <TrustBadge level={profile.data.trustLevel} />
      </CardHeader>
      <CardContent>
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <Field id={ids.name} label={t.employer.companyName}>
            <Input id={ids.name} required maxLength={300} value={current.companyName}
              onChange={(e) => setDraft({ ...current, companyName: e.target.value })} />
          </Field>
          <Field id={ids.type} label={t.employer.companyType}>
            <Input id={ids.type} maxLength={50} value={current.companyType}
              onChange={(e) => setDraft({ ...current, companyType: e.target.value })} />
          </Field>
          <Field id={ids.gst} label={t.employer.gstin} hint={t.employer.gstinHint}>
            <Input id={ids.gst} maxLength={15} value={current.gstNumber} className="uppercase"
              placeholder="27AAPFU0939F1ZV"
              onChange={(e) => setDraft({ ...current, gstNumber: e.target.value })} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field id={ids.city} label={t.employer.city}>
              <Input id={ids.city} value={current.city} onChange={(e) => setDraft({ ...current, city: e.target.value })} />
            </Field>
            <Field id={ids.state} label={t.employer.state}>
              <Input id={ids.state} value={current.state} onChange={(e) => setDraft({ ...current, state: e.target.value })} />
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Button type="submit" disabled={save.isPending || draft === null}>
              {t.employer.saveProfile}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

const EMPTY_JOB = {
  title: "",
  description: "",
  category: "DELIVERY",
  skills: "",
  salaryMin: "",
  salaryMax: "",
  city: "",
  state: "",
  pincode: "",
  address: "",
  lat: "",
  lng: "",
};

function PostJobCard() {
  const t = useT();
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY_JOB);
  const [result, setResult] = useState<JobResponse | null>(null);
  const ids = {
    title: useId(), desc: useId(), skills: useId(), min: useId(), max: useId(),
    city: useId(), state: useId(), pin: useId(), addr: useId(), lat: useId(), lng: useId(),
  };
  const set = (patch: Partial<typeof EMPTY_JOB>) => setForm((f) => ({ ...f, ...patch }));

  const post = useMutation({
    mutationFn: () => {
      const input: NewJobInput = {
        title: form.title.trim(),
        description: form.description.trim(),
        category: form.category,
        skillsRequired: form.skills.split(",").map((s) => s.trim()).filter(Boolean),
        salaryMin: form.salaryMin ? Number(form.salaryMin) : undefined,
        salaryMax: form.salaryMax ? Number(form.salaryMax) : undefined,
        salaryType: "MONTHLY",
        experienceRequiredMonths: 0,
        lat: Number(form.lat),
        lng: Number(form.lng),
        address: form.address || undefined,
        city: form.city.trim(),
        state: form.state.trim(),
        pincode: form.pincode || undefined,
        radiusKm: 10,
      };
      return employerService.postJob(input);
    },
    onSuccess: async (job) => {
      setResult(job);
      setForm(EMPTY_JOB);
      await queryClient.invalidateQueries({ queryKey: ["employer", "jobs"] });
      if (job.moderationStatus === "PENDING_REVIEW") toast.warning(t.employer.underReview);
      else toast.success(t.employer.published);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : t.common.error),
  });

  function locateMe() {
    navigator.geolocation?.getCurrentPosition(
      (pos) => set({ lat: pos.coords.latitude.toFixed(5), lng: pos.coords.longitude.toFixed(5) }),
      () => toast.error(t.employer.locationRequired),
      { timeout: 8000 },
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.employer.postJob}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {result?.moderationStatus === "PENDING_REVIEW" && <ModerationNotice job={result} />}
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (!form.lat || !form.lng) {
              toast.error(t.employer.locationRequired);
              return;
            }
            post.mutate();
          }}
        >
          <Field id={ids.title} label={t.employer.jobTitle}>
            <Input id={ids.title} required maxLength={300} value={form.title} onChange={(e) => set({ title: e.target.value })} />
          </Field>
          <div className="space-y-1.5">
            <Label>{t.employer.category}</Label>
            <Select value={form.category} onValueChange={(v) => set({ category: v })}>
              <SelectTrigger className="w-full" aria-label={t.employer.category}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {JOB_CATEGORIES.map((c) => (
                  <SelectItem key={c} value={c}>
                    {titleCase(c)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor={ids.desc}>{t.employer.jobDescription}</Label>
            <textarea id={ids.desc} required rows={4} value={form.description}
              onChange={(e) => set({ description: e.target.value })}
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50" />
          </div>
          <Field id={ids.skills} label={t.employer.skills}>
            <Input id={ids.skills} value={form.skills} placeholder="Driving, Tally" onChange={(e) => set({ skills: e.target.value })} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field id={ids.min} label={t.employer.salaryMin}>
              <Input id={ids.min} type="number" min={0} value={form.salaryMin} onChange={(e) => set({ salaryMin: e.target.value })} />
            </Field>
            <Field id={ids.max} label={t.employer.salaryMax}>
              <Input id={ids.max} type="number" min={0} value={form.salaryMax} onChange={(e) => set({ salaryMax: e.target.value })} />
            </Field>
          </div>

          <fieldset className="space-y-3 rounded-lg border p-3 sm:col-span-2">
            <legend className="px-1 text-sm font-medium">{t.employer.location}</legend>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={locateMe}>
                <LocateFixed className="size-4" aria-hidden />
                {t.employer.useMyLocation}
              </Button>
              {CITY_PRESETS.map((p) => (
                <Button key={p.city} type="button" variant="ghost" size="sm"
                  onClick={() => set({ city: p.city, state: p.state, lat: String(p.lat), lng: String(p.lng) })}>
                  {p.city}
                </Button>
              ))}
            </div>
            <div className="grid gap-3 sm:grid-cols-4">
              <Field id={ids.city} label={t.employer.city}>
                <Input id={ids.city} required value={form.city} onChange={(e) => set({ city: e.target.value })} />
              </Field>
              <Field id={ids.state} label={t.employer.state}>
                <Input id={ids.state} required value={form.state} onChange={(e) => set({ state: e.target.value })} />
              </Field>
              <Field id={ids.lat} label="Lat">
                <Input id={ids.lat} required inputMode="decimal" value={form.lat} onChange={(e) => set({ lat: e.target.value })} />
              </Field>
              <Field id={ids.lng} label="Lng">
                <Input id={ids.lng} required inputMode="decimal" value={form.lng} onChange={(e) => set({ lng: e.target.value })} />
              </Field>
              <Field id={ids.pin} label={t.employer.pincode}>
                <Input id={ids.pin} inputMode="numeric" maxLength={6} value={form.pincode} onChange={(e) => set({ pincode: e.target.value })} />
              </Field>
              <div className="sm:col-span-3">
                <Field id={ids.addr} label={t.employer.address}>
                  <Input id={ids.addr} value={form.address} onChange={(e) => set({ address: e.target.value })} />
                </Field>
              </div>
            </div>
          </fieldset>

          <div className="sm:col-span-2">
            <Button type="submit" disabled={post.isPending}>
              {post.isPending ? t.employer.publishing : t.employer.publish}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function ModerationNotice({ job }: { job: JobResponse }) {
  const t = useT();
  return (
    <div role="status" className="rounded-lg border border-warning bg-warning/10 p-3 text-sm">
      <p className="flex items-center gap-2 font-medium">
        <AlertTriangle className="size-4" aria-hidden />
        {t.employer.underReview}
      </p>
      {job.riskReasons && job.riskReasons.length > 0 && (
        <>
          <p className="mt-2 text-xs text-muted-foreground">{t.employer.reviewReasons}</p>
          <ul className="mt-1 list-inside list-disc">
            {job.riskReasons.map((r) => (
              <li key={r}>{riskReason(t, r)}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function MyJobs() {
  const t = useT();
  const jobs = useQuery({ queryKey: ["employer", "jobs"], queryFn: employerService.myJobs });
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.employer.myJobs}</CardTitle>
      </CardHeader>
      <CardContent>
        {jobs.data?.length === 0 && <p className="text-sm text-muted-foreground">{t.employer.noJobs}</p>}
        <ul className="space-y-3">
          {jobs.data?.map((job) => (
            <JobRow key={job.id} job={job} />
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function JobRow({ job }: { job: JobResponse }) {
  const t = useT();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const applicants = useQuery({
    queryKey: ["employer", "applicants", job.id],
    queryFn: () => employerService.applicants(job.id),
    enabled: open,
  });
  const close = useMutation({
    mutationFn: () => employerService.closeJob(job.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["employer", "jobs"] }),
  });
  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) => employerService.setApplicationStatus(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["employer", "applicants", job.id] }),
    onError: (err) => toast.error(err instanceof ApiError ? err.message : t.common.error),
  });
  const moderation = (job.moderationStatus ?? "APPROVED") as keyof Dictionary["moderation"];

  return (
    <li className="rounded-lg border p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          {moderation === "APPROVED" ? (
            <Link href={`/jobs/${job.slug}`} className="font-semibold hover:text-brand">
              {job.title}
            </Link>
          ) : (
            <span className="font-semibold">{job.title}</span>
          )}
          <p className="text-xs text-muted-foreground">
            {job.city} · {formatSalary(job.salaryMin, job.salaryMax, job.salaryType)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge variant={moderation === "APPROVED" ? "secondary" : "outline"}
            className={moderation === "PENDING_REVIEW" ? "border-warning" : undefined}>
            {t.employer.moderationLabel}: {t.moderation[moderation] ?? moderation}
          </Badge>
          <Badge variant="outline">
            {t.employer.statusLabel}: {titleCase(job.status)}
          </Badge>
        </div>
      </div>
      {moderation === "PENDING_REVIEW" && <div className="mt-2"><ModerationNotice job={job} /></div>}
      <div className="mt-2 flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
          {fmt(t.employer.applicants, { n: job.applicationCount ?? 0 })}
        </Button>
        {job.status === "ACTIVE" && (
          <Button variant="ghost" size="sm" onClick={() => close.mutate()} disabled={close.isPending}>
            {t.employer.close}
          </Button>
        )}
      </div>
      {open && (
        <ul className="mt-3 divide-y text-sm">
          {applicants.data?.length === 0 && <li className="py-2 text-muted-foreground">{t.employer.noApplicants}</li>}
          {applicants.data?.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <div>
                <p className="font-medium">{a.candidateName || "—"}</p>
                {a.candidatePhone && (
                  <a href={`tel:+91${a.candidatePhone}`} className="text-xs text-brand">
                    +91 {a.candidatePhone}
                  </a>
                )}
                {a.coverNote && <p className="text-xs text-muted-foreground">{a.coverNote}</p>}
              </div>
              <div className="flex flex-wrap items-center gap-1">
                <Badge variant="outline">{titleCase(a.status)}</Badge>
                {(APPLICATION_NEXT[a.status] ?? []).map((s) => (
                  <Button key={s} size="sm" variant="ghost" disabled={setStatus.isPending}
                    onClick={() => setStatus.mutate({ id: a.id, status: s })}>
                    {titleCase(s)}
                  </Button>
                ))}
              </div>
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}

function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}
