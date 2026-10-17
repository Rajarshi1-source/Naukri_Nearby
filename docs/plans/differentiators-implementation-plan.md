# Differentiator Features — Implementation Plan

Status: **approved for implementation** (2026-10-17). Sources: `NaukriNearby_Plan_v2_Supplement.md` §G,
the master plan §19, `naukrinearby/docs/future-work/README.md`, root `README.md`, `AGENTS.md`, and the
skills in `.claude/skills/`.

## 1. Where the project stands

Already built (verified in the tree, do not rebuild):

| Item | Where |
| --- | --- |
| G1 WhatsApp-first apply (chat → profile → idempotent apply) | `WhatsAppConversationService`, `/api/webhooks/twilio/inbound` |
| G3 resume-extraction eval gate + match/translation evals | `naukrinearby/eval/`, `InternalEvalController`, CI `resume-eval-gate` |
| G4 hybrid search (BM25 + pgvector, RRF) | `HybridSearchStrategy`, `ReciprocalRankFusion`, `SEARCH_RANKING=hybrid` |
| G8 (half) phone-OTP-only auth, erasure, SSE-S3, signed URLs, PII redaction | `AuthService`, `AccountService`, `FileStorageService`, `PiiRedactor` |
| Live adapters for Twilio WhatsApp/Verify, Bhashini, remote bge-m3 | `TwilioWhatsAppSender`, `TwilioVerifyOtpProvider`, `BhashiniTranslationProvider`, `RemoteEmbeddingProvider` (REST via `RestClient`, Resilience4j `@Retry` + `@CircuitBreaker`) |
| Frontend, Grafana, k8s manifests, alias-flip reindex | `frontend/`, `monitoring/`, `k8s/`, ADR 0003 |

So the "future work" integration list is done; it is a backlog note only. What is left are the Bharat
differentiators themselves, two platform items, and the frontend surfaces that make them usable.

## 2. Scope of this milestone

| # | Feature | Source | Build or write-up |
| --- | --- | --- | --- |
| F1 | **Voice-first IVR apply** (Twilio Voice, DTMF + speech, Hindi/English) | future-work G1 | Build |
| F2 | **Vernacular UX** (English + हिंदी UI, persisted language, alerts already translated) | future-work G2 | Build |
| F3 | **Offline-first PWA** (manifest, service worker, cached feed, offline apply queue) | supplement G2 | Build |
| F4 | **Transliteration + voice search** (Hinglish/Devanagari lexicon, Web Speech API) | supplement G5 | Build |
| F5 | **Fake-job / fraud detection + moderation** (explainable risk score, reports, admin queue) | supplement G6 | Build |
| F6 | **Trust & verification signals** (GSTIN checksum, phone-verified, tenure badges) | future-work G4+ | Build |
| F7 | **Skill-gap recommendations** (missing skills + free learning portals) | supplement G7 | Build |
| F8 | **DPDP consent ledger + data export** (completes G8) | supplement G8 | Build |
| P1 | **Bulkhead the resume-parse path** with Framework 7 `@ConcurrencyLimit` | future-work | Build |
| P2 | **Langfuse tracing** behind an `LlmTracer` port (metadata only, no PII) | future-work | Build |
| UI | Candidate dashboard, employer dashboard (post job + applicants), admin moderation page | needed by F5–F8 | Build |
| S | Security: CodeQL alerts, Dependabot alerts, workflow permissions | GitHub | Fix last |
| T | Playwright E2E smoke suite + CI job | testing | Build |

Out of scope (stay in `future-work`): Bhashini ASR/TTS for IVR (Twilio's built-in `hi-IN` speech is
used), Aadhaar/GST API lookups against government services, ML-trained fraud classifier, push
notifications.

## 3. Decisions (new and revised)

Existing design notes in the root README stay valid unless revised here.

