import type { Metadata } from "next";
import Link from "next/link";
import { WifiOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { getServerDictionary } from "@/i18n/server";

export const metadata: Metadata = { title: "Offline" };

/** Navigation fallback served by the service worker when a page is not cached (ADR 0009). */
export default async function OfflinePage() {
  const { t } = await getServerDictionary();
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-muted">
        <WifiOff className="size-7 text-muted-foreground" aria-hidden />
      </span>
      <h1 className="text-2xl font-bold">{t.offline.pageTitle}</h1>
      <p className="leading-relaxed text-muted-foreground">{t.offline.pageBody}</p>
      <div className="flex flex-wrap justify-center gap-3">
        <Button asChild>
          <Link href="/jobs">{t.offline.goJobs}</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/">{t.offline.retry}</Link>
        </Button>
      </div>
    </div>
  );
}
