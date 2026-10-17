import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ShieldAlert } from "lucide-react";

import ApplyButton from "@/components/ApplyButton";
import ReportJobButton from "@/components/ReportJobButton";
import SaveButton from "@/components/SaveButton";
import JobTranslationPanel from "@/components/JobTranslationPanel";
import SkillGapPanel from "@/components/SkillGapPanel";
import TrustBadge from "@/components/TrustBadge";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { fmt } from "@/i18n";
import { getServerDictionary } from "@/i18n/server";
import { formatSalary, titleCase } from "@/lib/format";
import { jobMetaDescription, jobPostingJsonLd } from "@/lib/seo";
import { jobService } from "@/services/jobService";

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const job = await jobService.getBySlug(slug);
  if (!job) {
    const { t } = await getServerDictionary();
    return { title: t.job.notFound };
  }
  return {
    title: `${job.title}${job.city ? ` in ${job.city}` : ""}`,
    description: jobMetaDescription(job),
    alternates: { canonical: `/jobs/${job.slug}` },
    openGraph: {
      title: job.title,
      description: jobMetaDescription(job),
      type: "website",
    },
  };
}

export default async function JobDetailPage({ params }: Props) {
  const { slug } = await params;
  const [job, { locale, t }] = await Promise.all([jobService.getBySlug(slug), getServerDictionary()]);
  if (!job) notFound();

  const since = job.employerSince
    ? new Date(job.employerSince).toLocaleDateString(locale === "hi" ? "hi-IN" : "en-IN", {
        month: "long",
        year: "numeric",
      })
    : null;

  return (
    <article className="mx-auto max-w-3xl">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(jobPostingJsonLd(job)).replace(/</g, "\\u003c"),
        }}
      />

      <Link href="/jobs" className="text-sm text-brand hover:underline">
        {t.job.back}
      </Link>

      <Card className="mt-3">
        <CardContent>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-foreground">{job.title}</h1>
              <p className="mt-1 text-muted-foreground">
                {job.companyName || t.job.localEmployer}
                {job.city ? ` · ${job.city}` : ""}
                {job.state ? `, ${job.state}` : ""}
              </p>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <TrustBadge level={job.trustLevel} />
                {since && <span>{fmt(t.job.employerSince, { date: since })}</span>}
              </div>
            </div>
            <SaveButton jobId={job.id} />
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
            {job.category && (
              <Badge className="bg-brand-light text-brand-dark">{titleCase(job.category)}</Badge>
            )}
            <span className="font-medium text-foreground">
              {job.salaryMin || job.salaryMax
                ? formatSalary(job.salaryMin, job.salaryMax, job.salaryType)
                : t.job.salaryHidden}
            </span>
            {job.status && job.status !== "ACTIVE" && (
              <Badge variant="secondary">{titleCase(job.status)}</Badge>
            )}
          </div>

          {job.skillsRequired.length > 0 && (
            <div className="mt-4">
              <h2 className="text-sm font-semibold text-foreground/80">{t.job.skills}</h2>
              <div className="mt-2 flex flex-wrap gap-2">
                {job.skillsRequired.map((s) => (
                  <Badge key={s} variant="secondary">
                    {s}
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {job.description && (
            <div className="mt-5">
              <h2 className="text-sm font-semibold text-foreground/80">{t.job.description}</h2>
              <p className="mt-2 whitespace-pre-line text-foreground/90">{job.description}</p>
            </div>
          )}

          <JobTranslationPanel slug={job.slug} />
          <SkillGapPanel jobId={job.id} />

          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t pt-5">
            <ApplyButton jobId={job.id} slug={job.slug} />
            <ReportJobButton jobId={job.id} slug={job.slug} />
          </div>
          <p className="mt-4 flex items-start gap-2 rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
            <ShieldAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            {t.trust.safety}
          </p>
        </CardContent>
      </Card>
    </article>
  );
}