| # | Decision | Status |
| --- | --- | --- |
| D1 | **Fraud scoring is rule-based and explainable**, not an ML model. A `JobRiskScorer` returns `score 0–100` + `reasons[]`. Rules are data (keyword lists per language in `resources/trust/`). A trained classifier can replace it behind the same port later. | New (ADR 0007) |
| D2 | **Moderation gates search, not storage.** A job is always committed to Postgres. `moderation_status` (`APPROVED`, `PENDING_REVIEW`, `REJECTED`) travels in the outbox payload; ES documents with a non-approved status are deleted/filtered by the sync worker, and the PostGIS fallback filters the same way. No dual-write is introduced. | New (ADR 0007) |
| D3 | **ADMIN is bootstrapped by config.** `NAUKRI_ADMIN_PHONES` (comma list) — a phone in that list gets `ROLE_ADMIN` at OTP verify. No self-service admin sign-up. Revises the earlier note "no flow issues ADMIN tokens". | Revised |
| D4 | **GSTIN verification is offline by default.** `GstVerifier` port: `checksum` (format + mod-36 check digit, default) and `none`. A government/GSP API adapter can be added later. Trust level = `UNVERIFIED` → `PHONE_VERIFIED` → `BUSINESS_VERIFIED`. | New (ADR 0007) |
| D5 | **Reports auto-escalate.** `job_reports UNIQUE(job_id, reporter_id)`; at `naukri.trust.report-threshold` (default 3) distinct reports the job moves to `PENDING_REVIEW` through the outbox. | New |
| D6 | **IVR reuses the WhatsApp pipeline.** Twilio Voice webhooks (`/api/webhooks/twilio/voice/**`) return TwiML, are signature-verified like the WhatsApp ones, keep call state in Redis keyed by `CallSid` (TTL 15 min), and apply through `ApplicationService` (idempotent). Speech uses Twilio `<Gather input="dtmf speech" language="hi-IN">`. | New (ADR 0008) |
| D7 | **UI i18n without a framework dependency.** Typed dictionaries (`src/i18n/{en,hi}.ts`, `hi` must satisfy the `en` type), language in an `nn_lang` cookie read with `await cookies()` on the server, and synced to `users.preferred_language` (`PUT /api/auth/me/language`) when signed in so WhatsApp/IVR use the same language. | New (ADR 0009) |
| D8 | **PWA with a hand-written service worker** (`public/sw.js`), no `next-pwa`. Network-first for `/api/jobs/search` and job pages with cache fallback; cache-first for static assets; applies made offline go to an IndexedDB queue replayed on `online` (apply is idempotent, `409` counts as success). Auth-bearing API responses are never cached. | New (ADR 0009) |
| D9 | **Query normalization before search.** `QueryNormalizer` maps Hinglish/Devanagari terms to canonical skill/category terms from `resources/lexicon/hinglish.tsv`, used by `SearchService` for both ES and the PostGIS fallback, and by the skill-gap matcher. The ES Hindi analyzer stays. | New |
| D10 | **Skill-gap links point to official portals only** (Skill India Digital Hub, SWAYAM, NPTEL, NCS) with a suggested search term. No invented course URLs. | New |
| D11 | **Consent is a ledger.** `consents(user_id, purpose, granted, notice_version, created_at)` append-only; the latest row per purpose wins. Purposes: `RESUME_PROCESSING`, `WHATSAPP_ALERTS`. `GET /api/candidate/account/export` returns all personal data as JSON (DPDP right to access). Erasure deletes the ledger with the user. | New (completes G8) |
| D12 | **Bulkhead = `@ConcurrencyLimit`** (Framework 7, `@EnableResilientMethods`) on the resume-parse entry point, limit `naukri.resume.parse-concurrency` (default 4). Resilience4j stays for retry/circuit breaking on HTTP adapters. | New |
| D13 | **LLM tracing never ships PII.** `LlmTracer` port: `log` (existing structured line) / `langfuse` (ingestion API, async, failures swallowed) / `none`. Payload = provider, model, prompt version, latency, JSON validity, char counts. Resume text is never sent. | New |
| D14 | **CSRF stays disabled on the API**, with an explicit code comment and a `csrf.ignoringRequestMatchers` scoped config: auth is a Bearer header, no ambient cookie authenticates a request (`nn_session` is a UI hint only). The CodeQL alert is resolved by documenting/dismissing, not by adding cookie CSRF tokens that would break Twilio webhooks. | Confirmed |
| D15 | **E2E tests run against the real stack** (Compose infra + boot jar + `next start`), Playwright Chromium only, in a dedicated CI job after `build-and-test`. | New |

