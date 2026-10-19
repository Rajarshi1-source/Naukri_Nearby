# ADR 0009 — Vernacular UX and Offline-First PWA

## Status
Accepted (2026-10-17). Amended 2026-10-19: Bhashini speech (voice-search fallback and Listen button).

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
  or `en-IN`; where that is missing it falls back to Bhashini ASR on the server (below). The mic is
  hidden only where neither path works (no `MediaRecorder`/microphone).

### Job translation (Bhashini)
- `GET /api/jobs/{slug}/translation?lang=` returns the title and description machine-translated by
  Bhashini's ULCA pipeline API (config call, then compute call; keys stay on the server). Results are
  cached in Redis per job version, so an edit invalidates them and a popular job costs one provider call
  per language. Failures return the original with `translated=false` and are not cached.
- The job page offers "Read this job in …" (all eight content languages) and defaults to the UI
  language. A translation is always labelled as machine-translated and the original stays visible.
- UI chrome stays in reviewed dictionaries, not runtime machine translation: labels are short,
  high-traffic and safety-relevant ("Never pay money to get a job"), so they are translated once and
  reviewed. Bhashini can draft new dictionaries offline.

### Speech (Bhashini ASR and TTS)
Two provider-agnostic ports (ADR 0002), selected by `naukri.speech.provider` (`SPEECH_PROVIDER`):
`SpeechToTextProvider` and `TextToSpeechProvider`. `stub` (default) hears nothing and has no audio;
`bhashini` calls the same ULCA pipeline as translation through a shared `BhashiniPipelineClient`
(config call → `serviceId` + `callbackUrl` + inference key, cached per task and language; then the
compute call; a 401/403 drops the cached config). Payloads were checked against the official
pipeline config and compute request/response pages:
- ASR: `config:{language:{sourceLanguage}, serviceId, audioFormat:"wav", samplingRate:16000}`,
  `inputData.audio[{audioContent:<base64>}]`; transcript at `pipelineResponse[0].output[0].source`.
- TTS: `config:{language:{sourceLanguage}, serviceId, gender:"female"}`, `inputData.input[{source}]`;
  base64 WAV at `pipelineResponse[0].audio[0].audioContent`.
Both carry `@Retry` + `@CircuitBreaker(name="bhashini")`; a failure after that is a `503` so the UI
falls back.

- **Voice-search fallback.** `POST /api/speech/transcribe` (multipart `audio` + `lang`, open to
  signed-out users like search). The browser records with `MediaRecorder`, then decodes and
  resamples to 16 kHz mono 16-bit WAV with Web Audio (`src/lib/wav.ts`, no dependency), because one
  fixed input format keeps the server simple and matches what Bhashini documents. The server checks
  the WAV header (bounded chunk walk), caps size (1 MB) and duration (30 s; the recorder stops itself
  at 29 s) and rate-limits per user, or per IP when signed out (10/min). The transcript goes into the
  normal search. Audio and transcripts are never logged or stored.
- **Listen button.** `GET /api/jobs/{slug}/audio?lang=` speaks the title and the start of the
  description (cut at a sentence near 700 characters), translated first through
  `JobTranslationService` when `lang` differs from the source. Only `ACTIVE` + `APPROVED` jobs; others
  are `404`. Audio is cached in Redis (base64) under job id + version + spoken language, so an edit
  invalidates it; cache misses are rate-limited per caller. `Content-Language` says what is actually
  spoken (the original language when the translation failed); `204` means no audio (stub). The
  client prefers the browser's `speechSynthesis` when it has a voice for the language (free, works
  offline) and only then calls the endpoint. The service worker does not cache the audio.
- Redis rather than object storage for the audio: clips are small (the text is capped), derived and
  re-creatable, and expire on their own; the object store is for user files (resumes).

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
- Voice search and Listen now work in Firefox and WebViews, at the cost of Bhashini calls; the browser
  path stays first so most Chrome/Android users cost nothing. With the stub provider the fallback mic
  records but returns no text, and Listen reports that audio is unavailable.
