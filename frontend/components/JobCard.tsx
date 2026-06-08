import Link from "next/link";

import { formatDistance, formatSalary, titleCase } from "@/lib/format";
import type { SearchResultItem } from "@/lib/types";
import SaveButton from "./SaveButton";

export default function JobCard({ item }: { item: SearchResultItem }) {
  const distance = formatDistance(item.distanceKm);
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <div>
          <Link
            href={`/jobs/${item.slug}`}
            className="text-lg font-semibold text-slate-900 hover:text-brand"
          >
            {item.title}
          </Link>
          <p className="text-sm text-slate-500">
            {item.companyName || "Local employer"}
            {item.city ? ` · ${item.city}` : ""}
          </p>
        </div>
        <SaveButton jobId={item.id} />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        {item.category && (
          <span className="rounded-full bg-brand-light px-2.5 py-0.5 text-brand-dark">
            {titleCase(item.category)}
          </span>
        )}
        <span className="text-slate-700">{formatSalary(item.salaryMin, item.salaryMax)}</span>
        {distance && <span className="text-slate-400">· {distance}</span>}
      </div>
    </div>
  );
}