Schema changes follow ADR 0004 (expand-only in this milestone): `V5__trust_and_moderation.sql`,
`V6__consents.sql`. All new columns are nullable or defaulted, so old pods keep working.

## 4. Work breakdown

### Phase 0 — skills and docs first
1. Update `.claude/skills/SKILL.md` (spring-boot) with a **Differentiators** section: risk scorer, moderation via outbox, trust levels, IVR TwiML pattern, `@ConcurrencyLimit` bulkhead, `LlmTracer`, consent ledger, query normalizer.
2. Update `security-and-api.md`: new endpoints, ADMIN bootstrap, IVR webhook signature rule, consent/export, CSRF rationale.
3. Update `naukrinearby-nextjs.SKILL.md` (Next.js 16, not 14): i18n dictionaries, PWA service worker rules, voice search hook, offline queue, Playwright conventions.
4. Update `naukrinearby-tailwind-shadcn.SKILL.md`: trust badges, risk banner, language switcher, offline banner.
5. Repackage the `.skill` archives from the edited markdown.
6. Write `SCALING.md` and ADRs 0007–0009.

### Phase 1 — backend
| Step | Files |
| --- | --- |
| Migrations V5, V6 | `db/migration/` |
| Entities/repos | `Job` (+`moderationStatus`, `riskScore`, `riskReasons`), `Employer` (+`gstVerified`, `verifiedAt`), `JobReport`, `Consent` |
| `TrustProperties`, `JobRiskScorer`, `GstVerifier` (+ checksum impl), `TrustService` | `trust/` package |
| Moderation in `JobService` (score on create/update → status), outbox payload carries status, `OutboxSyncWorker` deletes non-approved docs, fallback query filters | `service/`, `worker/` |
| `ReportController` `POST /api/jobs/{id}/report`; `AdminModerationController` `GET /api/admin/moderation/jobs`, `POST …/{id}/decision` | `controller/` |
| `EmployerController` `GET/PUT /api/employer/profile` (company, GSTIN → verification) | `controller/` |
| Trust fields on `JobResponse` / search items (`trustLevel`, `employerSince`) | DTOs |
| `QueryNormalizer` + lexicon; wire into `SearchService` | `service/search/` |
| `SkillGapService` + `GET /api/candidate/jobs/{id}/skill-gap` | `service/`, `resources/trust/learning-portals.tsv` |
| `ConsentService`, `GET/PUT /api/candidate/consents`, `GET /api/candidate/account/export` | `service/`, `controller/` |
| `PUT /api/auth/me/language` | `AuthController` |
| `IvrService` + `IvrController` (`/voice`, `/voice/gather/{step}`) | `service/`, `controller/` |
| `@EnableResilientMethods` + `@ConcurrencyLimit` on the parse path | `config/`, `ResumeParserService` |
| `LlmTracer` port, `LogLlmTracer`, `LangfuseLlmTracer`, `NoopLlmTracer` | `generation/` |
| Admin phone bootstrap in `AuthService` | `service/` |
| `.env.example`, `application.yml` | config |

Tests (Testcontainers + `RestTestClient`, plus plain unit tests):
`JobRiskScorerTest`, `GstinChecksumTest`, `QueryNormalizerTest`, `SkillGapServiceTest`,
`TrustAndModerationIntegrationTests` (scam post → pending, not searchable; admin approve → searchable;
3 reports → pending), `IvrIntegrationTests` (signature check, menu → apply → idempotent),
`ConsentAndExportIntegrationTests`, `LanguagePreferenceIntegrationTests`.

