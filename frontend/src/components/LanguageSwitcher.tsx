"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { Languages } from "lucide-react";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { LOCALE_COOKIE, LOCALE_LABELS, LOCALES, isLocale } from "@/i18n";
import { useI18n } from "@/i18n/I18nProvider";
import { useAuth } from "@/lib/auth";
import { trustService } from "@/services/trustService";

/**
 * Switches the UI language (ADR 0009): writes the nn_lang cookie, re-renders Server Components, and —
 * when signed in — stores the choice on the account so WhatsApp alerts and the IVR use it too.
 */
export default function LanguageSwitcher() {
  const { locale, t } = useI18n();
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  function change(next: string) {
    if (!isLocale(next) || next === locale) return;
    document.cookie = `${LOCALE_COOKIE}=${next}; path=/; max-age=31536000; SameSite=Lax`;
    if (isAuthenticated) {
      void trustService.setLanguage(next).catch(() => undefined);
    }
    startTransition(() => router.refresh());
  }

  return (
    <Select value={locale} onValueChange={change} disabled={pending}>
      <SelectTrigger
        aria-label={t.nav.language}
        className="h-9 w-auto gap-1.5 border-none px-2 shadow-none"
      >
        <Languages className="size-4" aria-hidden />
        <SelectValue />
      </SelectTrigger>
      <SelectContent align="end">
        {LOCALES.map((l) => (
          <SelectItem key={l} value={l} lang={l}>
            {LOCALE_LABELS[l]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
