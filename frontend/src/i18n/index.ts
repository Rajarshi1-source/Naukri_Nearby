import { en, type Dictionary } from "./en";
import { hi } from "./hi";

export type { Dictionary };

/** Supported UI locales. Adding a language = one dictionary file + one entry here. */
export const LOCALES = ["en", "hi"] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = "en";
export const LOCALE_COOKIE = "nn_lang";

/** Each option labelled in its own script, so a user can find their language without reading English. */
export const LOCALE_LABELS: Record<Locale, string> = {
  en: "English",
  hi: "हिंदी",
};

/** BCP-47 tags for the Web Speech API and number/date formatting. */
export const SPEECH_LANG: Record<Locale, string> = {
  en: "en-IN",
  hi: "hi-IN",
};

const DICTIONARIES: Record<Locale, Dictionary> = { en, hi };

export function isLocale(value: string | undefined | null): value is Locale {
  return value != null && (LOCALES as readonly string[]).includes(value);
}

export function getDictionary(locale: Locale): Dictionary {
  return DICTIONARIES[locale];
}

/** Fills {name} placeholders: fmt("Radius: {n} km", { n: 5 }). */
export function fmt(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match,
  );
}
