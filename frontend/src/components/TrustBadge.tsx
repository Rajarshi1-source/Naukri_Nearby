"use client";

import { Phone, ShieldAlert, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { useT } from "@/i18n/I18nProvider";
import { cn } from "@/lib/utils";

/**
 * Employer trust signal (ADR 0007). Always icon + text, never color alone. Candidates never see a
 * numeric risk score — only this level.
 */
export default function TrustBadge({
  level,
  className,
}: {
  level: string | null | undefined;
  className?: string;
}) {
  const t = useT();
  if (level === "BUSINESS_VERIFIED") {
    return (
      <Badge
        title={t.trust.businessHint}
        className={cn("gap-1 border-transparent bg-success/10 text-success", className)}
      >
        <ShieldCheck className="size-3.5" aria-hidden />
        {t.trust.BUSINESS_VERIFIED}
      </Badge>
    );
  }
  if (level === "PHONE_VERIFIED") {
    return (
      <Badge variant="secondary" title={t.trust.phoneHint} className={cn("gap-1", className)}>
        <Phone className="size-3.5" aria-hidden />
        {t.trust.PHONE_VERIFIED}
      </Badge>
    );
  }
  if (level === "UNVERIFIED") {
    return (
      <Badge variant="outline" title={t.trust.unverifiedHint} className={cn("gap-1 text-muted-foreground", className)}>
        <ShieldAlert className="size-3.5" aria-hidden />
        {t.trust.UNVERIFIED}
      </Badge>
    );
  }
  return null;
}
