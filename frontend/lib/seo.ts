import { formatSalary } from "./format";
import type { JobResponse } from "./types";

/** Builds a schema.org JobPosting JSON-LD object for Google rich results. */
export function jobPostingJsonLd(job: JobResponse) {
  return {
    "@context": "https://schema.org",
    "@type": "JobPosting",
    title: job.title,
    description: job.description || job.title,
    datePosted: job.createdAt,
    employmentType: "FULL_TIME",
    hiringOrganization: {
      "@type": "Organization",
      name: job.companyName || "Local employer",
    },
    jobLocation: {
      "@type": "Place",
      address: {
        "@type": "PostalAddress",
        addressLocality: job.city,
        addressRegion: job.state,
        postalCode: job.pincode,
        addressCountry: "IN",
      },
    },
    ...(job.salaryMin || job.salaryMax
      ? {
          baseSalary: {
            "@type": "MonetaryAmount",
            currency: "INR",
            value: {
              "@type": "QuantitativeValue",
              minValue: job.salaryMin ?? undefined,
              maxValue: job.salaryMax ?? undefined,
              unitText: (job.salaryType || "MONTH").toUpperCase(),
            },
          },
        }
      : {}),
  };
}

export function jobMetaDescription(job: JobResponse): string {
  const pay = formatSalary(job.salaryMin, job.salaryMax, job.salaryType);
  const where = [job.city, job.state].filter(Boolean).join(", ");
  return `${job.title} at ${job.companyName || "a local employer"}${
    where ? ` in ${where}` : ""
  }. ${pay}. Apply on NaukriNearby.`;
}
