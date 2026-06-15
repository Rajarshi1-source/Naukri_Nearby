import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import ApplyButton from "@/components/ApplyButton";
import SaveButton from "@/components/SaveButton";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { formatSalary, titleCase } from "@/lib/format";
import { jobMetaDescription, jobPostingJsonLd } from "@/lib/seo";
import { jobService } from "@/services/jobService";

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const job = await jobService.getBySlug(slug);
  if (!job) return { title: "Job not found" };
  return {
    title: `${job.title}${job.city ? ` in ${job.city}` : ""}`,
    description: jobMetaDescription(job),
    openGraph: {
      title: job.title,
      description: jobMetaDescription(job),
      type: "website",
    },
  };
}

export default async function JobDetailPage({ params }: Props) {
  const { slug } = await params;
  const job = await jobService.getBySlug(slug);
  if (!job) notFound();

  return (
    <article className="mx-auto max-w-3xl">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(jobPostingJsonLd(job)),
        }}
      />

      <Link href="/jobs" className="text-sm text-brand hover:underline">
        ← Back to search
      </Link>

      <Card className="mt-3">
        <CardContent>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-foreground">
                {job.title}
              </h1>
              <p className="mt-1 text-muted-foreground">
                {job.companyName || "Local employer"}
                {job.city ? ` · ${job.city}` : ""}
                {job.state ? `, ${job.state}` : ""}
              </p>
            </div>
            <SaveButton jobId={job.id} />
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
            {job.category && (
              <Badge className="bg-brand-light text-brand-dark">
                {titleCase(job.category)}
              </Badge>
            )}
            <span className="font-medium text-foreground">
              {formatSalary(job.salaryMin, job.salaryMax, job.salaryType)}
            </span>
            {job.status && job.status !== "ACTIVE" && (
              <Badge variant="secondary">{titleCase(job.status)}</Badge>
            )}
          </div>

          {job.skillsRequired.length > 0 && (
            <div className="mt-4">
              <h2 className="text-sm font-semibold text-foreground/80">
                Skills
              </h2>
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
              <h2 className="text-sm font-semibold text-foreground/80">
                Description
              </h2>
              <p className="mt-2 whitespace-pre-line text-foreground/90">
                {job.description}
              </p>
            </div>
          )}

          <div className="mt-6 border-t pt-5">
            <ApplyButton jobId={job.id} slug={job.slug} />
          </div>
        </CardContent>
      </Card>
    </article>
  );
}
