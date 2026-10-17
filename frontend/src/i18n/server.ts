import { cookies } from "next/headers";

import { DEFAULT_LOCALE, getDictionary, isLocale, LOCALE_COOKIE, type Dictionary, type Locale } from ".";

/** The visitor's UI language from the nn_lang cookie (Next.js 16: cookies() is async). */
export async function getLocale(): Promise<Locale> {
  const value = (await cookies()).get(LOCALE_COOKIE)?.value;
  return isLocale(value) ? value : DEFAULT_LOCALE;
}

export async function getServerDictionary(): Promise<{ locale: Locale; t: Dictionary }> {
  const locale = await getLocale();
  return { locale, t: getDictionary(locale) };
}
