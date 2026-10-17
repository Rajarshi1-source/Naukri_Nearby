"use client";

import { createContext, useContext, type ReactNode } from "react";

import { getDictionary, type Dictionary, type Locale } from ".";

interface I18nState {
  locale: Locale;
  t: Dictionary;
}

const I18nContext = createContext<I18nState | undefined>(undefined);

/**
 * Client-side access to the current dictionary. The root layout reads the nn_lang cookie on the
 * server and passes the locale in; a language switch writes the cookie and calls router.refresh().
 */
export function I18nProvider({ locale, children }: { locale: Locale; children: ReactNode }) {
  return (
    <I18nContext.Provider value={{ locale, t: getDictionary(locale) }}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n(): I18nState {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useI18n must be used within an I18nProvider");
  return ctx;
}

export function useT(): Dictionary {
  return useI18n().t;
}
