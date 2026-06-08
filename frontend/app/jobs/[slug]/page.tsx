import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import ApplyButton from "@/components/ApplyButton";
import SaveButton from "@/components/SaveButton";
import { serverGet } from "@/lib/api";
import { formatSalary, titleCase } from "@/lib/format";
import { jobMetaDescription, jobPostingJsonLd } from "@/lib/seo";
import type { JobResponse } from "@/lib/types";

interface Params {
  params: { slug: string };
}

async function getJob(slug: string): Promise<JobResponse | null> {
  return serverGet<JobResponse>(`/api/jobs/${encodeURIComponent(slug)}`);
}

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const job = await getJob(params.slug);
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

export default async function JobDetailPage({ params }: Params) {
  const job = await getJob(params.slug);
  if (!job) notFound();

  return (
    <article className="mx-auto max-w-3xl">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jobPostingJsonLd(job)) }}
      />

      <Link href="/jobs" className="text-sm text-brand hover:underline">
        ← Back to search
      </Link>

      <div className="mt-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">{job.title}</h1>
            <p className="mt-1 text-slate-500">
              {job.companyName || "Local employer"}
              {job.city ? ` · ${job.city}` : ""}
              {job.state ? `, ${job.state}` : ""}
            </p>
          </div>
          <SaveButton jobId={job.id} />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
          {job.category && (
            <span className="rounded-full bg-brand-light px-2.5 py-0.5 text-brand-dark">
              {titleCase(job.category)}
            </span>
          )}
          <span className="font-medium text-slate-800">
            {formatSalary(job.salaryMin, job.salaryMax, job.salaryType)}
          </span>
          {job.status && job.status !== "ACTIVE" && (
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-slate-600">
              {titleCase(job.status)}
            </span>
          )}
        </div>

        {job.skillsRequired.length > 0 && (
          <div className="mt-4">
            <h2 className="text-sm font-semibold text-slate-700">Skills</h2>
            <div className="mt-2 flex flex-wrap gap-2">
              {job.skillsRequired.map((s) => (
                <span
                  key={s}
                  className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-700"
                >
                  {s}
                </span>
              ))}
            </div>
          </div>
        )}

        {job.description && (
          <div className="mt-5">
            <h2 className="text-sm font-semibold text-slate-700">Description</h2>
            <p className="mt-2 whitespace-pre-line text-slate-700">{job.description}</p>
          </div>
        )}

        <div className="mt-6 border-t border-slate-100 pt-5">
          <ApplyButton jobId={job.id} slug={job.slug} />
        </div>
      </div>
    </article>
  );
}
