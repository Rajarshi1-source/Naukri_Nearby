"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Languages } from "lucide-react";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CONTENT_LANGUAGES } from "@/i18n";
import { useI18n } from "@/i18n/I18nProvider";
import { jobService } from "@/services/jobService";

const NONE = "none";

/**
 * "Read this job in …" — the job's title and description machine-translated by Bhashini on the server
 * (cached per job version). Defaults to the UI language when that is not English. The original stays
 * on the page above, and a translation is always labelled as one.
 */
export default function JobTranslationPanel({ slug }: { slug: string }) {
  const { locale, t } = useI18n();
  const [lang, setLang] = useState<string>(locale === "en" ? NONE : locale);
  const enabled = lang !== NONE;
  const { data, isFetching, isError } = useQuery({
    queryKey: ["job-translation", slug, lang],
    queryFn: () => jobService.translation(slug, lang),
    enabled,
    staleTime: 10 * 60_000,
  });

  const unavailable = isError || (data && !data.translated && data.language !== data.sourceLanguage);

  return (
    <section className="mt-5 space-y-3" aria-live="polite">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Languages className="size-4 text-brand" aria-hidden />
        <span>{t.job.readIn}</span>
        <Select value={lang} onValueChange={setLang}>
          <SelectTrigger className="h-8 w-auto min-w-32" aria-label={t.job.readIn}>
            <SelectValue placeholder="—" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE}>—</SelectItem>
            {Object.entries(CONTENT_LANGUAGES)
              .filter(([code]) => code !== "en")
              .map(([code, label]) => (
                <SelectItem key={code} value={code} lang={code}>
                  {label}
                </SelectItem>
              ))}
          </SelectContent>
        </Select>
      </div>

      {enabled && isFetching && <p className="text-sm text-muted-foreground">{t.job.translating}</p>}
      {enabled && !isFetching && unavailable && (
        <p className="text-sm text-muted-foreground">{t.job.translationUnavailable}</p>
      )}
      {enabled && !isFetching && data?.translated && (
        <div lang={data.language} className="rounded-lg border bg-muted/40 p-4">
          <p className="text-lg font-semibold leading-relaxed">{data.title}</p>
          {data.description && (
            <p className="mt-2 whitespace-pre-line leading-relaxed text-foreground/90">{data.description}</p>
          )}
          <p className="mt-3 text-xs text-muted-foreground">{t.job.translatedNote}</p>
        </div>
      )}
    </section>
  );
}