### Phase 2 — frontend
| Area | Work |
| --- | --- |
| i18n | `src/i18n/` dictionaries + `I18nProvider`, `LanguageSwitcher` in header, server `getDictionary()` |
| PWA | `app/manifest.ts`, icons, `public/sw.js`, `ServiceWorkerRegistrar`, `OfflineBanner`, `lib/offlineQueue.ts` (IndexedDB), `app/offline/page.tsx` |
| Voice search | `useSpeechSearch` hook (Web Speech API, `hi-IN` / `en-IN`), mic button in `SearchClient` |
| Trust | `TrustBadge`, risk-free display rules, `ReportJobButton` |
| Skill gap | `SkillGapPanel` on job detail for candidates |
| Candidate | `/candidate` dashboard: applications, saved, recommendations, alerts + consent, export, delete account |
| Employer | `/employer` dashboard: profile + GSTIN, post job (shows moderation result), my jobs, applicants + status |
| Admin | `/admin/moderation` queue with approve/reject |
| Route gate | `proxy.ts` adds `/admin` |

### Phase 3 — verification
1. `./gradlew build` green (WSL Docker).
2. `npm run lint && npm run build` green.
3. Run the full stack, walk every flow in the browser, fix bugs.
4. Playwright: `e2e/` smoke specs (search + voice button presence, language switch, OTP login, apply, offline apply queue, employer post → moderation, admin approve). CI job `e2e`.
5. Screenshots to `docs/screenshots/` and README.

### Phase 4 — security fixes (after features)
| Alert | Fix |
| --- | --- |
| CodeQL #1–3 workflow permissions | top-level `permissions: contents: read` in `ci.yml` |
| CodeQL #4 XSS in TwiML | escape with `HtmlUtils.htmlEscape(s, "UTF-8")`; build TwiML in one `TwiML` helper shared by WhatsApp + IVR |
| CodeQL #5–8 polynomial regex | bounded quantifiers + input length caps in `StubLlmExtractionProvider` and the WhatsApp apply pattern |
| CodeQL #9 CSRF disabled | D14: keep, document; ask the owner to dismiss with the written justification |
| Dependabot npm (next, sharp, postcss, nanoid, js-yaml, brace-expansion, browserslist, baseline-browser-mapping, source-map-js) | `next@16.4.x`, `eslint-config-next` to match, `overrides` for transitive pins, `npm audit` clean; supersedes Dependabot PR #1 |
| Dependabot pip `requests` | bump in `eval/requirements.txt` |

## 4b. Addendum (2026-10-17): Bhashini pipeline + job translation
- The original `BhashiniTranslationProvider` posted to a generic `/translate` path that Bhashini does
  not expose. It now implements the documented ULCA pipeline (config call → compute call), with the
  config cached per language pair. Env: `BHASHINI_USER_ID`, `BHASHINI_ULCA_API_KEY`,
  `BHASHINI_PIPELINE_ID` (replacing `BHASHINI_BASE_URL`/`BHASHINI_API_KEY`).
- New: cached job-description translation (`GET /api/jobs/{slug}/translation`) and a "Read this job
  in" picker on the job page.
- Follow-up (separate PR): Bhashini ASR for voice search where the browser has no speech recognition
  (and for the IVR), and Bhashini TTS for a "Listen" button on job pages.

## 5. Definition of done
- All features reachable in the UI and covered by backend tests.
- `./gradlew build`, `npm run lint`, `npm run build`, and Playwright pass locally; CI has the E2E job.
- README, `naukrinearby/README.md`, `frontend/README.md`, `.env.example`, runbook, future-work note, ADRs, skills, and `SCALING.md` reflect the new design.
- No new CodeQL findings; `npm audit` reports no high/critical.
