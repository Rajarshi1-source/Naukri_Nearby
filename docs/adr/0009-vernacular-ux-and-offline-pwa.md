# ADR 0009 — Vernacular UX and Offline-First PWA

## Status
Accepted (2026-10-17).

## Context
Target users browse on 2G/3G with intermittent connectivity and many prefer Hindi (and other
regional languages) over English. WhatsApp alerts are already translated; the web UI was
English-only and failed hard when offline.

## Decision

### Vernacular UX
- **No i18n framework dependency.** Typed dictionaries in `frontend/src/i18n/` (`en.ts` is the
  source of truth; `hi.ts` must satisfy the same type, so a missing key is a compile error).
- Language is stored in an `nn_lang` cookie. Server Components read it with `await cookies()`;
  Client Components use an `I18nProvider` context. `<html lang>` follows the choice.
- When signed in, a switch also calls `PUT /api/auth/me/language`, so WhatsApp alerts and the IVR
  greet the user in the same language.
- Search accepts Hinglish and Devanagari: the backend `QueryNormalizer` maps them to canonical
  terms before querying ES or PostGIS. Voice search uses the browser Web Speech API with `hi-IN`
  or `en-IN` and is hidden where unsupported.

### Offline-first PWA
- `app/manifest.ts` (installable, standalone) and a **hand-written service worker** at
  `public/sw.js` (no `next-pwa`, which lags Next.js majors).
- Strategies:
  - static assets (`/_next/static/**`, icons): cache-first, versioned cache name per build;
  - public job API (`GET /api/jobs/search`, `GET /api/jobs/{slug}`) and job pages: network-first
    with a 4 s timeout, cache fallback;
  - navigation fallback: `/offline`;
  - **never** cache requests that carry `Authorization`.
- **Offline apply queue:** if the network is down, the apply is stored in IndexedDB and replayed
  on the `online` event (and on app start). Apply is idempotent server-side, so a `409` on replay
  counts as success. The user sees "queued, will send when online".
- An offline banner tells the user they are seeing saved results.

## Consequences
- Adding a language is adding one dictionary file and an entry in the switcher.
- The SW is ~150 lines we own; its behavior is covered by a Playwright test that goes offline.
- Cached search results can be minutes old when offline; the banner makes that explicit.
