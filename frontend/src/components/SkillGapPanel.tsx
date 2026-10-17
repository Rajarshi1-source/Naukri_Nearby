"use client";

import { useQuery } from "@tanstack/react-query";
import { ExternalLink, GraduationCap } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { fmt } from "@/i18n";
import { useT } from "@/i18n/I18nProvider";
import { useAuth } from "@/lib/auth";
import { candidateService } from "@/services/candidateService";

/**
 * Skill-gap recommendations (supplement G7) for a signed-in candidate: which of the job's skills they
 * have, which are missing, and free official portals to learn them. Renders nothing for others.
 */
export default function SkillGapPanel({ jobId }: { jobId: number }) {
  const t = useT();
  const { isAuthenticated, role } = useAuth();
  const enabled = isAuthenticated && role === "CANDIDATE";
  const { data } = useQuery({
    queryKey: ["skill-gap", jobId],
    queryFn: () => candidateService.skillGap(jobId),
    enabled,
  });

  if (!enabled || !data) return null;
  if (data.matched.length + data.missing.length === 0) return null;

  return (
    <section aria-labelledby={`gap-${jobId}`} className="mt-5 rounded-lg border bg-muted/40 p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 id={`gap-${jobId}`} className="flex items-center gap-2 text-sm font-semibold">
          <GraduationCap className="size-4 text-brand" aria-hidden />
          {t.skillGap.title}
        </h2>
        <Badge variant="secondary">{fmt(t.skillGap.match, { n: data.matchPercent })}</Badge>
      </div>

      {data.matched.length > 0 && (
        <div className="mt-3">
          <p className="text-xs text-muted-foreground">{t.skillGap.have}</p>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {data.matched.map((s) => (
              <Badge key={s} variant="secondary">
                {s}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {data.missing.length === 0 ? (
        <p className="mt-3 text-sm">{t.skillGap.allMatched}</p>
      ) : (
        <div className="mt-3 space-y-3">
          <p className="text-xs text-muted-foreground">{t.skillGap.missing}</p>
          {data.suggestions.map((s) => (
            <div key={s.skill}>
              <Badge variant="outline">{s.skill}</Badge>
              <p className="mt-1 text-sm text-muted-foreground">
                {fmt(t.skillGap.learnAt, { term: s.searchTerm })}{" "}
                {s.portals.map((p, i) => (
                  <span key={p.id}>
                    {i > 0 && " · "}
                    <a
                      href={p.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-0.5 font-medium text-brand underline-offset-2 hover:underline"
                    >
                      {p.name}
                      <ExternalLink className="size-3" aria-hidden />
                    </a>
                  </span>
                ))}
              </p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
