import type { Dictionary } from "@/i18n";

/** Translates a backend risk-signal code (ADR 0007) for the employer/admin; unknown codes pass through. */
export function riskReason(t: Dictionary, code: string): string {
  return code in t.riskReasons ? t.riskReasons[code as keyof Dictionary["riskReasons"]] : code;
}
