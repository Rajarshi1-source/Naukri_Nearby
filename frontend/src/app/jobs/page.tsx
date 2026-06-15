import type { Metadata } from "next";

import SearchClient from "./SearchClient";

export const metadata: Metadata = {
  title: "Find jobs near you",
  description:
    "Search hyperlocal jobs by title, skill, category and distance on a live map.",
};

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

function first(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

export default async function JobsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const sp = await searchParams;
  const initialQuery = first(sp.q);
  const initialCategory = first(sp.category);
  const radiusRaw = Number(first(sp.radius));
  const initialRadius =
    Number.isFinite(radiusRaw) && radiusRaw > 0 ? radiusRaw : 10;

  return (
    <div>
      <h1 className="text-2xl font-bold text-foreground">Jobs near you</h1>
      <SearchClient
        initialQuery={initialQuery}
        initialCategory={initialCategory}
        initialRadius={initialRadius}
      />
    </div>
  );
}
