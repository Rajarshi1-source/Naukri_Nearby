import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import SaveButton from "@/components/SaveButton";
import { formatDistance, formatSalary, titleCase } from "@/lib/format";
import type { SearchResultItem } from "@/types";

export default function JobCard({ item }: { item: SearchResultItem }) {
  const distance = formatDistance(item.distanceKm);
  return (
    <Card className="gap-0 py-0 transition hover:shadow-md">
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <Link
              href={`/jobs/${item.slug}`}
              className="text-lg font-semibold text-foreground hover:text-brand"
            >
              {item.title}
            </Link>
            <p className="text-sm text-muted-foreground">
              {item.companyName || "Local employer"}
              {item.city ? ` · ${item.city}` : ""}
            </p>
          </div>
          <SaveButton jobId={item.id} />
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
          {item.category && (
            <Badge className="bg-brand-light text-brand-dark">
              {titleCase(item.category)}
            </Badge>
          )}
          <span className="text-foreground/80">
            {formatSalary(item.salaryMin, item.salaryMax)}
          </span>
          {distance && (
            <span className="text-muted-foreground">· {distance}</span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
