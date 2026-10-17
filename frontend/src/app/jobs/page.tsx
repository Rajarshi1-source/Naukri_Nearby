import type { Metadata } from "next";

import { getServerDictionary } from "@/i18n/server";
import SearchClient from "./SearchClient";

export async function generateMetadata(): Promise<Metadata> {
  const { t } = await getServerDictionary();
  return { title: t.search.title, description: t.search.subtitle };
}

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

  const { t } = await getServerDictionary();

  return (
    <div>
      <h1 className="text-2xl font-bold text-foreground">{t.search.title}</h1>
      <p className="mt-1 text-sm text-muted-foreground">{t.search.subtitle}</p>
      <SearchClient
        initialQuery={initialQuery}
        initialCategory={initialCategory}
        initialRadius={initialRadius}
      />
    </div>
  );
}
